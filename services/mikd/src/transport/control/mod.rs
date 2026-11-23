mod dispatch;
mod state;

#[cfg(test)]
mod tests;

use super::socket::{SocketListener, invalid};
use dispatch::Dispatcher;
pub(crate) use dispatch::PaperServices;
use mik_protocol::{
    CAPABILITIES, MAX_FRAME_BYTES, VERSION, frame_length,
    wire::{
        ConnectionRole, Envelope, Hello, Request, Response, Welcome, chat_command, envelope::Body,
        request::Operation, response::Code,
    },
};
use prost::Message;
use std::{
    collections::HashMap,
    io,
    sync::{
        Arc,
        atomic::{AtomicBool, Ordering},
    },
    time::Duration,
};
use tokio::{
    io::{AsyncReadExt, AsyncWriteExt},
    net::UnixStream,
    sync::Semaphore,
    task::JoinSet,
    time::timeout,
};
use tokio_util::sync::CancellationToken;
use uuid::Uuid;

const IO_TIMEOUT: Duration = Duration::from_secs(5);
const HANDSHAKE_TIMEOUT: Duration = Duration::from_secs(1);

// These contracts have different business semantics even though the frame and envelope are v1.
const ASSISTANT_TURNS: &str = "assistant-turns.v9";
const CHAT_RECEIPTS: &str = "chat-receipts.v1";
const LOGIN_RECEIPT: &str = "web-login-receipt.v1";
const GOVERNANCE_DELIVERY: &str = "governance-delivery.v1";
const WORKFLOWS: &[&str] = &[
    ASSISTANT_TURNS,
    CHAT_RECEIPTS,
    LOGIN_RECEIPT,
    GOVERNANCE_DELIVERY,
];

fn required_workflow(request: &Request) -> Option<&'static str> {
    match request.operation.as_ref() {
        Some(Operation::Assistant(_)) => Some(ASSISTANT_TURNS),
        Some(Operation::Chat(command))
            if !matches!(
                command.operation,
                Some(chat_command::Operation::InvalidatePlayer(_))
            ) =>
        {
            Some(CHAT_RECEIPTS)
        }
        Some(Operation::WebLogin(_)) => Some(LOGIN_RECEIPT),
        Some(Operation::Governance(_)) => Some(GOVERNANCE_DELIVERY),
        _ => None,
    }
}

pub(crate) async fn serve(
    listener: SocketListener,
    services: PaperServices,
    stop: CancellationToken,
) -> io::Result<()> {
    let paper = Arc::new(Dispatcher::new(services));
    let warned = Arc::new(AtomicBool::new(false));
    let handshakes = Arc::new(Semaphore::new(4));
    let quotas = Arc::new(HashMap::from([
        (ConnectionRole::Control, Semaphore::new(2)),
        (ConnectionRole::General, Semaphore::new(2)),
        (ConnectionRole::PaperState, Semaphore::new(1)),
        (ConnectionRole::GameQueries, Semaphore::new(2)),
        (ConnectionRole::AccountQueries, Semaphore::new(2)),
        (ConnectionRole::ChatUplink, Semaphore::new(2)),
        (ConnectionRole::ChatDownlink, Semaphore::new(1)),
        (ConnectionRole::Identity, Semaphore::new(1)),
        (ConnectionRole::Governance, Semaphore::new(1)),
    ]));
    let mut tasks = JoinSet::new();
    let outcome = loop {
        tokio::select! {
            () = stop.cancelled() => break Ok(()),
            Some(_) = tasks.join_next(), if !tasks.is_empty() => {},
            accepted = listener.accept() => {
                let mut stream = match accepted { Ok(stream) => stream, Err(error) => break Err(error) };
                let Ok(handshake) = handshakes.clone().try_acquire_owned() else { continue; };
                let paper = paper.clone();
                let quotas = quotas.clone();
                let warned = warned.clone();
                tasks.spawn(async move {
                    let first = timeout(HANDSHAKE_TIMEOUT, read_frame(&mut stream)).await.map_err(io::Error::other)??;
                    let (hello, role) = validate_hello(first)?;
                    let lane = quotas.get(&role).ok_or_else(|| invalid("connection role required"))?;
                    let _permit = lane.try_acquire().map_err(io::Error::other)?;
                    drop(handshake);
                    let missing: Vec<_> = WORKFLOWS.iter()
                        .filter(|name| !hello.capabilities.iter().any(|capability| capability == **name))
                        .copied().collect();
                    if !missing.is_empty() && !warned.swap(true, Ordering::Relaxed) {
                        eprintln!("mikd: Paper '{}' lacks {}; affected workflows are disabled; deploy Paper and mikd from the same build",
                            hello.game_instance, missing.join(", "));
                    }
                    serve_session(stream, role, &hello, &paper).await
                });
            }
        }
    };
    tasks.shutdown().await;
    outcome
}

fn validate_hello(first: Envelope) -> io::Result<(Hello, ConnectionRole)> {
    if first.request_id != 0 || !first.session_id.is_empty() {
        return Err(invalid("invalid handshake envelope"));
    }
    let Some(Body::Hello(hello)) = first.body else {
        return Err(invalid("handshake required"));
    };
    let role = ConnectionRole::try_from(hello.role).map_err(invalid)?;
    if hello.protocol_version != VERSION
        || role == ConnectionRole::Unspecified
        || hello.game_instance.is_empty()
        || hello.game_instance.len() > 64
        || !hello
            .game_instance
            .bytes()
            .all(|byte| byte.is_ascii_alphanumeric() || b"._-".contains(&byte))
        || Uuid::parse_str(&hello.startup_id).is_err()
        || Uuid::parse_str(&hello.connection_id).is_err()
        || hello.capabilities.len() > 16
        || !CAPABILITIES
            .iter()
            .all(|needed| hello.capabilities.iter().any(|cap| cap == needed))
    {
        return Err(invalid("incompatible handshake"));
    }
    Ok((hello, role))
}

async fn serve_session(
    mut stream: UnixStream,
    role: ConnectionRole,
    hello: &Hello,
    paper: &Dispatcher,
) -> io::Result<()> {
    let session_id = Uuid::new_v4().to_string();
    let welcome = Envelope {
        request_id: 0,
        session_id: session_id.clone(),
        body: Some(Body::Welcome(Welcome {
            protocol_version: VERSION,
            maximum_frame_bytes: u32::try_from(MAX_FRAME_BYTES).map_err(invalid)?,
            capabilities: CAPABILITIES
                .iter()
                .chain(WORKFLOWS)
                .map(|capability| (*capability).into())
                .collect(),
        })),
    };
    timeout(IO_TIMEOUT, write_frame(&mut stream, &welcome))
        .await
        .map_err(io::Error::other)??;
    let mut last_request = 0;
    let supports = |name: &str| {
        hello
            .capabilities
            .iter()
            .any(|capability| capability == name)
    };
    let mut session = paper.session(
        role,
        &hello.game_instance,
        &session_id,
        supports(ASSISTANT_TURNS),
    );
    loop {
        let envelope = timeout(IO_TIMEOUT, read_frame(&mut stream))
            .await
            .map_err(io::Error::other)??;
        if envelope.session_id != session_id || envelope.request_id <= last_request {
            return Err(invalid("stale session or request sequence"));
        }
        last_request = envelope.request_id;
        let Some(Body::Request(request)) = envelope.body else {
            return Err(invalid("request required"));
        };
        if !(1..=5000).contains(&request.timeout_ms) {
            return Err(invalid("invalid deadline"));
        }
        let response = if required_workflow(&request).is_some_and(|name| !supports(name)) {
            Response {
                code: Code::Unsupported.into(),
                result: None,
            }
        } else {
            session.handle(request).await
        };
        let result = Envelope {
            request_id: last_request,
            session_id: session_id.clone(),
            body: Some(Body::Response(response)),
        };
        timeout(IO_TIMEOUT, write_frame(&mut stream, &result))
            .await
            .map_err(io::Error::other)??;
    }
}

async fn read_frame(stream: &mut UnixStream) -> io::Result<Envelope> {
    let mut prefix = [0; 4];
    stream.read_exact(&mut prefix).await?;
    let mut buffer = vec![0; frame_length(prefix)?];
    stream.read_exact(&mut buffer).await?;
    Envelope::decode(buffer.as_slice()).map_err(invalid)
}

async fn write_frame(stream: &mut UnixStream, envelope: &Envelope) -> io::Result<()> {
    let length = u32::try_from(envelope.encoded_len()).map_err(invalid)?;
    frame_length(length.to_be_bytes())?;
    stream.write_all(&length.to_be_bytes()).await?;
    stream.write_all(&envelope.encode_to_vec()).await
}

fn reply(code: Code) -> Response {
    Response {
        code: code.into(),
        result: None,
    }
}
