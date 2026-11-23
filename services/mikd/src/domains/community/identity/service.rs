use super::{
    Admission, Binding, BindingChange, BindingIndex, CommunityStore, Error, ExternalKey,
    IssuedCode, LinkCode, Membership, Redemption, UnlinkAuthority, eligibility,
};
use mik_protocol::wire::{
    self, ChatExternalIdentity, ChatPlayer, IdentityBindingView, IdentityChange, IdentityCode,
    IdentityCommand, IdentityResult, identity_command::Operation, identity_list::Player,
    identity_result::Outcome, identity_unlink::Authority,
};
use std::{sync::Arc, time::Instant};
use uuid::Uuid;

pub(crate) trait MembershipEvidence: Send + Sync {
    fn lookup(&self, external: &ExternalKey) -> Membership;
    fn binding_platforms(&self) -> Vec<&'static str>;
    fn binding_command_prefix(&self, platform: &str) -> &'static str;
    fn ready_platforms(&self) -> Vec<String>;
}

pub(crate) struct IdentityService {
    store: Arc<CommunityStore>,
    membership: Arc<dyn MembershipEvidence>,
}

impl IdentityService {
    pub(crate) fn new(store: Arc<CommunityStore>, membership: Arc<dyn MembershipEvidence>) -> Self {
        Self { store, membership }
    }

    pub(crate) fn bindings(&self) -> &BindingIndex {
        self.store.bindings()
    }

    pub(crate) async fn redeem_external(
        &self,
        key: ExternalKey,
        name: &str,
        code: &str,
    ) -> Result<Redemption, Error> {
        let membership = self.membership.clone();
        self.store
            .redeem_link_code(
                Uuid::new_v4(),
                key,
                name,
                "",
                LinkCode::parse(code),
                move |key| Some(membership.lookup(key)),
            )
            .await
    }

    pub(crate) async fn unlink_external(
        &self,
        key: ExternalKey,
        binding: Uuid,
    ) -> Result<bool, Error> {
        self.store
            .unlink_binding(
                Uuid::new_v4(),
                binding,
                UnlinkAuthority::External(key),
                || true,
            )
            .await
            .map(|changed| changed.is_some())
    }

    pub(crate) async fn reconcile_departures(&self) {
        for (binding, key) in self.store.bindings().departures(|key| {
            key.parts()[0] == "matrix" && self.membership.lookup(key) == Membership::Absent
        }) {
            let membership = self.membership.clone();
            let expected = key.clone();
            self.revoke_departed(binding, key, move || {
                membership.lookup(&expected) == Membership::Absent
            })
            .await;
        }
    }

    pub(crate) async fn record_qq_departure(&self, key: ExternalKey) {
        if key.parts()[0] == "qq"
            && let Some(binding) = self.store.bindings().find(&key)
        {
            self.revoke_departed(binding.id, key, || true).await;
        }
    }

    async fn revoke_departed(
        &self,
        binding: Uuid,
        key: ExternalKey,
        authorized: impl FnOnce() -> bool + Send + 'static,
    ) {
        if let Err(error) = self
            .store
            .unlink_binding(
                Uuid::new_v4(),
                binding,
                UnlinkAuthority::ConfirmedDeparture(key),
                authorized,
            )
            .await
            && !matches!(error, Error::Denied)
        {
            eprintln!("mikd binding reconciliation: {error}");
        }
    }

    #[expect(
        clippy::too_many_lines,
        reason = "Keep identity protocol operations and their result mapping in one dispatcher"
    )]
    pub(crate) async fn execute(
        &self,
        command: IdentityCommand,
        deadline: Instant,
    ) -> Result<IdentityResult, Error> {
        let store = &self.store;
        let directory = &self.membership;
        let live = move || Instant::now() < deadline;
        let mut result = IdentityResult::default();
        let outcome = match command.operation {
            Some(Operation::Admit(wire::ChatPlayer { id, name })) => {
                let membership = directory.clone();
                let sources = directory.clone();
                let (admission, through, codes) = store
                    .admit_game(
                        uuid(&id)?,
                        name,
                        move |key| membership.lookup(key),
                        move || sources.ready_platforms(),
                        live,
                    )
                    .await?;
                result.codes = codes
                    .into_iter()
                    .map(|(platform, code)| {
                        let prefix = directory.binding_command_prefix(&platform);
                        issued(platform, &code, prefix)
                    })
                    .collect();
                if admission == Admission::Eligible {
                    result.admitted_through = through;
                    Outcome::Admitted
                } else if admission == Admission::AwaitingEvidence || result.codes.is_empty() {
                    Outcome::MembershipUnavailable
                } else {
                    Outcome::Challenge
                }
            }
            Some(Operation::Issue(request)) => {
                let player = request.player.ok_or(Error::Invalid("player required"))?;
                if directory
                    .binding_platforms()
                    .contains(&request.platform.as_str())
                {
                    let code = store
                        .issue_link_code(uuid(&player.id)?, player.name, &request.platform, live)
                        .await?;
                    let prefix = directory.binding_command_prefix(&request.platform);
                    result.codes.push(issued(request.platform, &code, prefix));
                    Outcome::Done
                } else {
                    Outcome::UnsupportedPlatform
                }
            }
            Some(Operation::Cancel(request)) => {
                let player = request.player.ok_or(Error::Invalid("player required"))?;
                if directory
                    .binding_platforms()
                    .contains(&request.platform.as_str())
                {
                    if store
                        .cancel_link_code(uuid(&player.id)?, &request.platform, live)
                        .await?
                    {
                        Outcome::Done
                    } else {
                        Outcome::NotFound
                    }
                } else {
                    Outcome::UnsupportedPlatform
                }
            }
            Some(Operation::List(query)) => {
                let after = (!query.after_binding_id.is_empty())
                    .then(|| uuid(&query.after_binding_id))
                    .transpose()?;
                let bindings = match query.player {
                    Some(Player::PlayerId(id)) => store.player_bindings(uuid(&id)?, after).await?,
                    Some(Player::PlayerName(name)) => {
                        store.named_player_bindings(name, after).await?
                    }
                    None => return Err(Error::Invalid("player required")),
                };
                result.bindings = bindings.into_iter().map(view).collect();
                if result.bindings.len() == 64 {
                    result.next_binding_id = result
                        .bindings
                        .last()
                        .map_or_else(String::new, |binding| binding.binding_id.clone());
                }
                Outcome::Done
            }
            Some(Operation::Unlink(request)) => {
                let authority = match request.authority {
                    Some(Authority::OwnerId(owner)) => UnlinkAuthority::Owner(uuid(&owner)?),
                    Some(Authority::Operator(true)) => UnlinkAuthority::Operator,
                    _ => return Err(Error::Denied),
                };
                if let Some((_, binding)) = store
                    .unlink_binding(Uuid::new_v4(), uuid(&request.binding_id)?, authority, live)
                    .await?
                {
                    result.bindings.push(view(binding));
                    Outcome::Done
                } else {
                    Outcome::NotFound
                }
            }
            Some(Operation::Poll(true)) => {
                result.changes = store
                    .pending_binding_changes()
                    .await?
                    .into_iter()
                    .map(|receipt| {
                        let revoked = receipt.change == BindingChange::Unlinked
                            && matches!(
                                eligibility(&store.bindings().player(receipt.account), |key| {
                                    directory.lookup(key)
                                }),
                                Admission::Unbound | Admission::AllAbsent,
                            );
                        let kind = match receipt.change {
                            BindingChange::Linked => wire::identity_change::Kind::Linked,
                            BindingChange::Refreshed => wire::identity_change::Kind::Refreshed,
                            BindingChange::Unlinked => wire::identity_change::Kind::Unlinked,
                        };
                        IdentityChange {
                            operation_id: receipt.operation_id.to_string(),
                            player_id: receipt.account.to_string(),
                            revoked,
                            sequence: receipt.sequence,
                            kind: kind.into(),
                            platform: receipt.platform,
                        }
                    })
                    .collect();
                Outcome::Done
            }
            Some(Operation::Acknowledge(ack)) if ack.operation_ids.len() <= 128 => {
                let operations = ack
                    .operation_ids
                    .iter()
                    .map(|id| uuid(id))
                    .collect::<Result<_, _>>()?;
                store.acknowledge_binding_changes(operations).await?;
                Outcome::Done
            }
            Some(Operation::Platforms(true)) => {
                result.platforms = directory
                    .binding_platforms()
                    .into_iter()
                    .map(str::to_owned)
                    .collect();
                Outcome::Done
            }
            _ => return Err(Error::Invalid("identity operation required")),
        };
        result.outcome = outcome.into();
        Ok(result)
    }
}

fn uuid(value: &str) -> Result<Uuid, Error> {
    Uuid::parse_str(value)
        .ok()
        .filter(|id| !id.is_nil())
        .ok_or(Error::Invalid("invalid identity ID"))
}

fn issued(platform: String, issued: &IssuedCode, prefix: &str) -> IdentityCode {
    IdentityCode {
        platform,
        code: issued.code.expose().to_owned(),
        expires_at_ms: issued.expires_at,
        command_prefix: prefix.into(),
    }
}

fn view(binding: Binding) -> IdentityBindingView {
    let [platform, issuer, scope, subject] = binding.external.parts();
    IdentityBindingView {
        binding_id: binding.id.to_string(),
        player: Some(ChatPlayer {
            id: binding.account.to_string(),
            name: binding.player_name,
        }),
        external: Some(ChatExternalIdentity {
            platform: platform.to_owned(),
            issuer: issuer.to_owned(),
            scope: scope.to_owned(),
            subject: subject.to_owned(),
            display_name: binding.external_name,
            scope_display_name: binding.scope_name,
        }),
        linked_at_ms: binding.created_at,
    }
}
