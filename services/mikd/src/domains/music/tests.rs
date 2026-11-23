use super::{Config, Service};
use crate::transport::{SocketListener, serve_music};
use mik_protocol::{
    MUSIC_FRAME_BYTES, MUSIC_NOTE_EVENTS_V1, MUSIC_PREFACE,
    music::{
        LyricsCommand, LyricsLookup, LyricsRead, MusicCatalogQuery, MusicCollectionQuery,
        MusicDecodeFailure, MusicMediaOpened, MusicOpenMedia, MusicReadMedia, MusicReply,
        MusicRepresentation, MusicRequest, MusicTrackQuery, NoteScore,
        lyrics_command::Operation as LyricOperation,
        lyrics_result::State as LyricsState,
        music_failure::Code,
        music_open_media::{Constraint, Readiness},
        music_read_end::Outcome as End,
        music_reply::Result as Reply,
        music_request::Operation,
        music_track_data::{Availability, State},
    },
};
use prost::Message;
use std::{
    os::unix::fs::{MetadataExt, PermissionsExt},
    path::Path,
    sync::Arc,
    time::Duration,
};
use tokio::{
    fs,
    io::{AsyncReadExt, AsyncWriteExt},
    net::{TcpListener, UnixStream},
    sync::oneshot,
    time::timeout,
};
use tokio_util::sync::CancellationToken;

struct Domain {
    root: tempfile::TempDir,
    service: Arc<Service>,
    socket: std::path::PathBuf,
    stop: CancellationToken,
    server: tokio::task::JoinHandle<std::io::Result<()>>,
}

impl Domain {
    async fn start() -> Self {
        Self::at(Self::directory()).await
    }

    fn directory() -> tempfile::TempDir {
        tempfile::Builder::new()
            .permissions(std::fs::Permissions::from_mode(0o700))
            .tempdir()
            .unwrap()
    }

    async fn at(root: tempfile::TempDir) -> Self {
        Self::with_budget(root, None).await
    }

    async fn with_budget(root: tempfile::TempDir, budget: Option<u64>) -> Self {
        let mut config = Config::load(root.path(), &root.path().join("cache"), true).unwrap();
        if let Some(budget) = budget {
            config.cache_bytes = budget;
        }
        let service = Arc::new(Service::open(config).await.unwrap());
        let socket = root.path().join("music.sock");
        let listener =
            SocketListener::bind(&socket, fs::metadata(root.path()).await.unwrap().uid()).unwrap();
        let stop = CancellationToken::new();
        let server = tokio::spawn(serve_music(listener, service.clone(), stop.clone()));
        Self {
            root,
            service,
            socket,
            stop,
            server,
        }
    }

    async fn close(self) {
        drop(self.stop().await);
    }

    async fn stop(self) -> tempfile::TempDir {
        self.stop.cancel();
        self.server.await.unwrap().unwrap();
        self.service.close().await.unwrap();
        self.root
    }
}

#[tokio::test]
async fn one_service_owns_the_catalog_until_shutdown() {
    let domain = Domain::start().await;
    let root = domain.root.path();
    let config = Config::load(root, &root.join("cache"), true).unwrap();
    assert!(Service::open(config).await.is_err());
    Domain::at(domain.stop().await).await.close().await;
}

async fn send(path: &Path, profile: u8, op: Operation) -> UnixStream {
    let mut socket = UnixStream::connect(path).await.unwrap();
    socket.write_all(&MUSIC_PREFACE).await.unwrap();
    socket.write_u8(profile).await.unwrap();
    assert_eq!(socket.read_u8().await.unwrap(), profile);
    let bytes = MusicRequest {
        operation: Some(op),
    }
    .encode_to_vec();
    socket
        .write_u32(u32::try_from(bytes.len()).unwrap())
        .await
        .unwrap();
    socket.write_all(&bytes).await.unwrap();
    socket
}

async fn reply(socket: &mut UnixStream) -> Reply {
    let length = timeout(Duration::from_secs(12), socket.read_u32())
        .await
        .unwrap()
        .unwrap() as usize;
    assert!((1..=MUSIC_FRAME_BYTES).contains(&length));
    let mut bytes = vec![0; length];
    socket.read_exact(&mut bytes).await.unwrap();
    MusicReply::decode(bytes.as_slice())
        .unwrap()
        .result
        .unwrap()
}

async fn call(path: &Path, op: Operation) -> Reply {
    reply(&mut send(path, 3, op).await).await
}

fn open(id: &str, constraint: Option<Constraint>, readiness: Readiness) -> Operation {
    Operation::OpenMedia(MusicOpenMedia {
        track_id: id.into(),
        readiness: readiness.into(),
        constraint,
    })
}

async fn opened(path: &Path, op: Operation) -> (UnixStream, MusicMediaOpened) {
    let mut lease = send(path, 3, op).await;
    let Reply::MediaOpened(media) = reply(&mut lease).await else {
        panic!("expected media lease");
    };
    (lease, media)
}

async fn reader(path: &Path, opened: &MusicMediaOpened, seq: u64, offset: u64) -> UnixStream {
    let mut socket = send(
        path,
        3,
        Operation::ReadMedia(MusicReadMedia {
            lease_id: opened.lease_id.clone(),
            read_seq: seq,
            byte_offset: offset,
        }),
    )
    .await;
    assert!(matches!(reply(&mut socket).await, Reply::ReadReady(_)));
    socket
}

async fn block(reader: &mut UnixStream) -> Vec<u8> {
    let length = reader.read_u32().await.unwrap() as usize;
    let mut bytes = vec![0; length];
    reader.read_exact(&mut bytes).await.unwrap();
    bytes
}

async fn lyrics(path: &Path, op: LyricOperation) -> mik_protocol::music::LyricsResult {
    let Reply::Lyrics(result) = call(
        path,
        Operation::Lyrics(LyricsCommand {
            operation: Some(op),
        }),
    )
    .await
    else {
        panic!("expected lyrics reply");
    };
    result
}

async fn install(domain: &Domain, name: &str, script: impl AsRef<[u8]>) -> String {
    let imports = domain.root.path().join("music/sources/imports");
    let path = imports.join(name);
    fs::write(&path, script).await.unwrap();
    domain
        .service
        .import_source(url::Url::from_file_path(path).unwrap().as_str())
        .await
        .unwrap()
        .id
}

fn nbs() -> Vec<u8> {
    let mut bytes = vec![2, 0, 1, 0];
    bytes.extend_from_slice(&[0; 16]);
    bytes.extend_from_slice(&1000u16.to_le_bytes());
    bytes.extend_from_slice(&[0; 23 + 4]);
    bytes.extend_from_slice(&[1, 0, 1, 0, 0, 45, 0, 0, 0, 0]);
    bytes
}

#[tokio::test]
async fn browse_replace_retire_and_negotiate_note_events() {
    let domain = Domain::start().await;
    let input = domain.root.path().join("notes.nbs");
    fs::write(&input, nbs()).await.unwrap();
    let mut ids = Vec::new();
    for _ in 0..25 {
        ids.push(
            domain
                .service
                .import_local(input.clone(), None)
                .await
                .unwrap()
                .id,
        );
    }
    let Reply::List(first) = call(
        &domain.socket,
        Operation::List(MusicCatalogQuery::default()),
    )
    .await
    else {
        panic!("expected catalog page");
    };
    assert_eq!(first.tracks.len(), 24);
    assert!(!first.next_cursor.is_empty());
    let id = &ids[0];
    let command = open(id, None, Readiness::Complete);
    let mut audio_only = send(&domain.socket, 1, command.clone()).await;
    assert!(
        matches!(reply(&mut audio_only).await, Reply::Failure(error) if error.code() == Code::UnsupportedMedia)
    );
    let (_lease, media) = opened(&domain.socket, command.clone()).await;
    let descriptor = media.descriptor.as_ref().unwrap();
    assert_eq!(descriptor.representation(), MusicRepresentation::NoteEvents);
    let exact = Some(Constraint::ExactVersion(media.media_version.clone()));
    assert!(
        matches!(call(&domain.socket, open(&ids[1], exact.clone(), Readiness::Complete)).await,
        Reply::Failure(error) if error.code() == Code::VersionGone)
    );
    domain
        .service
        .import_local(input, Some(id.clone()))
        .await
        .unwrap();
    let (_replacement, replacement) = opened(&domain.socket, command.clone()).await;
    assert_ne!(replacement.media_version, media.media_version);
    assert!(
        matches!(call(&domain.socket, Operation::List(MusicCatalogQuery { cursor: first.next_cursor, ..Default::default() })).await,
        Reply::Failure(error) if error.code() == Code::CursorExpired)
    );
    assert!(matches!(
        call(
            &domain.socket,
            Operation::DecodeFailed(MusicDecodeFailure {
                track_id: id.clone(),
                media_version: media.media_version.clone()
            })
        )
        .await,
        Reply::Done(false)
    ));
    domain.service.retire_track(id.clone()).await.unwrap();
    assert!(
        matches!(call(&domain.socket, open(id, exact, Readiness::Readable)).await, Reply::Failure(error) if error.code() == Code::Retired)
    );
    let lost = domain.root.path().join(format!(
        "music/catalog-v2/library/{}.notes",
        replacement.media_version
    ));
    assert!(lost.is_file());
    let mut stream = reader(&domain.socket, &media, 1, 0).await;
    let score = NoteScore::decode(block(&mut stream).await.as_slice()).unwrap();
    assert_eq!(score.duration_us, 200_000);
    assert_eq!(score.events.len(), 1);
    assert!(block(&mut stream).await.is_empty());
    assert!(
        matches!(reply(&mut stream).await, Reply::ReadEnd(end) if matches!(end.outcome, Some(End::Eof(_))))
    );
    let missing = uuid::Uuid::new_v4().to_string();
    let Reply::Tracks(batch) = call(
        &domain.socket,
        Operation::Tracks(MusicTrackQuery {
            track_ids: vec![missing.clone(), id.clone(), ids[1].clone()],
        }),
    )
    .await
    else {
        panic!("expected ordered batch");
    };
    assert_eq!(batch.tracks[0].track_id, missing);
    assert_eq!(batch.tracks[0].state(), State::Unknown);
    assert_eq!(batch.tracks[1].state(), State::Retired);
    assert_eq!(batch.tracks[2].availability(), Availability::Ready);
    domain.close().await;
}

#[tokio::test]
async fn search_keeps_identity_across_pages_and_download_leases_rewind() {
    let domain = Domain::start().await;
    let http = TcpListener::bind("127.0.0.1:0").await.unwrap();
    let address = http.local_addr().unwrap();
    let (send_tail, wait_tail) = oneshot::channel();
    let upstream = tokio::spawn(async move {
        let (mut socket, _) = http.accept().await.unwrap();
        let mut request = vec![0; 4096];
        assert!(socket.read(&mut request).await.unwrap() > 0);
        socket
            .write_all(
                b"HTTP/1.1 200 OK\r\nContent-Type: audio/wav\r\nContent-Length: 8\r\n\r\n0123",
            )
            .await
            .unwrap();
        wait_tail.await.unwrap();
        socket.write_all(b"4567").await.unwrap();
    });
    let script = format!(
        r"
        const {{ on, send, EVENT_NAMES }} = lx;
        on(EVENT_NAMES.request, ({{ action, info }}) => {{
            if (action === 'musicSearch') return Array.from({{ length: 28 }}, (_, i) => ({{name: 'Example ' + i, songmid: 'demo-' + i}}));
            if (action === 'lyric') return {{ lyric: '[00:01]Still text', tlyric: '[00:02]独立' }};
            if (!info.musicInfo.meta.songId.startsWith('demo-')) throw new Error('missing resolver input');
            return 'http://{address}/audio';
        }});
        send(EVENT_NAMES.inited, {{ status: true, sources: {{ demo: {{ type: 'music', actions: ['musicUrl', 'musicSearch', 'lyric'], qualitys: ['128k'] }} }} }});
    "
    );
    let imports = domain.root.path().join("music/sources/imports");
    fs::create_dir_all(&imports).await.unwrap();
    let script_path = imports.join("demo.js");
    fs::write(&script_path, script).await.unwrap();
    domain
        .service
        .import_source(url::Url::from_file_path(script_path).unwrap().as_str())
        .await
        .unwrap();
    let search = Operation::List(MusicCatalogQuery {
        query: "Example".into(),
        ..Default::default()
    });
    let Reply::List(first) = call(&domain.socket, search.clone()).await else {
        panic!("expected discovery results");
    };
    let Reply::List(again) = call(&domain.socket, search).await else {
        panic!("expected discovery results");
    };
    let ids = |page: &mik_protocol::music::MusicCatalogPage| {
        page.tracks
            .iter()
            .map(|t| t.track_id.clone())
            .collect::<std::collections::BTreeSet<_>>()
    };
    // Re-ranking may place a new candidate after known IDs, but one stable key always keeps one ID.
    assert_eq!(ids(&first), ids(&again));
    assert_eq!(first.tracks.len(), 24);
    let Reply::List(next) = call(
        &domain.socket,
        Operation::List(MusicCatalogQuery {
            query: "Example".into(),
            cursor: first.next_cursor.clone(),
            ..Default::default()
        }),
    )
    .await
    else {
        panic!("expected continuation");
    };
    assert_eq!(next.tracks.len(), 4);
    assert!(ids(&first).is_disjoint(&ids(&next)));
    let id = &first.tracks[0].track_id;
    let (_lease, media) = opened(&domain.socket, open(id, None, Readiness::Readable)).await;
    let mut first = reader(&domain.socket, &media, 1, 0).await;
    assert_eq!(block(&mut first).await, b"0123");
    let mut moved = reader(&domain.socket, &media, 2, 4).await;
    assert!(first.read_u32().await.is_err());
    send_tail.send(()).unwrap();
    assert_eq!(block(&mut moved).await, b"4567");
    assert_script_lyrics_are_plain(&domain.socket, media.media_version).await;
    upstream.await.unwrap();
    domain.close().await;
}

async fn assert_script_lyrics_are_plain(socket: &Path, media_version: String) {
    let lookup = LyricOperation::Lookup(LyricsLookup { media_version });
    assert_eq!(
        lyrics(socket, lookup.clone()).await.state(),
        LyricsState::Pending
    );
    let state = timeout(Duration::from_secs(5), async {
        loop {
            let state = lyrics(socket, lookup.clone()).await;
            if state.state() != LyricsState::Pending {
                break state;
            }
            tokio::time::sleep(Duration::from_millis(20)).await;
        }
    })
    .await
    .expect("remote lyrics should settle");
    assert_eq!(state.state(), LyricsState::Ready);
    let mik_protocol::music::lyrics_result::Content::Document(doc) = state.content.unwrap() else {
        panic!("expected header");
    };
    assert_eq!(doc.tracks.len(), 2);
    assert!(doc.tracks.iter().all(|track| !track.timed));
    let state = lyrics(
        socket,
        LyricOperation::Read(LyricsRead {
            ticket: state.ticket,
            track_key: doc.tracks[0].track_key,
            offset: 0,
        }),
    )
    .await;
    assert!(
        matches!(state.content, Some(mik_protocol::music::lyrics_result::Content::Page(page)) if page.lines == ["Still text"] && page.cues.is_empty())
    );
}

#[tokio::test]
async fn incomplete_discovery_keeps_fast_channels_and_their_stable_id() {
    let domain = Domain::start().await;
    let imports = domain.root.path().join("music/sources/imports");
    fs::create_dir_all(&imports).await.unwrap();
    for (channel, search) in [
        ("quick", "[{name: 'Example', songmid: 'stable'}]"),
        ("stall", "new Promise(() => {})"),
    ] {
        let script = format!(
            r"
            const {{on, send, EVENT_NAMES}} = lx;
            on(EVENT_NAMES.request, () => {search});
            send(EVENT_NAMES.inited, {{status: true, sources: {{ {channel}: {{type: 'music', actions: ['musicSearch']}} }} }});
        "
        );
        let path = imports.join(format!("{channel}.js"));
        fs::write(&path, script).await.unwrap();
        domain
            .service
            .import_source(url::Url::from_file_path(path).unwrap().as_str())
            .await
            .unwrap();
    }
    let query = Operation::List(MusicCatalogQuery {
        query: "Example".into(),
        ..Default::default()
    });
    let Reply::List(result) = timeout(Duration::from_secs(9), call(&domain.socket, query))
        .await
        .unwrap()
    else {
        panic!("expected partial list");
    };
    assert!(result.partial);
    assert_eq!(result.tracks.len(), 1);
    let row = &result.tracks[0];
    assert_eq!(row.title, "Example");
    assert!(
        matches!(call(&domain.socket, Operation::Tracks(MusicTrackQuery { track_ids: vec![row.track_id.clone()] })).await,
        Reply::Tracks(result) if result.tracks[0].track_id == row.track_id && result.tracks[0].state() == State::Active)
    );
    domain.close().await;
}

#[tokio::test]
async fn script_collection_and_concurrent_discovery_share_persisted_keys_not_titles() {
    let domain = Domain::start().await;
    let discovery = install(&domain, "discover.js", r"
        const { on, send, EVENT_NAMES } = lx;
        const song = id => ({ name: 'Same title', songmid: id });
        on(EVENT_NAMES.request, ({ action, info }) => {
            if (action === 'musicSearch') return [song('first'), song('second')];
            if (action !== 'musicCollection' || info.key !== 'example' || Object.keys(info).length !== 1) throw new Error('bad key');
            return { name: 'Fixture list', list: [song('second'), song('first'), song('first')] };
        });
        send(EVENT_NAMES.inited, {status: true, sources: { demo: {
            type: 'music', actions: ['musicSearch', 'musicCollection'],
            collectionPrefix: 'https://fixture.invalid/playlists/',
        } }});
    ").await;
    let search = Operation::List(MusicCatalogQuery {
        query: "Same title".into(),
        ..Default::default()
    });
    let collection = |reference: &str| {
        Operation::Collection(MusicCollectionQuery {
            reference: reference.into(),
            cursor: Vec::new(),
        })
    };
    let (first, second) = tokio::join!(
        call(&domain.socket, search.clone()),
        call(&domain.socket, search)
    );
    let imported = call(
        &domain.socket,
        collection("https://fixture.invalid/playlists/example"),
    )
    .await;
    let (Reply::List(first), Reply::List(second), Reply::Collection(imported)) =
        (first, second, imported)
    else {
        panic!("expected two searches and a supported collection");
    };
    let imported_page = imported.page.unwrap();
    assert_eq!(imported.title, "Fixture list");
    assert!(!imported_page.partial);
    let mut ordered = imported_page
        .tracks
        .into_iter()
        .map(|row| row.track_id)
        .collect::<Vec<_>>();
    assert_eq!(ordered.len(), 2);
    assert_ne!(ordered[0], ordered[1]);
    let keys = ["second", "first"].map(|value| super::TrackRef::new("demo", value.into()).unwrap());
    let from_keys = domain
        .service
        .identities
        .by_keys(keys.to_vec())
        .await
        .unwrap()
        .into_iter()
        .map(|row| row.id.to_string())
        .collect::<Vec<_>>();
    assert_eq!(ordered, from_keys);
    ordered.sort();
    for page in [first, second] {
        let mut ids = page
            .tracks
            .into_iter()
            .map(|row| row.track_id)
            .collect::<Vec<_>>();
        ids.sort();
        assert_eq!(ids, ordered);
    }
    for unsupported in [
        "https://fixture.invalid/other/example",
        "https://fixture.invalid/playlists/example?token=secret",
        "https://another.invalid/playlists/example",
        "https://fixture.invalid/playlists/one/two",
    ] {
        assert!(
            matches!(call(&domain.socket, collection(unsupported)).await,
            Reply::Failure(error) if error.code() == Code::InvalidRequest)
        );
    }
    domain.service.remove_source(&discovery).await.unwrap();
    let reopened = Domain::at(domain.stop().await).await;
    assert!(
        matches!(call(&reopened.socket, Operation::Tracks(MusicTrackQuery { track_ids: ordered.clone() })).await,
        Reply::Tracks(batch) if batch.tracks.iter().zip(ordered).all(|(row, id)| row.track_id == id && row.state() == State::Active))
    );
    reopened.close().await;
}

#[tokio::test]
async fn lyric_tickets_pin_tracks_and_replacing_media_does_not_inherit_corrections() {
    let domain = Domain::start().await;
    let input = domain.root.path().join("notes.nbs");
    fs::write(&input, nbs()).await.unwrap();
    let id = domain
        .service
        .import_local(input.clone(), None)
        .await
        .unwrap()
        .id;
    let (lease, media) = opened(&domain.socket, open(&id, None, Readiness::Complete)).await;
    let inspected = domain.service.inspect_track(&id).await.unwrap();
    let version_from_cli = inspected["media"]["version"].as_str().unwrap();
    assert_eq!(version_from_cli, media.media_version);
    let lookup = |version: &str| {
        LyricOperation::Lookup(LyricsLookup {
            media_version: version.into(),
        })
    };
    let path = domain.root.path().join("correction.json");
    fs::write(&path, r#"[
        {"role":"original","language_tag":"JA","kind":"timed","cues":[{"start_ms":10,"end_ms":100,"text":"first"}]},
        {"role":"translation","language_tag":"en-US","kind":"text","lines":["translation"]}
    ]"#).await.unwrap();
    domain
        .service
        .edit_lyrics(version_from_cli, Some(path))
        .await
        .unwrap();
    let first = lyrics(&domain.socket, lookup(&media.media_version)).await;
    let Some(mik_protocol::music::lyrics_result::Content::Document(document)) = first.content
    else {
        panic!("expected document");
    };
    assert_eq!(document.tracks[0].language_tag.as_deref(), Some("ja"));
    assert_eq!(document.tracks[1].language_tag.as_deref(), Some("en-US"));
    let later = domain.root.path().join("correction.lrc");
    fs::write(&later, "[00:00.010]updated\n[00:00.050]\n")
        .await
        .unwrap();
    domain
        .service
        .edit_lyrics(&media.media_version, Some(later))
        .await
        .unwrap();
    let next = lyrics(&domain.socket, lookup(&media.media_version)).await;
    let Some(mik_protocol::music::lyrics_result::Content::Document(updated)) = next.content else {
        panic!("expected updated document");
    };
    assert_ne!(document.revision, updated.revision);
    for (ticket, text, end) in [(first.ticket, "first", 100), (next.ticket, "updated", 50)] {
        let page = lyrics(
            &domain.socket,
            LyricOperation::Read(LyricsRead {
                ticket,
                track_key: 1,
                offset: 0,
            }),
        )
        .await;
        assert!(
            matches!(page.content, Some(mik_protocol::music::lyrics_result::Content::Page(page))
            if page.cues[0].text == text && page.cues[0].end_ms == Some(end))
        );
    }
    domain
        .service
        .import_local(input, Some(id.clone()))
        .await
        .unwrap();
    let (_replacement, replaced) =
        opened(&domain.socket, open(&id, None, Readiness::Complete)).await;
    assert_ne!(media.media_version, replaced.media_version);
    assert_eq!(
        lyrics(&domain.socket, lookup(&replaced.media_version))
            .await
            .state(),
        LyricsState::Empty
    );
    // Removal outside mikd (or a storage failure) must not grant a lease for the
    // current replacement when the caller requested the missing historical version.
    drop(lease);
    fs::remove_file(domain.root.path().join(format!(
        "music/catalog-v2/library/{}.notes",
        media.media_version
    )))
    .await
    .unwrap();
    assert!(
        matches!(call(&domain.socket, open(&id, Some(Constraint::ExactVersion(media.media_version.clone())), Readiness::Complete)).await,
        Reply::Failure(error) if error.code() == Code::VersionGone)
    );
    assert!(
        matches!(call(&domain.socket, open(&id, None, Readiness::Complete)).await,
        Reply::MediaOpened(current) if current.media_version == replaced.media_version)
    );
    domain.close().await;
}

async fn fallback_fixture() -> (Domain, tokio::task::JoinHandle<()>) {
    let root = Domain::directory();
    let http = TcpListener::bind("127.0.0.1:0").await.unwrap();
    let address = http.local_addr().unwrap();
    let upstream = tokio::spawn(async move {
        loop {
            let (mut socket, _) = http.accept().await.unwrap();
            let mut bytes = [0; 4096];
            let read = socket.read(&mut bytes).await.unwrap();
            let request = String::from_utf8_lossy(&bytes[..read]);
            let body = if request.starts_with("GET /primary/first ") {
                "primary!"
            } else if request.starts_with("GET /alternate/first ") {
                "fallback"
            } else if request.starts_with("GET /primary/second ") {
                "catalog pressure"
            } else {
                panic!("unexpected test media request");
            };
            let response = format!(
                "HTTP/1.1 200 OK\r\nContent-Type: audio/wav\r\nConnection: close\r\nContent-Length: {}\r\n\r\n{body}",
                body.len()
            );
            socket.write_all(response.as_bytes()).await.unwrap();
        }
    });
    let scripts = root.path().join("music/sources/local");
    fs::create_dir_all(&scripts).await.unwrap();
    for (name, route, search) in [
        ("a.js", "primary", ", 'musicSearch'"),
        ("b.js", "alternate", ""),
    ] {
        fs::write(
            scripts.join(name),
            format!(
                r"
                const {{ on, send, EVENT_NAMES }} = lx;
                on(EVENT_NAMES.request, ({{ action, info }}) => {{
                    if (action === 'musicSearch') return [
                        {{ name: 'Fixture first', songmid: 'first' }},
                        {{ name: 'Fixture second', songmid: 'second' }}
                    ];
                    return 'http://{address}/{route}/' + info.musicInfo.meta.songId;
                }});
                send(EVENT_NAMES.inited, {{ status: true, sources: {{ demo: {{
                    type: 'music', actions: ['musicUrl'{search}], qualitys: ['128k']
                }} }} }});
                "
            ),
        )
        .await
        .unwrap();
    }
    (Domain::with_budget(root, Some(16)).await, upstream)
}

#[tokio::test]
async fn download_retry_requires_a_report_then_cache_eviction_never_rebinds_a_version() {
    let (domain, upstream) = fallback_fixture().await;
    let Reply::List(page) = call(
        &domain.socket,
        Operation::List(MusicCatalogQuery {
            query: "Fixture".into(),
            ..Default::default()
        }),
    )
    .await
    else {
        panic!("expected download fixtures in the catalog");
    };
    let id = |title: &str| {
        page.tracks
            .iter()
            .find(|track| track.title == title)
            .unwrap()
            .track_id
            .as_str()
    };
    let first = id("Fixture first");
    let second = id("Fixture second");
    let (primary, initial) = opened(&domain.socket, open(first, None, Readiness::Complete)).await;
    let previous = initial.media_version.clone();
    let retry = open(
        first,
        Some(Constraint::AvoidVersion(previous.clone())),
        Readiness::Complete,
    );
    assert!(matches!(call(&domain.socket, retry.clone()).await,
        Reply::Failure(error) if error.code() == Code::VersionGone));
    assert!(matches!(
        call(
            &domain.socket,
            Operation::DecodeFailed(MusicDecodeFailure {
                track_id: first.into(),
                media_version: previous.clone(),
            })
        )
        .await,
        Reply::Done(true)
    ));

    let (alternate, current) = opened(&domain.socket, retry).await;
    assert_ne!(current.media_version, previous);
    let inspect = domain.service.inspect_track(first).await.unwrap();
    assert_eq!(inspect["media"]["source"], "local:b.js");
    assert_eq!(inspect["media"]["version"], current.media_version);
    for (media, expected) in [(&initial, b"primary!"), (&current, b"fallback")] {
        let mut stream = reader(&domain.socket, media, 1, 0).await;
        assert_eq!(block(&mut stream).await, expected);
        assert!(block(&mut stream).await.is_empty());
        assert!(
            matches!(reply(&mut stream).await, Reply::ReadEnd(end) if matches!(end.outcome, Some(End::Eof(_))))
        );
    }
    assert!(
        matches!(call(&domain.socket, open(second, None, Readiness::Complete)).await,
        Reply::Failure(error) if error.code() == Code::Busy)
    );
    drop((primary, alternate));
    timeout(Duration::from_secs(5), async {
        loop {
            match call(&domain.socket, open(second, None, Readiness::Complete)).await {
                Reply::MediaOpened(_) => break,
                Reply::Failure(error) if error.code() == Code::Busy => {}
                _ => panic!("unexpected result opening media under cache pressure"),
            }
            tokio::task::yield_now().await;
        }
    })
    .await
    .unwrap();
    for version in [previous, current.media_version] {
        assert!(
            matches!(call(&domain.socket, open(first, Some(Constraint::ExactVersion(version)), Readiness::Complete)).await,
            Reply::Failure(error) if error.code() == Code::VersionGone)
        );
    }
    domain.close().await;
    upstream.abort();
    assert!(upstream.await.unwrap_err().is_cancelled());
}

#[tokio::test]
async fn wrong_version_and_oversized_frames_close_only_the_music_connection() {
    let domain = Domain::start().await;
    let mut old = UnixStream::connect(&domain.socket).await.unwrap();
    old.write_all(b"MIKM\0\x01\x03").await.unwrap();
    assert!(old.read_u8().await.is_err());
    let mut large = UnixStream::connect(&domain.socket).await.unwrap();
    large.write_all(&MUSIC_PREFACE).await.unwrap();
    large.write_u8(3).await.unwrap();
    assert_eq!(large.read_u8().await.unwrap(), 3);
    large
        .write_u32(u32::try_from(MUSIC_FRAME_BYTES).unwrap() + 1)
        .await
        .unwrap();
    assert!(large.read_u8().await.is_err());
    assert!(
        matches!(call(&domain.socket, Operation::List(MusicCatalogQuery::default())).await, Reply::List(page) if page.tracks.is_empty())
    );
    domain.close().await;
}

#[tokio::test]
async fn profile_negotiates_the_known_output_versions() {
    let domain = Domain::start().await;
    let mut partial = UnixStream::connect(&domain.socket).await.unwrap();
    partial.write_all(&MUSIC_PREFACE).await.unwrap();
    partial.write_u8(MUSIC_NOTE_EVENTS_V1 | 0x80).await.unwrap();
    assert_eq!(partial.read_u8().await.unwrap(), MUSIC_NOTE_EVENTS_V1);

    let mut unsupported = UnixStream::connect(&domain.socket).await.unwrap();
    unsupported.write_all(&MUSIC_PREFACE).await.unwrap();
    unsupported.write_u8(0x80).await.unwrap();
    assert!(unsupported.read_u8().await.is_err());
    domain.close().await;
}

#[tokio::test]
async fn lease_quota_releases_on_owner_disconnect_and_never_blocks_directory_reads() {
    let domain = Domain::start().await;
    let input = domain.root.path().join("notes.nbs");
    fs::write(&input, nbs()).await.unwrap();
    let id = domain.service.import_local(input, None).await.unwrap().id;
    let op = open(&id, None, Readiness::Complete);
    let (lease, media) = opened(&domain.socket, op.clone()).await;
    let mut other_leases = Vec::new();
    let rejected = loop {
        let mut candidate = send(&domain.socket, 3, op.clone()).await;
        match reply(&mut candidate).await {
            Reply::MediaOpened(_) => other_leases.push(candidate),
            Reply::Failure(error) => break error.code(),
            _ => panic!("unexpected reply for lease open"),
        }
        assert!(
            other_leases.len() < 64,
            "a bounded lease lane must reject excess opens"
        );
    };
    assert_eq!(rejected, Code::Busy);
    assert!(
        matches!(call(&domain.socket, Operation::List(MusicCatalogQuery::default())).await,
        Reply::List(page) if page.tracks.len() == 1)
    );
    let read = Operation::ReadMedia(MusicReadMedia {
        lease_id: media.lease_id,
        read_seq: 1,
        byte_offset: 0,
    });
    let mut wrong_profile = send(&domain.socket, 1, read.clone()).await;
    assert!(matches!(reply(&mut wrong_profile).await,
        Reply::Failure(error) if error.code() == Code::NotFound));

    drop(lease);
    let reissued = timeout(Duration::from_secs(5), async {
        loop {
            let mut candidate = send(&domain.socket, 3, op.clone()).await;
            match reply(&mut candidate).await {
                Reply::MediaOpened(_) => break candidate,
                Reply::Failure(error) if error.code() == Code::Busy => {
                    tokio::task::yield_now().await;
                }
                _ => panic!("unexpected reply after disconnect"),
            }
        }
    })
    .await
    .unwrap();
    assert!(matches!(call(&domain.socket, read).await,
        Reply::Failure(error) if error.code() == Code::NotFound));
    drop((reissued, other_leases));

    // Socket file access alone is insufficient when the peer UID is different.
    let wrong_uid = fs::metadata(domain.root.path())
        .await
        .unwrap()
        .uid()
        .wrapping_add(1);
    let denied_path = domain.root.path().join("other-paper.sock");
    let listener = SocketListener::bind(&denied_path, wrong_uid).unwrap();
    let stop = CancellationToken::new();
    let denied_server = tokio::spawn(serve_music(listener, domain.service.clone(), stop.clone()));
    let mut denied = UnixStream::connect(denied_path).await.unwrap();
    assert!(
        timeout(Duration::from_secs(5), denied.read_u8())
            .await
            .unwrap()
            .is_err()
    );
    stop.cancel();
    denied_server.await.unwrap().unwrap();
    domain.close().await;
}
