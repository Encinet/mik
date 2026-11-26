use super::{
    model::{Destination, destination, player_snapshot, visibility_allows},
    presence::{GameView, Peer, active_web_players},
};
use crate::domains::community::BindingIndex;
use mik_protocol::wire::{ChatEvent, ChatPlayer, ChatVisibility, ChatWebPeer, chat_record::Kind};
use regex::Regex;
use std::{ops::Range, sync::LazyLock};

pub(super) const RESOLUTION_LIMIT: usize = 64;

// East Asian prose may touch a handle without a space, as on Paper.
#[expect(clippy::expect_used, reason = "Static mention boundary pattern")]
static WORD: LazyLock<Regex> = LazyLock::new(|| {
    Regex::new(r"^[\p{L}\p{Nd}\p{Mn}\p{Mc}_--[\p{Han}\p{Hiragana}\p{Katakana}\p{Hangul}]]$")
        .expect("mention word character pattern")
});

fn word(ch: char) -> bool {
    if ch.is_ascii() {
        ch.is_ascii_alphanumeric() || ch == '_'
    } else {
        WORD.is_match(ch.encode_utf8(&mut [0; 4]))
    }
}

fn name_byte(byte: u8) -> bool {
    byte.is_ascii_alphanumeric() || byte == b'_'
}

pub(super) fn valid_name(name: &str) -> bool {
    (1..=16).contains(&name.len()) && name.bytes().all(name_byte)
}

pub(super) fn candidates(
    text: &str,
    range: Range<usize>,
) -> impl Iterator<Item = (Range<usize>, &str)> {
    text[range.clone()]
        .match_indices('@')
        .filter_map(move |(offset, _)| {
            let start = range.start + offset;
            if text[..start].ends_with(|ch| word(ch) || "@/:#".contains(ch)) {
                return None;
            }
            let tail = text[start + 1..range.end].trim_start_matches([' ', '\t']);
            let length = tail.bytes().take_while(|byte| name_byte(*byte)).count();
            let name = &tail[..length];
            if !valid_name(name) {
                return None;
            }
            let end = range.end - tail.len() + length;
            let rest = &text[end..];
            if rest.starts_with(|ch| word(ch) || ch == '@')
                || rest.strip_prefix(':').is_some_and(|rest| {
                    rest.starts_with(|ch: char| ch.is_ascii_alphanumeric() || ch == '[')
                })
            {
                return None;
            }
            Some((start..end, name))
        })
}

/// Resolves Minecraft account names against the audience of an already restricted event.
pub(super) struct Lookup<'a> {
    game: &'a GameView,
    web: &'a [ChatWebPeer],
    bindings: Option<&'a BindingIndex>,
    author: Option<&'a str>,
    destination: Destination<'a>,
    visibility: Option<&'a ChatVisibility>,
    peers: Option<Vec<Peer<'a>>>,
}

impl<'a> Lookup<'a> {
    pub(super) fn new(
        entry: &'a ChatEvent,
        game: Option<&'a GameView>,
        web: &'a [ChatWebPeer],
        bindings: Option<&'a BindingIndex>,
    ) -> Option<Self> {
        let record = entry.record.as_ref()?;
        let Kind::User(user) = record.kind.as_ref()? else {
            return None;
        };
        Some(Self {
            game: game?,
            web,
            bindings,
            author: user
                .author
                .as_ref()
                .and_then(player_snapshot)
                .map(|player| player.id.as_str()),
            destination: destination(record).ok()?,
            visibility: entry.visibility.as_ref(),
            peers: None,
        })
    }

    pub(super) fn resolve(&mut self, name: &str) -> Option<ChatPlayer> {
        self.resolve_name(name, true)
    }

    /// Paper already interpreted the local participant list. Complete only an offline identity.
    pub(super) fn resolve_offline(&mut self, name: &str) -> Option<ChatPlayer> {
        if !self.allows_offline() {
            return None;
        }
        self.resolve_name(name, false)
    }

    fn allows_offline(&self) -> bool {
        matches!(self.destination, Destination::Public) && self.visibility.is_none()
    }

    fn resolve_name(&mut self, name: &str, allow_live: bool) -> Option<ChatPlayer> {
        let peers = self
            .peers
            .get_or_insert_with(|| self.game.peers(self.author.unwrap_or_default(), self.web));
        let mut candidates = peers.iter().filter(|peer| {
            let id = peer.player.id.as_str();
            peer.player.name.eq_ignore_ascii_case(name)
                && self.destination.includes(id, peer.staff)
                && visibility_allows(self.visibility, id)
                && Some(id) != self.author
        });
        if let Some(peer) = candidates.next() {
            return (allow_live && candidates.next().is_none()).then(|| peer.player.clone());
        }
        if !self.allows_offline() {
            return None;
        }
        let binding = self.bindings?.named(name)?;
        let id = binding.account.to_string();
        // An active UUID cannot be recovered via stale binding names or around visibility filters.
        if self.author == Some(id.as_str())
            || self.game.has_player(&id)
            || active_web_players(self.web).any(|player| player.id == id)
        {
            return None;
        }
        Some(ChatPlayer {
            id,
            name: binding.player_name.clone(),
        })
    }
}
