use super::{
    content::decrypted_message,
    sync::{self, Input, Status},
};
use futures_util::StreamExt;
use matrix_sdk::ruma::{
    OwnedEventId, events::room::encrypted::OriginalSyncRoomEncryptedEvent, serde::Raw,
};
use matrix_sdk::{
    Client, Room, RoomState,
    deserialized_responses::{TimelineEvent, TimelineEventKind},
};
use std::{collections::VecDeque, io, time::Duration};
use tokio::{
    pin,
    sync::{mpsc, watch},
    time::Instant,
};

const MAX_PENDING: usize = 64;
const KEY_WAIT: Duration = Duration::from_secs(90);

pub(super) struct Pending {
    epoch: u64,
    room: Room,
    encrypted: Ciphertext,
    expires: Instant,
}

struct Ciphertext {
    id: OwnedEventId,
    session: String,
    raw: Raw<OriginalSyncRoomEncryptedEvent>,
}

impl Ciphertext {
    fn waiting_for_key(event: TimelineEvent) -> Option<Self> {
        let TimelineEventKind::UnableToDecrypt { event, utd_info } = event.kind else {
            return None;
        };
        if !utd_info.reason.is_missing_room_key() {
            return None;
        }
        Some(Self {
            id: event.get_field("event_id").ok()??,
            session: utd_info.session_id?,
            raw: event.cast_unchecked(),
        })
    }
}

impl Pending {
    pub(super) fn from_event(epoch: u64, room: &Room, event: TimelineEvent) -> Option<Self> {
        Some(Self {
            epoch,
            room: room.clone(),
            encrypted: Ciphertext::waiting_for_key(event)?,
            expires: Instant::now() + KEY_WAIT,
        })
    }

    fn active(&self, state: &watch::Receiver<Status>) -> bool {
        state.borrow().epoch == Some(self.epoch)
            && self.expires > Instant::now()
            && self.room.state() == RoomState::Joined
            && self.room.encryption_state().is_encrypted()
    }

    async fn retry(&self, state: &watch::Receiver<Status>, inbound: &mpsc::Sender<Input>) -> bool {
        if !self.active(state) {
            return false;
        }
        let Ok(event) = self.room.decrypt_event(&self.encrypted.raw, None).await else {
            return false;
        };
        if !self.active(state) {
            return false;
        }
        if let Some(message) = decrypted_message(&event) {
            sync::deliver(inbound, self.epoch, &self.room, message);
        }
        matches!(event.kind, TimelineEventKind::UnableToDecrypt { utd_info, .. } if utd_info.reason.is_missing_room_key())
    }
}

pub(super) async fn run(
    client: &Client,
    mut state: watch::Receiver<Status>,
    mut input: mpsc::Receiver<Pending>,
    inbound: &mpsc::Sender<Input>,
) -> io::Result<()> {
    let keys = client
        .encryption()
        .room_keys_received_stream()
        .await
        .ok_or_else(|| io::Error::other("Matrix room key stream is unavailable"))?;
    pin!(keys);
    let mut pending = VecDeque::<Pending>::with_capacity(MAX_PENDING);
    loop {
        tokio::select! {
            changed = state.changed() => {
                if changed.is_err() { return Ok(()); }
                pending.retain(|event| event.active(&state));
            }
            next = input.recv() => {
                let Some(event) = next else { return Ok(()); };
                if !pending.iter().any(|queued| queued.epoch == event.epoch
                    && queued.room.room_id() == event.room.room_id() && queued.encrypted.id == event.encrypted.id)
                    && event.retry(&state, inbound).await
                {
                    if pending.len() == MAX_PENDING { pending.pop_front(); }
                    pending.push_back(event);
                }
            }
            update = keys.next() => {
                let Some(update) = update else {
                    return Err(io::Error::other("Matrix room key stream closed"));
                };
                for _ in 0..pending.len() {
                    let Some(event) = pending.pop_front() else { break; };
                    if !event.active(&state) { continue; }
                    let retry = update.as_ref().map_or(true, |keys| keys.iter().any(|key| {
                        key.room_id == event.room.room_id() && key.session_id == event.encrypted.session
                    }));
                    if !retry || event.retry(&state, inbound).await {
                        pending.push_back(event);
                    }
                }
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use matrix_sdk::deserialized_responses::{
        UnableToDecryptInfo, UnableToDecryptReason, WithheldCode,
    };
    use serde_json::json;

    #[test]
    fn only_missing_keys_enter_the_retry_queue() {
        let raw = Raw::new(&json!({ "event_id": "$late", "type": "m.room.encrypted" }))
            .unwrap()
            .cast_unchecked();
        let waiting = |reason| {
            TimelineEvent::from_utd(
                raw.clone(),
                UnableToDecryptInfo {
                    session_id: Some("session".into()),
                    reason,
                },
            )
        };
        let admitted =
            Ciphertext::waiting_for_key(waiting(UnableToDecryptReason::MissingMegolmSession {
                withheld_code: None,
            }))
            .unwrap();
        assert_eq!(admitted.id.as_str(), "$late");
        assert_eq!(admitted.session, "session");
        assert!(
            Ciphertext::waiting_for_key(waiting(UnableToDecryptReason::MissingMegolmSession {
                withheld_code: Some(WithheldCode::Unverified),
            }))
            .is_none()
        );
        assert!(Ciphertext::waiting_for_key(TimelineEvent::from_plaintext(raw)).is_none());
    }
}
