use super::{
    chat::Platform,
    config::{CommandPrefix, Config},
};
use crate::domains::community::{ExternalKey, Membership, MembershipEvidence};
use lru::LruCache;
use matrix_sdk::ruma::{OwnedRoomId, OwnedUserId, RoomId, UserId};
use std::{
    collections::{BTreeSet, HashMap},
    num::NonZeroUsize,
    sync::{
        RwLock,
        atomic::{AtomicBool, Ordering},
    },
    time::{Duration, Instant},
};

const QQ_ROSTER_AGE: Duration = Duration::from_secs(300);
const MATRIX_EVIDENCE_AGE: Duration = Duration::from_secs(90);

#[derive(Default)]
pub(crate) struct Directory {
    qq: Option<Qq>,
    matrix: RwLock<HashMap<OwnedRoomId, MatrixObservation>>,
    qq_command_prefix: CommandPrefix,
    matrix_command_prefix: CommandPrefix,
}

#[derive(Clone, Copy)]
pub(super) enum MatrixSync {
    Unavailable,
    Joined { synced: bool, changed: bool },
}

#[derive(Default)]
struct MatrixObservation {
    members: Option<Box<[OwnedUserId]>>,
    synced_at: Option<Instant>,
    revision: u64,
}

impl MatrixObservation {
    fn current(&self) -> Option<&[OwnedUserId]> {
        self.synced_at
            .filter(|at| at.elapsed() <= MATRIX_EVIDENCE_AGE)?;
        self.members.as_deref()
    }

    fn invalidate(&mut self) -> Option<Box<[OwnedUserId]>> {
        self.revision = self.revision.wrapping_add(1);
        self.members.take()
    }
}

struct Qq {
    issuer: String,
    ready: AtomicBool,
    membership_rooms: BTreeSet<String>,
    sources: RwLock<HashMap<String, QqRoom>>,
}

struct QqRoom {
    observed: LruCache<String, Observation>,
    complete: Option<(BTreeSet<String>, Instant)>,
    cleared: Option<Instant>,
    last_eviction: Option<Instant>,
}

struct Observation {
    joined: bool,
    observed: Instant,
}

impl Default for QqRoom {
    fn default() -> Self {
        const OBSERVATION_LIMIT: NonZeroUsize = NonZeroUsize::new(4096).expect("nonzero capacity");
        Self {
            observed: LruCache::sparse(OBSERVATION_LIMIT),
            complete: None,
            cleared: None,
            last_eviction: None,
        }
    }
}

impl QqRoom {
    fn observe(&mut self, subject: &str, joined: bool, observed: Instant) {
        if let Some((members, _)) = &mut self.complete {
            update_member(members, subject, joined);
        }
        if let Some((previous, evidence)) = self
            .observed
            .push(subject.into(), Observation { joined, observed })
            && previous != subject
        {
            self.last_eviction = Some(evidence.observed);
        }
    }

    fn replace_roster(&mut self, started: Instant, mut members: BTreeSet<String>) {
        if self.cleared.is_some_and(|at| at >= started)
            || self.last_eviction.is_some_and(|at| at >= started)
        {
            return;
        }
        self.observed.retain(|subject, event| {
            if event.observed >= started {
                update_member(&mut members, subject, event.joined);
                true
            } else {
                members.contains(subject) == event.joined
            }
        });
        self.complete = Some((members, started));
    }
}

fn update_member(members: &mut BTreeSet<String>, subject: &str, joined: bool) {
    if joined {
        if !members.contains(subject) {
            members.insert(subject.into());
        }
    } else {
        members.remove(subject);
    }
}

impl Directory {
    pub(crate) fn new(config: &Config) -> Self {
        Self {
            qq: config.qq.as_ref().map(|qq| Qq {
                issuer: qq.app_id.clone(),
                ready: AtomicBool::new(false),
                membership_rooms: qq
                    .groups
                    .iter()
                    .filter(|(_, conversation)| conversation.membership)
                    .map(|(room, _)| room.clone())
                    .collect(),
                sources: RwLock::new(
                    qq.groups
                        .keys()
                        .map(|name| (name.clone(), QqRoom::default()))
                        .collect(),
                ),
            }),
            matrix: RwLock::new(
                config
                    .matrix
                    .iter()
                    .flat_map(|matrix| matrix.rooms.iter())
                    .filter(|(_, conversation)| conversation.membership)
                    .map(|(id, _)| (id.clone(), MatrixObservation::default()))
                    .collect(),
            ),
            qq_command_prefix: config
                .qq
                .as_ref()
                .map_or_else(CommandPrefix::default, |qq| qq.command_prefix),
            matrix_command_prefix: config
                .matrix
                .as_ref()
                .map_or_else(CommandPrefix::default, |matrix| matrix.command_prefix),
        }
    }

    pub(super) fn observe_qq(&self, room: &str, subject: &str, joined: bool) {
        let Some(qq) = &self.qq else {
            return;
        };
        if let Ok(mut sources) = qq.sources.write()
            && let Some(source) = sources.get_mut(room)
        {
            source.observe(subject, joined, Instant::now());
        }
    }

    pub(super) fn clear_qq(&self, room: Option<&str>) {
        if let Some(qq) = &self.qq
            && let Ok(mut sources) = qq.sources.write()
        {
            for (name, members) in sources.iter_mut() {
                if room.is_none_or(|room| room == name) {
                    *members = QqRoom {
                        cleared: Some(Instant::now()),
                        ..Default::default()
                    };
                }
            }
        }
    }

    pub(super) fn replace_qq(&self, room: &str, started: Instant, members: BTreeSet<String>) {
        if let Some(qq) = &self.qq
            && let Ok(mut rooms) = qq.sources.write()
            && let Some(source) = rooms.get_mut(room)
        {
            source.replace_roster(started, members);
        }
    }

    pub(super) fn set_qq_ready(&self, ready: bool) {
        if let Some(qq) = &self.qq {
            qq.ready.store(ready, Ordering::Release);
        }
    }

    fn configured_platforms(&self) -> impl Iterator<Item = &'static str> {
        [
            (
                self.qq
                    .as_ref()
                    .is_some_and(|qq| !qq.membership_rooms.is_empty()),
                Platform::Qq,
            ),
            (
                self.matrix.read().is_ok_and(|rooms| !rooms.is_empty()),
                Platform::Matrix,
            ),
        ]
        .into_iter()
        .filter_map(|(configured, platform)| configured.then_some(platform.id()))
    }
}

impl MembershipEvidence for Directory {
    fn ready_platforms(&self) -> Vec<String> {
        let mut platforms = Vec::new();
        if self
            .qq
            .as_ref()
            .is_some_and(|qq| qq.ready.load(Ordering::Acquire) && !qq.membership_rooms.is_empty())
        {
            platforms.push(Platform::Qq.id().into());
        }
        if self
            .matrix
            .read()
            .is_ok_and(|rooms| rooms.values().any(|room| room.current().is_some()))
        {
            platforms.push(Platform::Matrix.id().into());
        }
        platforms
    }

    fn binding_platforms(&self) -> Vec<&'static str> {
        self.configured_platforms().collect()
    }

    fn binding_command_prefix(&self, platform: &str) -> &'static str {
        match platform {
            "qq" => self.qq_command_prefix,
            "matrix" => self.matrix_command_prefix,
            _ => CommandPrefix::default(),
        }
        .as_str()
    }

    fn lookup(&self, external: &ExternalKey) -> Membership {
        let [platform, issuer, scope, subject] = external.parts();
        if !scope.is_empty() {
            return Membership::Unknown;
        }
        match platform {
            "qq" => self.state(Platform::Qq, issuer, subject),
            "matrix"
                if <&UserId>::try_from(subject)
                    .is_ok_and(|subject| subject.server_name().as_str() == issuer) =>
            {
                self.state(Platform::Matrix, issuer, subject)
            }
            _ => Membership::Unknown,
        }
    }
}

impl Directory {
    pub(super) fn observe_matrix_room(&self, id: &RoomId, update: MatrixSync) -> bool {
        let Ok(mut rooms) = self.matrix.write() else {
            return false;
        };
        let Some(room) = rooms.get_mut(id) else {
            return false;
        };
        let retired = match update {
            MatrixSync::Unavailable if room.synced_at.take().is_some() => room.invalidate(),
            MatrixSync::Unavailable => None,
            MatrixSync::Joined { synced, changed } => {
                room.synced_at = Some(Instant::now());
                if changed || (!synced && room.members.is_some()) {
                    room.invalidate()
                } else {
                    None
                }
            }
        };
        let needs_roster = room.synced_at.is_some() && room.members.is_none();
        drop(rooms);
        drop(retired);
        needs_roster
    }

    pub(super) fn pending_matrix_room(&self, id: &RoomId) -> Option<u64> {
        let rooms = self.matrix.read().ok()?;
        let room = rooms.get(id)?;
        (room.synced_at.is_some() && room.members.is_none()).then_some(room.revision)
    }

    pub(super) fn publish_matrix_room(
        &self,
        id: &RoomId,
        revision: u64,
        members: Box<[OwnedUserId]>,
    ) {
        if let Ok(mut rooms) = self.matrix.write()
            && let Some(room) = rooms.get_mut(id)
            && room.revision == revision
            && room.synced_at.is_some()
            && room.members.is_none()
        {
            room.members = Some(members);
        }
    }

    pub(super) fn clear_matrix(&self) {
        let retired: Vec<_> = if let Ok(mut rooms) = self.matrix.write() {
            rooms
                .values_mut()
                .filter_map(|room| {
                    room.synced_at.take()?;
                    room.invalidate()
                })
                .collect()
        } else {
            return;
        };
        drop(retired);
    }

    pub(super) fn qq_known_absent(&self, issuer: &str, room: &str, subject: &str) -> bool {
        self.qq
            .as_ref()
            .filter(|qq| qq.issuer == issuer && qq.ready.load(Ordering::Acquire))
            .and_then(|qq| qq.sources.read().ok())
            .and_then(|rooms| {
                rooms
                    .get(room)
                    .and_then(|members| qq_observation(members, subject))
            })
            == Some(false)
    }

    pub(super) fn state(&self, platform: Platform, issuer: &str, subject: &str) -> Membership {
        match platform {
            Platform::Qq => {
                let Some(qq) = self.qq.as_ref().filter(|config| {
                    config.issuer == issuer && config.ready.load(Ordering::Acquire)
                }) else {
                    return Membership::Unknown;
                };
                qq.sources
                    .read()
                    .ok()
                    .map_or(Membership::Unknown, |sources| {
                        aggregate(qq.membership_rooms.iter().map(|room| {
                            sources
                                .get(room)
                                .and_then(|members| qq_observation(members, subject))
                        }))
                    })
            }
            Platform::Matrix => self
                .matrix
                .read()
                .ok()
                .map_or(Membership::Unknown, |rooms| {
                    aggregate(rooms.values().map(|room| {
                        room.current().map(|members| {
                            members
                                .binary_search_by(|id| id.as_str().cmp(subject))
                                .is_ok()
                        })
                    }))
                }),
        }
    }
}

fn qq_observation(source: &QqRoom, subject: &str) -> Option<bool> {
    source
        .observed
        .peek(subject)
        .map(|entry| entry.joined)
        .or_else(|| {
            source
                .complete
                .as_ref()
                .filter(|(_, at)| at.elapsed() <= QQ_ROSTER_AGE)
                .map(|(members, _)| members.contains(subject))
        })
}

fn aggregate(mut sources: impl Iterator<Item = Option<bool>>) -> Membership {
    let mut all_absent = sources
        .next()
        .map_or(Membership::Unknown, |first| match first {
            Some(true) => Membership::Joined,
            Some(false) => Membership::Absent,
            None => Membership::Unknown,
        });
    if all_absent == Membership::Joined {
        return all_absent;
    }
    for evidence in sources {
        match evidence {
            Some(true) => return Membership::Joined,
            Some(false) => (),
            None => all_absent = Membership::Unknown,
        }
    }
    all_absent
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn binding_command_prefixes_come_from_platform_configuration() {
        let yaml = "\
qq: { app_id: sample, groups: {} }
matrix: { homeserver: 'https://example.org', user: '@bridge:example.org', rooms: {}, command_prefix: '!' }
";
        let config = serde_yaml_ng::from_str(yaml).unwrap();
        let directory = Directory::new(&config);
        assert_eq!(directory.binding_command_prefix("qq"), "/");
        assert_eq!(directory.binding_command_prefix("matrix"), "!");

        let invalid = yaml.replace("command_prefix: '!'", "command_prefix: '?'");
        assert!(serde_yaml_ng::from_str::<Config>(&invalid).is_err());
    }

    #[test]
    fn matrix_uses_available_rooms_but_requires_every_source_to_prove_absence() {
        let config: Config = serde_yaml_ng::from_str(
            "matrix:\n  homeserver: https://example.org\n  user: '@bridge:example.org'\n  rooms:\n    '!first:example.org': { membership: true }\n    '!second:example.org': { membership: true }",
        )
        .unwrap();
        let directory = Directory::new(&config);
        let first = matrix_sdk::ruma::room_id!("!first:example.org");
        let second = matrix_sdk::ruma::room_id!("!second:example.org");
        let subject = "@alice:example.org";
        let state = || directory.state(Platform::Matrix, "example.org", subject);

        let quiet = || MatrixSync::Joined {
            synced: true,
            changed: false,
        };
        let load = |room: &RoomId, members: Box<[OwnedUserId]>| {
            let revision = directory.pending_matrix_room(room).unwrap();
            directory.publish_matrix_room(room, revision, members);
        };
        assert!(directory.observe_matrix_room(first, quiet()));
        load(first, Box::new([UserId::parse(subject).unwrap()]));
        assert!(matches!(state(), Membership::Joined));
        assert_eq!(directory.ready_platforms(), ["matrix"]);

        directory
            .matrix
            .write()
            .unwrap()
            .get_mut(first)
            .unwrap()
            .synced_at = Some(
            Instant::now()
                .checked_sub(MATRIX_EVIDENCE_AGE + Duration::from_secs(1))
                .unwrap(),
        );
        assert!(matches!(state(), Membership::Unknown));
        assert!(!directory.observe_matrix_room(first, quiet()));
        assert!(directory.pending_matrix_room(first).is_none());
        assert!(matches!(state(), Membership::Joined));

        assert!(directory.observe_matrix_room(
            first,
            MatrixSync::Joined {
                synced: true,
                changed: true
            }
        ));
        let old = directory.pending_matrix_room(first).unwrap();
        directory.clear_matrix();
        assert!(directory.observe_matrix_room(
            first,
            MatrixSync::Joined {
                synced: false,
                changed: false
            }
        ));
        directory.publish_matrix_room(first, old, Box::new([UserId::parse(subject).unwrap()]));
        assert!(matches!(state(), Membership::Unknown));
        load(first, Box::new([]));
        assert!(matches!(state(), Membership::Unknown));
        assert!(directory.observe_matrix_room(second, quiet()));
        load(second, Box::new([]));
        assert!(matches!(state(), Membership::Absent));

        assert!(!directory.observe_matrix_room(second, MatrixSync::Unavailable));
        assert!(matches!(state(), Membership::Unknown));
        directory.clear_matrix();
        assert!(directory.ready_platforms().is_empty());
    }

    #[test]
    fn qq_gateway_evidence_survives_quiet_members_and_newer_events_win() {
        let mut room = QqRoom::default();
        let stale = Instant::now()
            .checked_sub(QQ_ROSTER_AGE + Duration::from_secs(1))
            .unwrap();
        room.observe("quiet", true, stale);
        room.replace_roster(
            stale,
            BTreeSet::from(["quiet".into(), "roster-only".into()]),
        );
        assert_eq!(qq_observation(&room, "quiet"), Some(true));
        assert_eq!(qq_observation(&room, "roster-only"), None);

        let started = Instant::now();
        room.observe("quiet", false, started);
        room.replace_roster(started, BTreeSet::from(["quiet".into()]));
        assert_eq!(qq_observation(&room, "quiet"), Some(false));
        assert_eq!(
            room.complete.as_ref().map(|(members, _)| members.len()),
            Some(0)
        );
    }
}
