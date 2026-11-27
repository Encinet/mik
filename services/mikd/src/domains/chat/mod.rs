mod body;
mod history;
mod links;
mod mentions;
mod model;
mod presence;
mod routing;
mod service;
pub(crate) mod web;
pub(crate) use body::BodyInput;
pub(crate) use history::{HistoryError, HistoryStore};
pub(crate) use links::{label_text, link_destination};
#[cfg(test)]
pub(crate) use model::text_part;
pub(crate) use model::{
    binding_key, current_player, description_kind_label, description_label, external_account,
    external_name, external_principal, link_reference, link_uri, mentioned_person, person_mention,
    player_snapshot, social_platform_label, valid_game_roster,
};
pub(crate) use routing::{Committed, ConversationAddress, NativeEvent};
pub(crate) use service::{ChatService, empty_web_roster};

pub(crate) fn now() -> i64 {
    let now = time::OffsetDateTime::now_utc();
    now.unix_timestamp() * 1_000 + i64::from(now.millisecond())
}
