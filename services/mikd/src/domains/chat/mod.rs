mod cursor;
mod history;
mod links;
mod players;
mod presence;
mod routing;
mod service;
pub(crate) mod web;
pub(crate) use history::{HistoryError, HistoryStore};
pub(crate) use links::link_destination;
pub(crate) use service::{ChatService, Committed};

pub(crate) fn now() -> i64 {
    let now = time::OffsetDateTime::now_utc();
    now.unix_timestamp() * 1_000 + i64::from(now.millisecond())
}
