use mik_protocol::music::{
    MusicTrackData,
    music_track_data::{Availability, State},
};
use serde_json::Value;
use std::io;
use uuid::Uuid;

/// Public catalog identity. Provider keys are never accepted in its place.
#[derive(Clone, Copy, Debug, PartialEq, Eq, PartialOrd, Ord, Hash)]
pub(super) struct TrackId(pub Uuid);

impl TrackId {
    pub fn parse(value: &str) -> io::Result<Self> {
        let uuid = Uuid::parse_str(value).map_err(|_| super::invalid("invalid track ID"))?;
        if uuid.is_nil() || value != uuid.hyphenated().to_string() {
            return Err(super::invalid("noncanonical track ID"));
        }
        Ok(Self(uuid))
    }
}

impl std::fmt::Display for TrackId {
    fn fmt(&self, formatter: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        self.0.fmt(formatter)
    }
}

/// An upstream locator. Only adapters and their durable mapping/cache use it.
#[derive(Clone, Debug, PartialEq, Eq, PartialOrd, Ord, Hash)]
pub(super) struct TrackRef {
    pub platform: String,
    pub id: String,
}

impl TrackRef {
    pub fn new(platform: &str, id: String) -> Option<Self> {
        (valid_platform(platform)
            && !id.trim().is_empty()
            && id.len() <= 512
            && !id.chars().any(char::is_control))
        .then(|| Self {
            platform: platform.into(),
            id,
        })
    }
}

pub(super) fn valid_platform(platform: &str) -> bool {
    !platform.is_empty()
        && platform.len() <= 32
        && platform
            .bytes()
            .all(|ch| ch.is_ascii_lowercase() || ch.is_ascii_digit() || matches!(ch, b'_' | b'-'))
}

pub(super) fn label(value: &str) -> String {
    value
        .trim()
        .chars()
        .filter(|ch| !ch.is_control())
        .take(240)
        .collect()
}

pub(super) fn status(
    track_id: TrackId,
    state: State,
    availability: Availability,
) -> MusicTrackData {
    MusicTrackData {
        track_id: track_id.to_string(),
        state: state.into(),
        availability: availability.into(),
        ..Default::default()
    }
}

#[derive(Clone)]
pub(super) struct TrackRecord {
    pub key: TrackRef,
    pub provider: String,
    pub info_json: String,
    pub title: String,
    pub artist: String,
    pub album: String,
    pub duration_ms: Option<i64>,
}

impl TrackRecord {
    pub fn display(&self, id: TrackId, availability: Availability) -> MusicTrackData {
        MusicTrackData {
            title: label(&self.title),
            artist: label(&self.artist),
            album: label(&self.album),
            duration_ms: self.duration_ms.and_then(|value| u64::try_from(value).ok()),
            ..status(id, State::Active, availability)
        }
    }
}

pub(super) struct ResolverInput {
    pub provider: String,
    pub info: Value,
}

impl ResolverInput {
    pub(super) fn from_record(record: &TrackRecord) -> io::Result<Self> {
        Ok(Self {
            provider: record.provider.clone(),
            info: serde_json::from_str(&record.info_json).map_err(io::Error::other)?,
        })
    }
}

pub(super) fn search_rank(
    query: &str,
    title: &str,
    artist: &str,
    album: &str,
    author: &str,
) -> Option<u32> {
    let query = query.to_lowercase();
    let title = title.to_lowercase();
    if query == title {
        return Some(0);
    }
    if title.starts_with(&query) {
        return Some(1);
    }
    if title.contains(&query) {
        return Some(2);
    }
    let fields = format!(
        "{title} {} {} {}",
        artist.to_lowercase(),
        album.to_lowercase(),
        author.to_lowercase()
    );
    query
        .split_whitespace()
        .all(|word| fields.contains(word))
        .then_some(3)
}
