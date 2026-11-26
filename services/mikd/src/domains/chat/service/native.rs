use super::{ChatService, empty_web_roster};
use crate::domains::chat::{
    BodyInput, HistoryError, NativeEvent,
    history::{Admission, Append},
    model::{binding_key, external_principal, room},
    now,
};
use mik_protocol::wire::{
    ChatEvent, ChatExternalIdentity, ChatPlayer, ChatRecord, ChatUserRecord,
    chat_destination::Room, chat_identity::Principal, chat_part::Content, chat_record::Kind,
    chat_reference::Target as ReferenceTarget,
};
use std::time::Duration;
use uuid::Uuid;

const NATIVE_VISIBILITY_WAIT: Duration = Duration::from_millis(750);

impl ChatService {
    pub(crate) async fn publish_native(
        &self,
        account: ChatExternalIdentity,
        native: NativeEvent,
        mut input: Vec<BodyInput>,
    ) -> Result<(), HistoryError> {
        let platform = account.platform.clone();
        self.bind_native_mentions(&mut input)?;
        let player = self.bound_player(&account);
        let game = if player.is_some() {
            Some(
                self.game
                    .wait_for_view(NATIVE_VISIBILITY_WAIT)
                    .await
                    .ok_or(HistoryError::Busy)?,
            )
        } else {
            self.game()
        };
        let mut entry = ChatEvent {
            message_id: Uuid::new_v4().to_string(),
            record: Some(ChatRecord {
                created_at_ms: now(),
                platform,
                kind: Some(Kind::User(ChatUserRecord {
                    destination: Some(room(Room::Public)),
                    author: Some(external_principal(account, player)),
                    body: None,
                })),
            }),
            visibility: None,
        };
        let roster = if game.is_some() {
            self.web_roster()
        } else {
            empty_web_roster()
        };
        self.prepare_post(&mut entry, input, game.as_deref(), &roster.players)?;
        self.commit(Append {
            entry,
            admission: Admission::Native(native),
        })
        .await
        .map(|_| ())
    }

    fn bind_native_mentions(&self, input: &mut [BodyInput]) -> Result<(), HistoryError> {
        for input in input {
            if let BodyInput::Resolved(part) = input
                && let Some(Content::Reference(reference)) = &mut part.content
            {
                let person = match &mut reference.target {
                    Some(ReferenceTarget::Link(_)) => continue,
                    Some(ReferenceTarget::Person(person)) => person,
                    _ => {
                        return Err(HistoryError::Invalid(
                            "native mention requires an external account",
                        ));
                    }
                };
                let Some(Principal::External(actor)) = &mut person.principal else {
                    return Err(HistoryError::Invalid(
                        "native mention requires an external account",
                    ));
                };
                actor.bound_player = actor
                    .account
                    .as_ref()
                    .and_then(|account| self.bound_player(account));
            }
        }
        Ok(())
    }

    fn bound_player(&self, account: &ChatExternalIdentity) -> Option<ChatPlayer> {
        let binding = self.bindings()?.find(&binding_key(account)?)?;
        Some(ChatPlayer {
            id: binding.account.to_string(),
            name: binding.player_name.clone(),
        })
    }
}
