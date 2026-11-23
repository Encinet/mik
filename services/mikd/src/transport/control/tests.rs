use super::{PaperServices, SocketListener, WORKFLOWS, serve};
use crate::{
    domains::{access::Access, ai::AssistantSlot},
    game::GameBridge,
};
use mik_protocol::{
    CAPABILITIES, MAX_FRAME_BYTES, VERSION,
    wire::{
        self, ChatGameRoster, ConnectionRole, Envelope, GameRoster, GameStatusView, Hello,
        PaperStateUpdate, PlayerSnapshot, Request, StatusRequest,
        envelope::Body,
        request::Operation,
        response::{Code, Result as ResponseResult},
    },
};
use prost::Message;
use std::{
    fs, io,
    os::unix::fs::{MetadataExt, PermissionsExt},
    sync::Arc,
    time::Duration,
};
use tempfile::TempDir;
use tokio::{
    io::{AsyncReadExt, AsyncWriteExt},
    net::UnixStream,
    task::JoinHandle,
    time::timeout,
};
use tokio_util::sync::CancellationToken;
use uuid::Uuid;

struct Running {
    directory: TempDir,
    stop: CancellationToken,
    task: JoinHandle<io::Result<()>>,
}

impl Running {
    fn start(uid: Option<u32>) -> Self {
        let directory = private_directory();
        let uid = uid.unwrap_or_else(|| fs::metadata(directory.path()).unwrap().uid());
        let listener = SocketListener::bind(&directory.path().join("mikd.sock"), uid).unwrap();
        let game = Arc::new(GameBridge::new());
        let access = Arc::new(Access::new(game.clone(), None));
        let services = PaperServices {
            game,
            access,
            chat: None,
            web_chat: None,
            assistant: AssistantSlot::default(),
            announcements: None,
            identity: None,
            governance: None,
            web_api_port: Arc::default(),
            web_chat_port: Arc::default(),
            qq_moderation: false,
        };
        let stop = CancellationToken::new();
        let task = tokio::spawn(serve(listener, services, stop.clone()));
        Self {
            directory,
            stop,
            task,
        }
    }

    async fn connect(&self) -> UnixStream {
        UnixStream::connect(self.directory.path().join("mikd.sock"))
            .await
            .unwrap()
    }

    async fn close(self) {
        self.stop.cancel();
        timeout(Duration::from_secs(1), self.task)
            .await
            .unwrap()
            .unwrap()
            .unwrap();
        assert!(!self.directory.path().join("mikd.sock").exists());
    }
}

fn hello(role: ConnectionRole) -> Envelope {
    Envelope {
        request_id: 0,
        session_id: String::new(),
        body: Some(Body::Hello(Hello {
            protocol_version: VERSION,
            role: role.into(),
            game_instance: "test".into(),
            startup_id: Uuid::new_v4().to_string(),
            connection_id: Uuid::new_v4().to_string(),
            capabilities: CAPABILITIES
                .iter()
                .chain(WORKFLOWS)
                .map(|v| (*v).into())
                .collect(),
        })),
    }
}

async fn send(stream: &mut UnixStream, message: &Envelope) {
    let payload = message.encode_to_vec();
    stream
        .write_u32(u32::try_from(payload.len()).unwrap())
        .await
        .unwrap();
    stream.write_all(&payload).await.unwrap();
}

async fn receive(stream: &mut UnixStream) -> Envelope {
    let length = timeout(Duration::from_secs(2), stream.read_u32())
        .await
        .unwrap()
        .unwrap();
    assert!(length <= u32::try_from(MAX_FRAME_BYTES).unwrap());
    let mut payload = vec![0; length as usize];
    stream.read_exact(&mut payload).await.unwrap();
    Envelope::decode(payload.as_slice()).unwrap()
}

async fn handshake(server: &Running, role: ConnectionRole) -> (UnixStream, String) {
    let mut stream = server.connect().await;
    send(&mut stream, &hello(role)).await;
    let welcome = receive(&mut stream).await;
    assert!(matches!(welcome.body, Some(Body::Welcome(_))));
    (stream, welcome.session_id)
}

fn request(session: &str, id: u64, operation: Operation) -> Envelope {
    Envelope {
        request_id: id,
        session_id: session.into(),
        body: Some(Body::Request(Request {
            timeout_ms: 2000,
            operation: Some(operation),
        })),
    }
}

async fn expect_code(stream: &mut UnixStream, request: &Envelope, expected: Code) {
    send(stream, request).await;
    let Some(Body::Response(response)) = receive(stream).await.body else {
        panic!("expected a response");
    };
    assert_eq!(response.code, i32::from(expected), "expected {expected:?}");
}

async fn closed(stream: &mut UnixStream) {
    let mut byte = [0];
    let outcome = timeout(Duration::from_secs(2), stream.read(&mut byte))
        .await
        .unwrap();
    assert!(
        matches!(outcome, Ok(0) | Err(_)),
        "connection must fail closed"
    );
}

#[tokio::test]
async fn old_paper_can_read_status_but_cannot_enter_the_new_turn_workflow() {
    let server = Running::start(None);
    let mut stream = server.connect().await;
    let mut envelope = hello(ConnectionRole::General);
    let Some(Body::Hello(hello)) = &mut envelope.body else {
        unreachable!()
    };
    hello.capabilities.truncate(CAPABILITIES.len());
    hello.capabilities.push("assistant-turns.v8".into());
    send(&mut stream, &envelope).await;
    let welcome = receive(&mut stream).await;
    assert!(matches!(&welcome.body, Some(Body::Welcome(ack)) if ack.protocol_version == 1));
    let session = welcome.session_id;
    expect_code(
        &mut stream,
        &request(
            &session,
            1,
            Operation::Assistant(wire::AssistantCommand::default()),
        ),
        Code::Unsupported,
    )
    .await;
    expect_code(
        &mut stream,
        &request(&session, 2, Operation::Status(StatusRequest {})),
        Code::Ok,
    )
    .await;
    server.close().await;
}

#[tokio::test]
async fn paper_state_retains_omitted_rosters_only_on_the_same_connection() {
    let server = Running::start(None);
    let (mut stream, session) = handshake(&server, ConnectionRole::PaperState).await;
    let (mut general, general_session) = handshake(&server, ConnectionRole::General).await;
    let snapshot = PaperStateUpdate {
        version: 7,
        server: Some(GameStatusView::default()),
        public_roster: Some(GameRoster {
            players: vec![PlayerSnapshot {
                player_id: Uuid::new_v4().to_string(),
                name: "测试".into(),
                ..Default::default()
            }],
        }),
        chat_roster: Some(ChatGameRoster::default()),
        ..Default::default()
    };
    let metrics_only = PaperStateUpdate {
        version: 8,
        server: Some(GameStatusView::default()),
        ..Default::default()
    };
    expect_code(
        &mut stream,
        &request(&session, 1, Operation::PaperState(snapshot.clone())),
        Code::Ok,
    )
    .await;
    expect_code(
        &mut stream,
        &request(&session, 2, Operation::PaperState(snapshot.clone())),
        Code::StaleVersion,
    )
    .await;
    send(
        &mut stream,
        &request(&session, 3, Operation::PaperState(metrics_only.clone())),
    )
    .await;
    assert!(matches!(
        receive(&mut stream).await.body,
        Some(Body::Response(wire::Response {
            result: Some(ResponseResult::PaperState(wire::PaperStateAccepted {
                version: 8,
                ..
            })),
            ..
        }))
    ));
    send(
        &mut general,
        &request(&general_session, 1, Operation::Status(StatusRequest {})),
    )
    .await;
    assert!(
        matches!(receive(&mut general).await.body, Some(Body::Response(wire::Response {
        result: Some(ResponseResult::Status(wire::Status { snapshot_version: 8, player_count: 1, ready_domains, .. })), ..
    })) if ready_domains.is_empty())
    );
    stream.shutdown().await.unwrap();
    closed(&mut stream).await;
    send(
        &mut general,
        &request(&general_session, 2, Operation::Status(StatusRequest {})),
    )
    .await;
    assert!(matches!(
        receive(&mut general).await.body,
        Some(Body::Response(wire::Response {
            result: Some(ResponseResult::Status(wire::Status {
                snapshot_version: 0,
                player_count: 0,
                ..
            })),
            ..
        }))
    ));
    let (mut stream, new_session) = handshake(&server, ConnectionRole::PaperState).await;
    assert_ne!(new_session, session);
    expect_code(
        &mut stream,
        &request(&new_session, 1, Operation::PaperState(metrics_only)),
        Code::InvalidRequest,
    )
    .await;
    expect_code(
        &mut stream,
        &request(&new_session, 2, Operation::PaperState(snapshot.clone())),
        Code::Ok,
    )
    .await;
    send(
        &mut stream,
        &request(&session, 3, Operation::PaperState(snapshot)),
    )
    .await;
    closed(&mut stream).await;
    server.close().await;
}

#[tokio::test]
async fn fragmented_and_unknown_fields_are_compatible() {
    let server = Running::start(None);
    let mut stream = server.connect().await;
    let mut payload = hello(ConnectionRole::Control).encode_to_vec();
    payload.extend_from_slice(&[0xf8, 0x07, 0x01]); // unknown field 127
    let mut frame = u32::try_from(payload.len()).unwrap().to_be_bytes().to_vec();
    frame.extend_from_slice(&payload);
    for chunk in frame.chunks(3) {
        stream.write_all(chunk).await.unwrap();
        tokio::task::yield_now().await;
    }
    let session = receive(&mut stream).await.session_id;
    let status = request(&session, 1, Operation::Status(StatusRequest {}));
    send(&mut stream, &status).await;
    assert_eq!(receive(&mut stream).await.request_id, 1);
    // Reused/out-of-order request IDs cannot produce a second result.
    send(&mut stream, &status).await;
    closed(&mut stream).await;
    server.close().await;
}

#[tokio::test]
async fn invalid_lengths_and_protocol_versions_fail_closed() {
    let server = Running::start(None);
    for length in [0, u32::try_from(MAX_FRAME_BYTES).unwrap() + 1, u32::MAX] {
        let mut stream = server.connect().await;
        stream.write_u32(length).await.unwrap();
        closed(&mut stream).await;
    }
    for version in [0, 2] {
        let mut stream = server.connect().await;
        let mut message = hello(ConnectionRole::General);
        if let Some(Body::Hello(hello)) = &mut message.body {
            hello.protocol_version = version;
        }
        send(&mut stream, &message).await;
        closed(&mut stream).await;
    }
    server.close().await;
}

#[tokio::test]
async fn peer_uid_and_role_quotas_are_enforced_independently() {
    let denied = Running::start(Some(u32::MAX));
    closed(&mut denied.connect().await).await;
    denied.close().await;
    let server = Running::start(None);
    let (_one, _) = handshake(&server, ConnectionRole::General).await;
    let (_two, _) = handshake(&server, ConnectionRole::General).await;
    let mut rejected = server.connect().await;
    send(&mut rejected, &hello(ConnectionRole::General)).await;
    closed(&mut rejected).await;
    let (mut control, session) = handshake(&server, ConnectionRole::Control).await;
    expect_code(
        &mut control,
        &request(
            &session,
            1,
            Operation::PaperState(PaperStateUpdate {
                version: 1,
                ..Default::default()
            }),
        ),
        Code::Unsupported,
    )
    .await;
    server.close().await;
    closed(&mut control).await;
}

#[tokio::test]
async fn shutdown_interrupts_incomplete_frames_and_owns_only_its_socket() {
    let server = Running::start(None);
    let mut stream = server.connect().await;
    stream.write_all(&[0, 0]).await.unwrap();
    server.close().await;
    closed(&mut stream).await;
}

#[tokio::test]
async fn socket_lock_permissions_and_stale_recovery() {
    let directory = private_directory();
    let path = directory.path().join("mikd.sock");
    let uid = fs::metadata(directory.path()).unwrap().uid();
    let old = std::os::unix::net::UnixListener::bind(&path).unwrap();
    drop(old);
    let server = SocketListener::bind(&path, uid).unwrap();
    assert_eq!(
        fs::metadata(&path).unwrap().permissions().mode() & 0o777,
        0o660
    );
    assert!(SocketListener::bind(&path, uid).is_err());
    drop(server);
    assert!(!path.exists());
    fs::write(&path, b"do not delete").unwrap();
    assert!(SocketListener::bind(&path, uid).is_err());
    assert_eq!(fs::read(&path).unwrap(), b"do not delete");
    fs::set_permissions(directory.path(), fs::Permissions::from_mode(0o777)).unwrap();
    assert!(SocketListener::bind(&path, uid).is_err());
}

fn private_directory() -> TempDir {
    tempfile::Builder::new()
        .permissions(fs::Permissions::from_mode(0o700))
        .tempdir()
        .unwrap()
}
