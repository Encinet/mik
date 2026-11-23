use super::community::CommunityStore;
use crate::game::{GameBridge, GameResult, Operation};
use mik_protocol::wire::{
    AccountPolicy, AccountQuery, WebLoginApproval, WebLoginReceipt, web_login_receipt::Outcome,
};
use std::{
    collections::HashMap,
    sync::{Arc, Mutex},
    time::Duration,
};
use time::OffsetDateTime;
use tokio::time::Instant;
use uuid::Uuid;

const VALIDITY: Duration = Duration::from_mins(5);
const MAX_APPROVALS: usize = 1024;
const MAX_PER_ACCOUNT: usize = 4;

pub(crate) struct Access {
    game: Arc<GameBridge>,
    community: Option<Arc<CommunityStore>>,
    approvals: Mutex<HashMap<String, Arc<Approval>>>,
}

pub(crate) enum AccountLookupError {
    Unavailable,
    Failed,
}

pub(crate) struct Account {
    pub(crate) policy: AccountPolicy,
    pub(crate) role: &'static str,
}

impl Account {
    pub(crate) fn staff(&self) -> bool {
        matches!(self.role, "moderator" | "custodian")
    }
}

pub(crate) struct Approval {
    pub(crate) player_uuid: String,
    pub(crate) player_name: String,
    pub(crate) confirmed_at: OffsetDateTime,
    instance: String,
    login_reference: String,
    expires_at: Instant,
}

pub(crate) enum Confirmation {
    Missing,
    Banned,
    Unavailable,
    Confirmed {
        approval: Arc<Approval>,
        role: &'static str,
    },
}

pub(crate) fn valid_code(code: &str) -> bool {
    (6..=10).contains(&code.len()) && code.bytes().all(|byte| byte.is_ascii_digit())
}

impl Access {
    pub(crate) fn new(game: Arc<GameBridge>, community: Option<Arc<CommunityStore>>) -> Self {
        Self {
            game,
            community,
            approvals: Mutex::default(),
        }
    }

    pub(crate) async fn account(
        &self,
        uuid: &str,
        name: String,
    ) -> Result<Account, AccountLookupError> {
        let query = AccountQuery {
            uuid: uuid.into(),
            name,
            expected_login_reference: None,
        };
        let policy = match self.game.query(Operation::Account(query)).await {
            Some(GameResult::Account(policy)) => policy,
            Some(_) => return Err(AccountLookupError::Failed),
            None => return Err(AccountLookupError::Unavailable),
        };
        let role = self
            .role(uuid, policy.local_custodian)
            .await
            .ok_or(AccountLookupError::Unavailable)?;
        Ok(Account { policy, role })
    }

    async fn role(&self, uuid: &str, local_custodian: bool) -> Option<&'static str> {
        if local_custodian {
            return Some("custodian");
        }
        match &self.community {
            Some(store) => store.community_role(uuid.into()).await.ok(),
            None => Some("default"),
        }
    }

    pub(crate) async fn community_profile_access(&self, uuid: &str) -> Option<bool> {
        self.role(uuid, false)
            .await
            .map(|role| matches!(role, "member" | "moderator"))
    }

    pub(crate) fn approve(&self, instance: &str, request: WebLoginApproval) -> WebLoginReceipt {
        WebLoginReceipt {
            outcome: self.store_approval(instance, request).into(),
        }
    }

    fn store_approval(&self, instance: &str, request: WebLoginApproval) -> Outcome {
        if !valid_code(&request.code)
            || Uuid::parse_str(&request.player_uuid).is_err()
            || Uuid::parse_str(&request.login_reference).is_err()
            || request.player_name.is_empty()
            || request.player_name.encode_utf16().count() > 64
            || request.player_name.chars().any(char::is_control)
        {
            return Outcome::Rejected;
        }
        let now = Instant::now();
        let Ok(mut approvals) = self.approvals.lock() else {
            return Outcome::Unavailable;
        };
        approvals.retain(|_, approval| approval.expires_at > now);
        if let Some(existing) = approvals.get(&request.code) {
            return if existing.player_uuid == request.player_uuid
                && existing.instance == instance
                && existing.login_reference == request.login_reference
            {
                Outcome::Approved
            } else {
                Outcome::Rejected
            };
        }
        if approvals.len() >= MAX_APPROVALS
            || approvals
                .values()
                .filter(|approval| approval.player_uuid == request.player_uuid)
                .count()
                >= MAX_PER_ACCOUNT
        {
            return Outcome::Unavailable;
        }
        approvals.insert(
            request.code,
            Arc::new(Approval {
                player_uuid: request.player_uuid,
                player_name: request.player_name,
                confirmed_at: OffsetDateTime::now_utc(),
                instance: instance.into(),
                login_reference: request.login_reference,
                expires_at: now + VALIDITY,
            }),
        );
        Outcome::Approved
    }

    pub(crate) async fn lookup(&self, code: &str) -> Confirmation {
        let approval = {
            let Ok(approvals) = self.approvals.lock() else {
                return Confirmation::Unavailable;
            };
            approvals.get(code).cloned()
        };
        let Some(approval) = approval.filter(|approval| approval.expires_at > Instant::now())
        else {
            return Confirmation::Missing;
        };
        let query = AccountQuery {
            uuid: approval.player_uuid.clone(),
            name: approval.player_name.clone(),
            expected_login_reference: Some(approval.login_reference.clone()),
        };
        let Some(GameResult::Account(policy)) = self
            .game
            .query_for(approval.instance.clone(), Operation::Account(query))
            .await
        else {
            return Confirmation::Unavailable;
        };
        if approval.expires_at <= Instant::now() {
            return Confirmation::Missing;
        }
        if policy.banned {
            return Confirmation::Banned;
        }
        if !policy.login_matches {
            return Confirmation::Missing;
        }
        let Some(role) = self
            .role(&approval.player_uuid, policy.local_custodian)
            .await
        else {
            return Confirmation::Unavailable;
        };
        if approval.expires_at <= Instant::now() {
            return Confirmation::Missing;
        }
        Confirmation::Confirmed { approval, role }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn request(code: &str, player: Uuid, session: Uuid) -> WebLoginApproval {
        WebLoginApproval {
            code: code.into(),
            player_uuid: player.to_string(),
            player_name: "Player".into(),
            login_reference: session.to_string(),
        }
    }

    #[test]
    fn approval_can_be_repeated_only_by_its_original_instance_account_and_session() {
        let access = Access::new(Arc::new(GameBridge::new()), None);
        let player = Uuid::from_u128(1);
        let session = Uuid::from_u128(2);
        let original = request("123456", player, session);
        assert_eq!(
            access.approve("paper-1", original.clone()).outcome(),
            Outcome::Approved
        );
        let first = access.approvals.lock().unwrap()["123456"].clone();
        assert_eq!(
            access.approve("paper-1", original.clone()).outcome(),
            Outcome::Approved
        );
        assert!(Arc::ptr_eq(
            &first,
            &access.approvals.lock().unwrap()["123456"]
        ));
        assert_eq!(
            access.approve("paper-2", original).outcome(),
            Outcome::Rejected
        );
        assert_eq!(
            access
                .approve("paper-1", request("123456", player, Uuid::from_u128(3)))
                .outcome(),
            Outcome::Rejected
        );
        assert_eq!(
            access
                .approve("paper-1", request("123456", Uuid::from_u128(4), session))
                .outcome(),
            Outcome::Rejected
        );
    }

    #[test]
    fn a_well_formed_code_is_not_rejected_when_approval_capacity_is_exhausted() {
        let access = Access::new(Arc::new(GameBridge::new()), None);
        let player = Uuid::from_u128(1);
        let session = Uuid::from_u128(2);
        for i in 0..MAX_PER_ACCOUNT {
            let approval = request(&format!("{i:06}"), player, session);
            assert_eq!(
                access.approve("paper", approval).outcome(),
                Outcome::Approved
            );
        }
        let overflow = format!("{MAX_PER_ACCOUNT:06}");
        assert_eq!(
            access
                .approve("paper", request(&overflow, player, session))
                .outcome(),
            Outcome::Unavailable
        );
        assert!(!access.approvals.lock().unwrap().contains_key(&overflow));
        assert_eq!(
            access
                .approve("paper", request("bad", player, session))
                .outcome(),
            Outcome::Rejected
        );
    }
}
