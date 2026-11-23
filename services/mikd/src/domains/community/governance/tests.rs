use super::*;
use mik_protocol::wire::{
    GovernanceDeliveryPoll, GovernanceDeliveryReceipt, GovernanceGameGrant,
    governance_game_grant::Permission,
};
use std::{fs, os::unix::fs::PermissionsExt};

const PLAYER: &str = "00000000-0000-4000-8000-000000000001";

async fn execute(store: &CommunityStore, operation: Operation) -> GovernanceResult {
    store
        .governance(GovernanceCommand {
            operation: Some(operation),
        })
        .await
        .unwrap()
}

async fn poll(store: &CommunityStore, deferred: &[&str]) -> Vec<GovernanceGameGrant> {
    execute(
        store,
        Operation::PollDeliveries(GovernanceDeliveryPoll {
            deferred_players: deferred.iter().map(ToString::to_string).collect(),
        }),
    )
    .await
    .grants
}

async fn confirm(store: &CommunityStore, id: i64) -> Vec<GovernanceGameGrant> {
    execute(
        store,
        Operation::Confirm(GovernanceDeliveryReceipt {
            grants: vec![id],
            announcements: Vec::new(),
        }),
    )
    .await
    .grants
}

#[tokio::test]
async fn late_grant_receipt_does_not_complete_revocation_or_restore_authority() {
    let root = tempfile::tempdir().unwrap();
    fs::set_permissions(root.path(), fs::Permissions::from_mode(0o700)).unwrap();
    let path = root.path().join("community.db");
    let store = CommunityStore::open(&path).await.unwrap();
    store
        .call(|db| {
            let now = now_ms()?;
            db.execute(
                "INSERT INTO gov_players(player_id,display_name) VALUES (?1,'Moderator')",
                [PLAYER],
            )?;
            db.execute(
                "INSERT INTO gov_tenures(player_id,source,started_ms) VALUES (?1,'vote',?2)",
                params![PLAYER, now],
            )?;
            db.execute("INSERT INTO gov_ban_snapshot VALUES (1,?1)", [now])?;
            delivery::request(db, PLAYER, Permission::Moderator, true, now, None)
        })
        .await
        .unwrap();
    let action = |action: Action| {
        Operation::Act(GovernanceActionRequest {
            actor_id: PLAYER.into(),
            player_id: PLAYER.into(),
            action: action.into(),
            ..GovernanceActionRequest::default()
        })
    };
    let inspect = || {
        Operation::Query(GovernanceQuery {
            view: View::FindPlayers.into(),
            viewer_id: PLAYER.into(),
            ..GovernanceQuery::default()
        })
    };
    let granted = poll(&store, &[]).await.remove(0);
    assert!(granted.enabled);
    assert!(poll(&store, &[PLAYER]).await.is_empty());
    assert_eq!(execute(&store, inspect()).await.outcome(), Outcome::Done);

    assert_eq!(
        execute(&store, action(Action::Resign)).await.outcome(),
        Outcome::Done
    );
    assert_eq!(
        execute(&store, inspect()).await.outcome(),
        Outcome::NotAuthorized
    );
    let pending = poll(&store, &[PLAYER]).await;
    assert_eq!(pending.len(), 1);
    let revoked = &pending[0];
    assert!(!revoked.enabled);
    assert!(revoked.id > granted.id);
    assert_eq!(
        execute(
            &store,
            Operation::Query(GovernanceQuery {
                view: View::GrantTargets.into(),
                player_id: PLAYER.into(),
                ..GovernanceQuery::default()
            })
        )
        .await
        .grants,
        pending
    );
    assert_eq!(confirm(&store, granted.id).await, pending);
    store.close().await.unwrap();
    drop(store);

    let store = CommunityStore::open(&path).await.unwrap();
    assert_eq!(poll(&store, &[]).await, pending);
    assert!(confirm(&store, revoked.id).await.is_empty());
    assert!(confirm(&store, revoked.id).await.is_empty());
    assert_eq!(confirm(&store, granted.id).await, pending);
    assert!(poll(&store, &[]).await.is_empty());
    assert_eq!(
        execute(&store, inspect()).await.outcome(),
        Outcome::NotAuthorized
    );
    store.close().await.unwrap();
}

#[test]
fn deferred_work_does_not_hide_its_revocation_or_consume_other_players_slots() {
    let db = Connection::open_in_memory().unwrap();
    crate::domains::community::schema::initialize_or_validate(&db).unwrap();
    for n in 1..=34 {
        let player = format!("00000000-0000-4000-8000-{n:012}");
        db.execute(
            "INSERT INTO gov_players(player_id,display_name) VALUES (?1,'Player')",
            [&player],
        )
        .unwrap();
        delivery::request(&db, &player, Permission::Member, true, 0, None).unwrap();
    }
    delivery::request(&db, PLAYER, Permission::Moderator, false, 1, None).unwrap();

    let ready = delivery::pending(&db, &[PLAYER.into()]).unwrap();
    assert_eq!(ready.len(), 33);
    assert_eq!(ready[0].player_id, PLAYER);
    assert!(!ready[0].enabled);
    assert!(
        ready[1..]
            .iter()
            .all(|work| work.player_id != PLAYER && work.enabled)
    );
    assert!(ready[32].player_id.ends_with("000000000033"));
}
