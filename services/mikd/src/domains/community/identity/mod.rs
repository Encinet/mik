mod codes;
mod index;
mod service;

use super::{CommunityStore, Error, java_whitespace, outbox, schema, store::now_ms};
pub use codes::IssuedCode;
pub(crate) use index::BindingIndex;
pub(crate) use service::{IdentityService, MembershipEvidence};
use sha2::{Digest, Sha256};
use tokio_rusqlite::rusqlite::{
    self, Connection, OptionalExtension, Row, TransactionBehavior, params,
};
use uuid::Uuid;
use zeroize::Zeroizing;

#[derive(Clone, Eq, Hash, PartialEq)]
pub struct ExternalKey {
    platform: String,
    issuer: String,
    scope: String,
    subject: String,
}

impl ExternalKey {
    /// # Errors
    /// Rejects invalid platform names, control characters or oversized key parts.
    /// Issuer, scope and subject are opaque values supplied by the authenticated adapter.
    pub fn new(
        platform: &str,
        issuer: String,
        scope: String,
        subject: String,
    ) -> Result<Self, Error> {
        let platform = platform_name(platform)?;
        schema::identity_part(&issuer, false)?;
        schema::identity_part(&scope, true)?;
        schema::identity_part(&subject, false)?;
        Ok(Self {
            platform,
            issuer,
            scope,
            subject,
        })
    }

    #[must_use]
    pub fn parts(&self) -> [&str; 4] {
        [&self.platform, &self.issuer, &self.scope, &self.subject]
    }
}

fn platform_name(value: &str) -> Result<String, Error> {
    let value = value.trim_matches(java_whitespace);
    if value.len() > 96 {
        return Err(Error::Invalid("invalid platform name"));
    }
    let value = value.to_lowercase();
    schema::platform(&value)?;
    Ok(value)
}

/// The plaintext is held only for display or redemption and is never debug formatted.
pub struct LinkCode(Zeroizing<String>);

impl LinkCode {
    #[must_use]
    pub fn parse(raw: &str) -> Option<Self> {
        let value = raw.trim_matches(java_whitespace);
        (value.len() == 6 && value.bytes().all(|ch| ch.is_ascii_digit()))
            .then(|| Self(Zeroizing::new(value.to_owned())))
    }

    fn generate() -> Self {
        Self(Zeroizing::new(format!(
            "{:06}",
            rand::random_range(0..1_000_000)
        )))
    }

    #[must_use]
    pub fn expose(&self) -> &str {
        self.0.as_str()
    }

    fn digest(&self) -> [u8; 32] {
        Sha256::digest(self.expose().as_bytes()).into()
    }
}

#[derive(Clone)]
pub struct Binding {
    pub id: Uuid,
    pub account: Uuid,
    pub player_name: String,
    pub external: ExternalKey,
    pub external_name: String,
    pub scope_name: String,
    pub created_at: i64,
}

#[derive(Clone, Copy, Eq, PartialEq)]
pub enum Membership {
    Joined,
    Absent,
    Unknown,
}

#[derive(Debug, Clone, Copy, Eq, PartialEq)]
pub enum Admission {
    Eligible,
    Unbound,
    AwaitingEvidence,
    AllAbsent,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum BindingChange {
    Linked,
    Refreshed,
    Unlinked,
}

impl BindingChange {
    pub(super) const fn label(self) -> &'static str {
        match self {
            Self::Linked => "LINKED",
            Self::Refreshed => "REFRESHED",
            Self::Unlinked => "UNLINKED",
        }
    }
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct BindingReceipt {
    pub sequence: i64,
    pub operation_id: Uuid,
    pub binding_id: Uuid,
    pub account: Uuid,
    pub change: BindingChange,
    pub platform: String,
}

#[derive(Debug, Eq, PartialEq)]
pub enum Redemption {
    Applied(BindingReceipt),
    InvalidOrExpired,
    RateLimited { retry_at: i64 },
    OtherPlatform,
    ExternalIdentityInUse,
    PlayerScopeInUse,
    MembershipAbsent,
    MembershipUnknown,
}

/// Scope of the authenticated command. Authority for all variants is provided
/// by an active adapter, never by self-declared IDs or roles in a command body.
pub enum UnlinkAuthority {
    Owner(Uuid),
    External(ExternalKey),
    ConfirmedDeparture(ExternalKey),
    Operator,
}

const SELECT_BINDING: &str =
    "SELECT b.id,b.account_id,coalesce(a.display_name,a.id),b.platform,b.issuer,b.scope,
            b.subject,b.label,b.scope_label,b.linked_at
     FROM bindings b JOIN accounts a ON a.id=b.account_id";

fn binding_row(row: &Row<'_>) -> rusqlite::Result<Binding> {
    Ok(Binding {
        id: schema::row_uuid(row, 0)?,
        account: schema::row_uuid(row, 1)?,
        player_name: row.get(2)?,
        external: ExternalKey {
            platform: row.get(3)?,
            issuer: row.get(4)?,
            scope: row.get(5)?,
            subject: row.get(6)?,
        },
        external_name: row.get(7)?,
        scope_name: row.get(8)?,
        created_at: row.get(9)?,
    })
}

fn find_external(db: &Connection, key: &ExternalKey) -> Result<Option<Binding>, Error> {
    Ok(db
        .query_row(
            &format!(
                "{SELECT_BINDING}
                  WHERE b.platform=?1 AND b.issuer=?2 AND b.scope=?3 AND b.subject=?4"
            ),
            key.parts(),
            binding_row,
        )
        .optional()?)
}

fn player_name(name: &str) -> Result<(), Error> {
    if name.encode_utf16().count() > 256
        || name.chars().all(java_whitespace)
        || name.chars().any(char::is_control)
    {
        return Err(Error::Invalid("invalid binding player name"));
    }
    Ok(())
}

fn display_name(name: &str) -> String {
    let mut clean = String::new();
    let mut count = 0;
    let mut space = false;
    for ch in name.chars() {
        if java_whitespace(ch) || ch.is_control() {
            space = !clean.is_empty();
            continue;
        }
        count += ch.len_utf16() + usize::from(space);
        if count > 64 {
            break;
        }
        if space {
            clean.push(' ');
        }
        clean.push(ch);
        space = false;
    }
    clean
}

struct ExternalProfile {
    key: ExternalKey,
    name: String,
    scope_name: String,
}

impl ExternalProfile {
    fn redeem(
        &self,
        db: &mut Connection,
        index: &BindingIndex,
        operation_id: Uuid,
        code: Option<&LinkCode>,
        evidence: impl FnOnce(&ExternalKey) -> Option<Membership>,
        now: i64,
    ) -> Result<Redemption, Error> {
        let tx = db.transaction_with_behavior(TransactionBehavior::Immediate)?;
        match evidence(&self.key).ok_or(Error::Denied)? {
            Membership::Absent => return Ok(Redemption::MembershipAbsent),
            Membership::Unknown => return Ok(Redemption::MembershipUnknown),
            Membership::Joined => {}
        }

        outbox::check_operation(&tx, operation_id)?;
        let attempts = codes::Attempts::read(&tx, &self.key, now)?;
        if let Some(retry_at) = attempts.locked_until() {
            return Ok(Redemption::RateLimited { retry_at });
        }
        attempts.admit_new_principal(&tx)?;
        let digest = code.map(LinkCode::digest);
        let pending = digest
            .as_ref()
            .map(|digest| codes::pending(&tx, digest, now))
            .transpose()?
            .flatten();
        let (Some(digest), Some(pending)) = (digest, pending) else {
            let retry_at = attempts.fail(&tx)?;
            tx.commit()?;
            return Ok(retry_at.map_or(Redemption::InvalidOrExpired, |retry_at| {
                Redemption::RateLimited { retry_at }
            }));
        };
        if pending.platform != self.key.platform {
            return Ok(Redemption::OtherPlatform);
        }

        let change = if let Some(existing) = find_external(&tx, &self.key)? {
            if existing.account != pending.account {
                return Ok(Redemption::ExternalIdentityInUse);
            }
            self.refresh(&tx, existing.id)?;
            BindingChange::Refreshed
        } else {
            let [platform, issuer, scope, _] = self.key.parts();
            if tx.prepare(
                "SELECT 1 FROM bindings WHERE account_id=?1 AND platform=?2 AND issuer=?3 AND scope=?4",
            )?.exists(params![pending.account.to_string(), platform, issuer, scope])? {
                return Ok(Redemption::PlayerScopeInUse);
            }
            self.insert(&tx, pending.account, now)?;
            BindingChange::Linked
        };
        codes::consume(&tx, &digest)?;
        attempts.clear(&tx)?;
        let binding = find_external(&tx, &self.key)?.ok_or(Error::Stale)?;
        let receipt = record_change(&tx, operation_id, &binding, change, now)?;
        index.commit(tx, |data| data.insert(binding))?;
        Ok(Redemption::Applied(receipt))
    }

    fn refresh(&self, db: &Connection, id: Uuid) -> Result<(), Error> {
        db.execute(
            "UPDATE bindings SET label=?1,scope_label=?2 WHERE id=?3",
            params![self.name, self.scope_name, id.to_string()],
        )?;
        Ok(())
    }

    fn insert(&self, db: &Connection, account: Uuid, now: i64) -> Result<(), Error> {
        let [platform, issuer, scope, subject] = self.key.parts();
        let id = Uuid::new_v4();
        db.execute("INSERT INTO bindings(id,account_id,platform,issuer,scope,subject,label,scope_label,linked_at)
                    VALUES (?1,?2,?3,?4,?5,?6,?7,?8,?9)",
            params![id.to_string(), account.to_string(), platform, issuer, scope, subject, self.name, self.scope_name, now],
        )?;
        Ok(())
    }
}

fn record_change(
    db: &Connection,
    operation_id: Uuid,
    binding: &Binding,
    change: BindingChange,
    now: i64,
) -> Result<BindingReceipt, Error> {
    let platform = binding.external.parts()[0];
    let sequence = outbox::record(
        db,
        operation_id,
        binding.account,
        change,
        binding.id,
        platform,
        now,
    )?;
    Ok(BindingReceipt {
        sequence,
        operation_id,
        binding_id: binding.id,
        account: binding.account,
        change,
        platform: platform.into(),
    })
}

fn binding_receipt(row: &Row<'_>) -> rusqlite::Result<BindingReceipt> {
    Ok(BindingReceipt {
        sequence: row.get(0)?,
        operation_id: schema::row_uuid(row, 1)?,
        account: schema::row_uuid(row, 2)?,
        binding_id: schema::row_uuid(row, 4)?,
        platform: row.get(5)?,
        change: match row.get_ref(3)?.as_str()? {
            "LINKED" => BindingChange::Linked,
            "REFRESHED" => BindingChange::Refreshed,
            "UNLINKED" => BindingChange::Unlinked,
            _ => return Err(rusqlite::Error::InvalidQuery),
        },
    })
}

impl CommunityStore {
    /// Issue or replace the active code for one platform. The callback must validate
    /// the exact live game connection, including first-login password checks.
    /// # Errors
    /// Rejects expired request authority and storage errors.
    pub async fn issue_link_code(
        &self,
        account: Uuid,
        name: String,
        platform: &str,
        authorized: impl FnOnce() -> bool + Send + 'static,
    ) -> Result<IssuedCode, Error> {
        let platform = platform_name(platform)?;
        player_name(&name)?;
        let index = self.binding_index.clone();
        self.call(move |db| {
            let tx = db.transaction_with_behavior(TransactionBehavior::Immediate)?;
            if !authorized() {
                return Err(Error::Denied);
            }
            schema::set_account_name(&tx, account, &name)?;
            let code = codes::issue(&tx, account, &platform, now_ms()?)?;
            index.commit(tx, |data| data.update_name(account, &name))?;
            Ok(code)
        })
        .await
    }

    /// # Errors
    /// Rejects expired request authority and storage errors.
    pub async fn cancel_link_code(
        &self,
        account: Uuid,
        platform: &str,
        authorized: impl FnOnce() -> bool + Send + 'static,
    ) -> Result<bool, Error> {
        let platform = platform_name(platform)?;
        self.call(move |db| {
            let tx = db.transaction_with_behavior(TransactionBehavior::Immediate)?;
            if !authorized() {
                return Err(Error::Denied);
            }
            let removed = tx.execute(
                "DELETE FROM link_challenges WHERE account_id=?1 AND platform=?2 AND expires_at>?3",
                params![account.to_string(), platform, now_ms()?],
            )? == 1;
            tx.commit()?;
            Ok(removed)
        })
        .await
    }

    /// Consume the code only when an authenticated adapter confirms current membership
    /// for this exact external key. Return no evidence once that adapter loses authority.
    /// # Errors
    /// Reports stale adapter authority, duplicate committed operation IDs and storage failures.
    pub async fn redeem_link_code(
        &self,
        operation_id: Uuid,
        key: ExternalKey,
        display: &str,
        scope_display: &str,
        code: Option<LinkCode>,
        evidence: impl FnOnce(&ExternalKey) -> Option<Membership> + Send + 'static,
    ) -> Result<Redemption, Error> {
        let profile = ExternalProfile {
            name: display_name(display),
            scope_name: if key.scope.is_empty() {
                String::new()
            } else {
                display_name(scope_display)
            },
            key,
        };
        let index = self.binding_index.clone();
        self.call(move |db| {
            profile.redeem(db, &index, operation_id, code.as_ref(), evidence, now_ms()?)
        })
        .await
    }

    /// # Errors
    /// Reports expired authority, stale credentials, already committed IDs or storage errors.
    pub async fn unlink_binding(
        &self,
        operation_id: Uuid,
        expected_binding: Uuid,
        authority: UnlinkAuthority,
        authorized: impl FnOnce() -> bool + Send + 'static,
    ) -> Result<Option<(BindingReceipt, Binding)>, Error> {
        let index = self.binding_index.clone();
        self.call(move |db| {
            let tx = db.transaction_with_behavior(TransactionBehavior::Immediate)?;
            if !authorized() {
                return Err(Error::Denied);
            }
            outbox::check_operation(&tx, operation_id)?;
            let binding = tx
                .query_row(
                    &format!("{SELECT_BINDING} WHERE b.id=?1"),
                    [expected_binding.to_string()],
                    binding_row,
                )
                .optional()?;
            let Some(binding) = binding.filter(|binding| match &authority {
                UnlinkAuthority::Owner(account) => binding.account == *account,
                UnlinkAuthority::External(key) | UnlinkAuthority::ConfirmedDeparture(key) => {
                    &binding.external == key
                }
                UnlinkAuthority::Operator => true,
            }) else {
                return Ok(None);
            };
            tx.execute(
                "DELETE FROM bindings WHERE id=?1",
                [expected_binding.to_string()],
            )?;
            let now = now_ms()?;
            codes::Attempts::read(&tx, &binding.external, now)?.clear(&tx)?;
            let receipt = record_change(&tx, operation_id, &binding, BindingChange::Unlinked, now)?;
            index.commit(tx, |data| data.remove(&binding))?;
            Ok(Some((receipt, binding)))
        })
        .await
    }

    /// Up to 64 bindings ordered by binding ID. Pass the last ID to read the next page.
    /// # Errors
    /// Reports storage or admission failures.
    pub async fn player_bindings(
        &self,
        account: Uuid,
        after: Option<Uuid>,
    ) -> Result<Vec<Binding>, Error> {
        self.binding_page("b.account_id=?1", account.to_string(), after)
            .await
    }

    pub(crate) async fn named_player_bindings(
        &self,
        name: String,
        after: Option<Uuid>,
    ) -> Result<Vec<Binding>, Error> {
        player_name(&name)?;
        self.binding_page("a.display_name=?1 COLLATE NOCASE", name, after)
            .await
    }

    async fn binding_page(
        &self,
        selector: &'static str,
        value: String,
        after: Option<Uuid>,
    ) -> Result<Vec<Binding>, Error> {
        self.call(move |db| {
            Ok(db
                .prepare_cached(&format!(
                    "{SELECT_BINDING}
                      WHERE {selector} AND b.id>?2 ORDER BY b.id LIMIT 64"
                ))?
                .query_map(
                    params![value, after.map_or_else(String::new, |id| id.to_string())],
                    binding_row,
                )?
                .collect::<Result<_, _>>()?)
        })
        .await
    }

    pub(crate) async fn admit_game(
        &self,
        account: Uuid,
        name: String,
        evidence: impl Fn(&ExternalKey) -> Membership + Send + 'static,
        platforms: impl FnOnce() -> Vec<String> + Send + 'static,
        authorized: impl FnOnce() -> bool + Send + 'static,
    ) -> Result<(Admission, i64, Vec<(String, IssuedCode)>), Error> {
        player_name(&name)?;
        let index = self.binding_index.clone();
        self.call(move |db| {
            let tx = db.transaction_with_behavior(TransactionBehavior::Immediate)?;
            if !authorized() {
                return Err(Error::Denied);
            }
            let bindings = index.player(account);
            let result = eligibility(&bindings, &evidence);
            let through = if result == Admission::Eligible {
                tx.query_row(
                    "SELECT coalesce(max(id),0) FROM community_changes",
                    [],
                    |row| row.get(0),
                )?
            } else {
                0
            };
            let enabled = if result == Admission::Eligible {
                Vec::new()
            } else {
                platforms()
                    .into_iter()
                    .filter(|platform| {
                        result != Admission::AwaitingEvidence
                            || !bindings
                                .iter()
                                .any(|binding| binding.external.parts()[0] == platform)
                    })
                    .collect()
            };
            if enabled.is_empty()
                && (result != Admission::Eligible
                    || bindings.iter().all(|binding| binding.player_name == name))
            {
                return Ok((result, through, Vec::new()));
            }
            schema::set_account_name(&tx, account, &name)?;
            let now = now_ms()?;
            let issued = enabled
                .into_iter()
                .map(|platform| {
                    let code = codes::issue(&tx, account, &platform, now)?;
                    Ok((platform, code))
                })
                .collect::<Result<_, Error>>()?;
            index.commit(tx, |data| data.update_name(account, &name))?;
            Ok((result, through, issued))
        })
        .await
    }

    /// Up to 128 unapplied changes in commit order.
    /// # Errors
    /// Reports storage or admission failures.
    pub async fn pending_binding_changes(&self) -> Result<Vec<BindingReceipt>, Error> {
        self.call(|db| outbox::pending(db, binding_receipt)).await
    }

    /// Acknowledge once the game has applied the binding generation and rechecked admission.
    /// # Errors
    /// Reports storage or admission failures.
    pub(crate) async fn acknowledge_binding_changes(
        &self,
        operations: Vec<Uuid>,
    ) -> Result<(), Error> {
        self.call(move |db| {
            let tx = db.transaction_with_behavior(TransactionBehavior::Immediate)?;
            for operation in operations {
                outbox::acknowledge(&tx, operation)?;
            }
            tx.commit()?;
            Ok(())
        })
        .await
    }
}

pub(crate) fn eligibility(
    bindings: &[std::sync::Arc<Binding>],
    evidence: impl Fn(&ExternalKey) -> Membership,
) -> Admission {
    if bindings
        .iter()
        .any(|binding| binding.external.platform == "qq")
    {
        return Admission::Eligible;
    }
    let mut result = Admission::Unbound;
    for binding in bindings {
        match evidence(&binding.external) {
            Membership::Joined => return Admission::Eligible,
            Membership::Unknown => result = Admission::AwaitingEvidence,
            Membership::Absent if result == Admission::Unbound => result = Admission::AllAbsent,
            Membership::Absent => (),
        }
    }
    result
}
