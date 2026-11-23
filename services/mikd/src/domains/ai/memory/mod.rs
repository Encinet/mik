//! Bounded, private Paper memory. Only reviewed changes from delivered turns reach the store.

mod learning;
mod store;
mod worker;

pub(super) use learning::PlayerMemory;

use serde::{Deserialize, Serialize};
use serde_json::Value;
use uuid::Uuid;

pub(super) const NOTES_PER_PLAYER: usize = 8;
pub(super) const NOTE_CHARS: usize = 160;
pub(super) const CHANGE_LIMIT: usize = 3;
pub(super) const MAX_TTL_DAYS: u16 = 365;

#[derive(Eq, Hash, PartialEq)]
struct Scope {
    instance: String,
    player: Uuid,
}

#[derive(Clone, Serialize)]
pub(super) struct Note {
    id: i64,
    text: String,
    #[serde(skip)]
    expires_at: Option<i64>,
}

impl Note {
    fn current(&self, now: i64) -> bool {
        self.expires_at.is_none_or(|deadline| deadline > now)
    }
}

pub(super) struct Snapshot {
    pub(super) generation: Uuid,
    pub(super) notes: Vec<Note>,
}

#[derive(Deserialize, Serialize)]
#[serde(tag = "op", deny_unknown_fields, rename_all = "snake_case")]
pub(super) enum Change {
    Add {
        text: String,
        ttl_days: u16,
    },
    Replace {
        id: i64,
        text: String,
        ttl_days: u16,
    },
    Forget {
        id: i64,
    },
}

impl Change {
    const fn target(&self) -> Option<i64> {
        match *self {
            Self::Add { .. } => None,
            Self::Replace { id, .. } | Self::Forget { id } => Some(id),
        }
    }

    fn new_text(&self) -> Option<&str> {
        match self {
            Self::Add { text, .. } | Self::Replace { text, .. } => Some(text),
            Self::Forget { .. } => None,
        }
    }

    fn normalize(&mut self, request: &str) -> bool {
        let (text, ttl_days) = match self {
            Self::Add { text, ttl_days } | Self::Replace { text, ttl_days, .. } => {
                (text, *ttl_days)
            }
            Self::Forget { .. } => return true,
        };
        if ttl_days > MAX_TTL_DAYS || text.chars().any(char::is_control) {
            return false;
        }
        let trimmed = text.trim();
        if trimmed.is_empty()
            || trimmed.chars().nth(NOTE_CHARS).is_some()
            || trimmed.contains("://")
            || trimmed.eq_ignore_ascii_case(request.trim())
        {
            return false;
        }
        if trimmed.len() != text.len() {
            *text = trimmed.to_owned();
        }
        true
    }
}

#[derive(Serialize)]
pub(super) struct Proposal {
    pub(super) id: u64,
    pub(super) change: Change,
    #[serde(skip_serializing_if = "Option::is_none")]
    previous: Option<String>,
}

pub(super) fn propose(notes: &[Note], changes: &Value, request: &str) -> Vec<Proposal> {
    let Some(changes) = changes
        .as_array()
        .filter(|changes| changes.len() <= CHANGE_LIMIT)
    else {
        return Vec::new();
    };
    let mut proposals = Vec::<Proposal>::new();
    for (id, raw) in (1..).zip(changes) {
        let Ok(mut change) = Change::deserialize(raw) else {
            continue;
        };
        if !change.normalize(request) {
            continue;
        }
        let target = change.target();
        let previous = target.and_then(|id| notes.iter().find(|note| note.id == id));
        if (target.is_some() && previous.is_none())
            || proposals
                .iter()
                .any(|proposal| target.is_some() && proposal.change.target() == target)
            || change.new_text().is_some_and(|text| {
                notes
                    .iter()
                    .any(|note| Some(note.id) != target && note.text.eq_ignore_ascii_case(text))
                    || proposals.iter().any(|proposal| {
                        proposal
                            .change
                            .new_text()
                            .is_some_and(|other| other.eq_ignore_ascii_case(text))
                    })
            })
        {
            continue;
        }
        proposals.push(Proposal {
            id,
            change,
            previous: previous.map(|note| note.text.clone()),
        });
    }
    proposals
}
