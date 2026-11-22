use super::super::super::{membership::MatrixSync, runtime::Runtime};
use super::content::{Message, decrypted_message};
use super::late_keys::Pending;
use super::{Matrix, request};
use matrix_sdk::ruma::{RoomId, api::error::ErrorKind, serde::Raw};
use matrix_sdk::{
    Client, Room, RoomState,
    config::SyncSettings,
    encryption::VerificationState,
    sync::{JoinedRoomUpdate, State},
};
use std::{collections::HashSet, io, time::Duration};
use tokio::{
    sync::{mpsc, watch},
    time::sleep,
};

#[derive(Clone, Copy, Default, PartialEq, Eq)]
pub(super) struct Status {
    generation: u64,
    pub(super) epoch: Option<u64>,
}

impl Status {
    fn observe(&mut self, verified: bool) -> bool {
        if verified == self.epoch.is_some() {
            return false;
        }
        self.epoch = verified.then(|| {
            self.generation = self.generation.wrapping_add(1);
            self.generation
        });
        true
    }
}

pub(super) struct Input {
    pub(super) epoch: u64,
    pub(super) room: Room,
    pub(super) event: Message,
}

pub(super) fn deliver(inbound: &mpsc::Sender<Input>, epoch: u64, room: &Room, event: Message) {
    if inbound
        .try_send(Input {
            epoch,
            room: room.clone(),
            event,
        })
        .is_err()
    {
        eprintln!("mikd Matrix input is backpressured");
    }
}

pub(super) async fn run(
    config: &Matrix,
    client: &Client,
    runtime: &Runtime,
    ready: watch::Sender<Status>,
    roster_changes: &watch::Sender<()>,
    inbound: &mpsc::Sender<Input>,
    missing_keys: mpsc::Sender<Pending>,
) -> io::Result<()> {
    let mut active = HashSet::<&RoomId>::new();
    let mut delay = Duration::from_secs(1);
    let mut logged_unverified = false;
    loop {
        match client.sync_once(SyncSettings::default()).await {
            Ok(response) => {
                delay = Duration::from_secs(1);
                let verified =
                    client.encryption().verification_state().get() == VerificationState::Verified;
                if !verified {
                    ready.send_if_modified(|status| status.observe(false));
                    active.clear();
                    runtime.membership().clear_matrix();
                    if !logged_unverified {
                        eprintln!("mikd Matrix bridge waiting for a verified device");
                        logged_unverified = true;
                    }
                    continue;
                }
                logged_unverified = false;
                let mut needs_roster = false;
                for (id, route) in &config.rooms {
                    if !route.membership {
                        continue;
                    }
                    let update = joined_room(client, id).map_or(MatrixSync::Unavailable, |room| {
                        MatrixSync::Joined {
                            synced: room.are_members_synced(),
                            changed: response
                                .rooms
                                .joined
                                .get(id)
                                .is_some_and(has_roster_changes),
                        }
                    });
                    needs_roster |= runtime.membership().observe_matrix_room(id, update);
                }
                if needs_roster {
                    roster_changes.send_replace(());
                }
                ready.send_if_modified(|status| status.observe(true));
                let Some(epoch) = ready.borrow().epoch else {
                    unreachable!()
                };
                for (id, update) in response.rooms.joined {
                    if !active.contains::<RoomId>(&id) {
                        continue;
                    }
                    let Some(room) = client
                        .get_room(&id)
                        .filter(|room| room.encryption_state().is_encrypted())
                    else {
                        continue;
                    };
                    for event in update.timeline.events {
                        if let Some(message) = decrypted_message(&event) {
                            deliver(inbound, epoch, &room, message);
                        } else if let Some(pending) = Pending::from_event(epoch, &room, event)
                            && missing_keys.try_send(pending).is_err()
                        {
                            eprintln!("mikd Matrix pending decryption is backpressured");
                        }
                    }
                }
                active.clear();
                active.extend(
                    config
                        .rooms
                        .keys()
                        .map(AsRef::as_ref)
                        .filter(|id| joined_room(client, id).is_some()),
                );
            }
            Err(error) => {
                ready.send_if_modified(|status| status.observe(false));
                active.clear();
                runtime.membership().clear_matrix();
                if matches!(
                    error.client_api_error_kind(),
                    Some(ErrorKind::UnknownToken { .. })
                ) {
                    return Err(io::Error::other(
                        "Matrix session revoked; provision a new device explicitly",
                    ));
                }
                if delay == Duration::from_secs(1) {
                    eprintln!("mikd Matrix sync unavailable; pausing delivery until it recovers");
                }
                let retry_after = request::sdk_http(&error).and_then(request::retry_after);
                sleep(delay.max(retry_after.unwrap_or_default())).await;
                delay = (delay * 2).min(Duration::from_secs(30));
            }
        }
    }
}

pub(super) fn joined_room(client: &Client, id: &RoomId) -> Option<Room> {
    client
        .get_room(id)
        .filter(|room| room.state() == RoomState::Joined)
}

fn has_roster_changes(update: &JoinedRoomUpdate) -> bool {
    if update.timeline.limited {
        return true;
    }
    match &update.state {
        State::Before(events) => {
            events.iter().any(is_member)
                || update
                    .timeline
                    .events
                    .iter()
                    .any(|event| is_member(event.raw()))
        }
        State::After(events) => events.iter().any(is_member),
    }
}

fn is_member<Event>(event: &Raw<Event>) -> bool {
    event
        .get_field::<&str>("type")
        .map_or(true, |kind| kind == Some("m.room.member"))
}

#[cfg(test)]
mod tests {
    use super::{JoinedRoomUpdate, Raw, State, Status, has_roster_changes};

    #[test]
    fn successful_syncs_preserve_epoch_but_reconnect_rejects_old_work() {
        let mut status = Status::default();
        assert_eq!(status.epoch, None);
        assert!(status.observe(true));
        let queued = status.epoch;
        assert!(queued.is_some());
        assert!(!status.observe(true));
        assert_eq!(status.epoch, queued);
        status.observe(false);
        assert_eq!(status.epoch, None);
        status.observe(true);
        assert!(status.epoch.is_some());
        assert_ne!(status.epoch, queued);
    }

    #[test]
    fn member_events_and_timeline_gaps_dirty_the_roster() {
        let mut update = JoinedRoomUpdate::default();
        assert!(!has_roster_changes(&update));
        update.state = State::Before(vec![
            Raw::from_json_string(r#"{"type":"m.room.member"}"#.to_owned()).unwrap(),
        ]);
        assert!(has_roster_changes(&update));
        update.state = State::default();
        update.timeline.limited = true;
        assert!(has_roster_changes(&update));
    }
}
