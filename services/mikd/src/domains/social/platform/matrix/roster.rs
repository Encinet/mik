use super::{super::super::membership::Directory, Matrix, sync::joined_room};
use futures_util::future::join_all;
use matrix_sdk::ruma::{RoomId, UserId};
use matrix_sdk::{Client, RoomMemberships, RoomState};
use tokio::sync::{Semaphore, watch};

pub(super) async fn run(
    config: &Matrix,
    client: &Client,
    directory: &Directory,
    updates: watch::Receiver<()>,
) {
    let reads = Semaphore::new(4);
    join_all(
        config
            .rooms
            .iter()
            .filter(|(_, route)| route.membership)
            .map(|(id, _)| observe(id, &config.user, client, directory, &reads, updates.clone())),
    )
    .await;
    std::future::pending::<()>().await;
}

async fn observe(
    id: &RoomId,
    bot: &UserId,
    client: &Client,
    directory: &Directory,
    reads: &Semaphore,
    mut updates: watch::Receiver<()>,
) {
    while updates.changed().await.is_ok() {
        if directory.pending_matrix_room(id).is_none() {
            continue;
        }
        let Ok(_permit) = reads.acquire().await else {
            break;
        };
        if let Some(revision) = directory.pending_matrix_room(id)
            && let Some(room) = joined_room(client, id)
            && room.sync_members().await.is_ok()
            && let Ok(mut members) = client
                .state_store()
                .get_user_ids(id, RoomMemberships::JOIN)
                .await
            && room.are_members_synced()
            && room.state() == RoomState::Joined
        {
            members.sort_unstable();
            if members
                .binary_search_by(|id| id.as_str().cmp(bot.as_str()))
                .is_ok()
            {
                directory.publish_matrix_room(id, revision, members.into_boxed_slice());
            }
        }
    }
}
