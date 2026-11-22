use super::identity::{Admission, LinkCode, eligibility};
use super::*;
use std::sync::Arc;
use uuid::Uuid;

const PLAYER: &str = "00000000-0000-4000-8000-000000000001";

fn private_root() -> tempfile::TempDir {
    let root = tempfile::tempdir().unwrap();
    fs::set_permissions(root.path(), fs::Permissions::from_mode(0o700)).unwrap();
    root
}

struct FixedMembership(Membership);

impl MembershipEvidence for FixedMembership {
    fn lookup(&self, _: &ExternalKey) -> Membership {
        self.0
    }

    fn binding_platforms(&self) -> Vec<&'static str> {
        Vec::new()
    }

    fn binding_command_prefix(&self, _: &str) -> &'static str {
        "/"
    }

    fn ready_platforms(&self) -> Vec<String> {
        Vec::new()
    }
}

#[tokio::test]
async fn qq_checks_membership_only_when_binding_and_persists_reported_departures() {
    let root = private_root();
    let path = root.path().join("community.db");
    let store = Arc::new(CommunityStore::open(&path).await.unwrap());
    let player = Uuid::parse_str(PLAYER).unwrap();
    let key = ExternalKey::new("qq", "app".into(), String::new(), "member".into()).unwrap();
    let admit = || {
        store.admit_game(
            player,
            "Player".into(),
            |_| panic!("QQ admission must not consult membership evidence"),
            || vec!["qq".into(), "matrix".into()],
            || true,
        )
    };
    assert!(matches!(
        store
            .issue_link_code(player, "Player".into(), "qq", || false)
            .await,
        Err(Error::Denied)
    ));
    let (unbound, _, codes) = admit().await.unwrap();
    assert_eq!(unbound, Admission::Unbound);
    let code = codes[0].1.code.expose();
    let redeem = |membership| {
        store.redeem_link_code(
            Uuid::new_v4(),
            key.clone(),
            "Member",
            "",
            LinkCode::parse(code),
            move |_| Some(membership),
        )
    };
    assert_eq!(
        redeem(Membership::Unknown).await.unwrap(),
        Redemption::MembershipUnknown
    );
    let Redemption::Applied(linked) = redeem(Membership::Joined).await.unwrap() else {
        panic!("link was not committed");
    };
    assert_eq!(
        store.bindings().named("PLAYER").unwrap().id,
        linked.binding_id
    );
    IdentityService::new(store.clone(), Arc::new(FixedMembership(Membership::Absent)))
        .reconcile_departures()
        .await;
    let (eligible, through, codes) = admit().await.unwrap();
    assert_eq!(eligible, Admission::Eligible);
    assert_eq!(through, linked.sequence);
    assert!(codes.is_empty());
    let identity = IdentityService::new(
        store.clone(),
        Arc::new(FixedMembership(Membership::Unknown)),
    );
    identity.record_qq_departure(key.clone()).await;
    identity.record_qq_departure(key.clone()).await;
    assert!(store.bindings().find(&key).is_none());
    assert!(store.bindings().named("player").is_none());
    assert_eq!(admit().await.unwrap().0, Admission::Unbound);
    drop(identity);
    store.close().await.unwrap();
    drop(store);
    let store = CommunityStore::open(&path).await.unwrap();
    let pending = store.pending_binding_changes().await.unwrap();
    assert_eq!(pending.len(), 2);
    assert_eq!(pending[0], linked);
    assert_eq!(pending[1].binding_id, linked.binding_id);
    assert_eq!(pending[1].change, BindingChange::Unlinked);
    store
        .acknowledge_binding_changes(pending.iter().map(|receipt| receipt.operation_id).collect())
        .await
        .unwrap();
    assert!(store.pending_binding_changes().await.unwrap().is_empty());
    store.close().await.unwrap();
}

#[test]
fn matrix_checks_membership_unless_the_player_also_has_qq() {
    let matrix = Arc::new(Binding {
        id: Uuid::new_v4(),
        account: Uuid::parse_str(PLAYER).unwrap(),
        player_name: "Player".into(),
        external: ExternalKey::new(
            "matrix",
            "example.org".into(),
            String::new(),
            "@member:example.org".into(),
        )
        .unwrap(),
        external_name: "Member".into(),
        scope_name: String::new(),
        created_at: 0,
    });
    assert_eq!(
        eligibility(std::slice::from_ref(&matrix), |_| Membership::Unknown),
        Admission::AwaitingEvidence
    );
    let qq = Arc::new(Binding {
        id: Uuid::new_v4(),
        external: ExternalKey::new("qq", "app".into(), String::new(), "member".into()).unwrap(),
        ..matrix.as_ref().clone()
    });
    let admission = eligibility(&[matrix, qq], |_| {
        panic!("QQ must bypass the evidence check")
    });
    assert_eq!(admission, Admission::Eligible);
}
