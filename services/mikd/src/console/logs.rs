use super::requests::{Failure, invalid_receipt};
use mik_protocol::wire::{
    ConsoleLogQuery, ConsoleLogReceipt, console_log_query::Position, console_log_receipt::Result,
};
use serde::{Deserialize, Serialize};
use std::io;

const MAX_CURSOR_BYTES: usize = 512;
const MAX_LINES: usize = 256;
const MAX_TEXT_BYTES: usize = 64 * 1024;
const MAX_NOTICE_BYTES: usize = 512;

#[derive(Clone, Deserialize, Serialize)]
#[serde(rename_all = "snake_case")]
pub(crate) enum LogPosition {
    Latest,
    Before(String),
    After(String),
}

#[derive(Deserialize, Serialize)]
pub(crate) struct LogPage {
    pub lines: Vec<String>,
    pub before: Option<String>,
    pub after: Option<String>,
    pub notice: Option<String>,
}

impl LogPosition {
    pub(super) fn into_query(self) -> io::Result<ConsoleLogQuery> {
        let position = match self {
            Self::Latest => None,
            Self::Before(cursor) if valid_cursor(&cursor) => Some(Position::Before(cursor)),
            Self::After(cursor) if valid_cursor(&cursor) => Some(Position::After(cursor)),
            _ => {
                return Err(io::Error::new(
                    io::ErrorKind::InvalidInput,
                    "Invalid log cursor",
                ));
            }
        };
        Ok(ConsoleLogQuery { position })
    }
}

pub(super) fn request_error(failure: Failure) -> io::Error {
    let message = match failure {
        Failure::Offline => "The console log connection is offline",
        Failure::Busy => "A console log query is already in progress",
        Failure::Expired => "The console log query expired before delivery",
        Failure::Unconfirmed => "The console log provider did not return a result",
    };
    io::Error::other(message)
}

pub(super) fn receipt(receipt: ConsoleLogReceipt) -> io::Result<(String, io::Result<LogPage>)> {
    let result = match receipt.result {
        Some(Result::Page(page))
            if page.lines.len() <= MAX_LINES
                && page.lines.iter().map(String::len).sum::<usize>() <= MAX_TEXT_BYTES
                && page.lines.iter().all(|line| !line.contains(['\r', '\n']))
                && (page.before.is_empty() || valid_cursor(&page.before))
                && (page.after.is_empty() || valid_cursor(&page.after))
                && (page.notice.is_empty() || valid_notice(&page.notice)) =>
        {
            Ok(LogPage {
                lines: page.lines,
                before: nonempty(page.before),
                after: nonempty(page.after),
                notice: nonempty(page.notice),
            })
        }
        Some(Result::Error(message)) if valid_notice(&message) => Err(io::Error::other(message)),
        _ => return Err(invalid_receipt()),
    };
    Ok((receipt.id, result))
}

fn valid_cursor(cursor: &str) -> bool {
    (1..=MAX_CURSOR_BYTES).contains(&cursor.len()) && !cursor.chars().any(char::is_control)
}

fn valid_notice(message: &str) -> bool {
    (1..=MAX_NOTICE_BYTES).contains(&message.len()) && !message.chars().any(char::is_control)
}

fn nonempty(value: String) -> Option<String> {
    (!value.is_empty()).then_some(value)
}
