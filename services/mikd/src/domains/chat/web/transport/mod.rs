mod active;
mod crypto;
mod framing;
mod history;
mod presence;
mod progress;
mod request;
mod scope;

pub(crate) use crypto::Identity;

use super::{
    record,
    session::{Registration, State, WebChat},
};
use framing::{FrameLimit, HISTORY, HISTORY_ABORT, Output};
use futures_util::{SinkExt, StreamExt, stream::SplitStream};
use std::{io, net::IpAddr, sync::Arc, time::Duration};
use tokio::{
    net::{TcpListener, TcpStream},
    sync::{Semaphore, mpsc},
    task::JoinSet,
    time::{interval, timeout},
};
use tokio_tungstenite::{
    WebSocketStream, accept_hdr_async_with_config,
    tungstenite::{
        Message,
        handshake::server::{ErrorResponse, Request, Response},
        http::{HeaderValue, StatusCode, header},
        protocol::WebSocketConfig,
    },
};
use tokio_util::sync::CancellationToken;
use zeroize::Zeroizing;

type Socket = WebSocketStream<TcpStream>;
type Failure = Box<dyn std::error::Error + Send + Sync>;
type Result<T> = std::result::Result<T, Failure>;
const PATH: &str = "/ws";
const VERSION: &str = "2026-11-26-5";
const IO_TIMEOUT: Duration = Duration::from_secs(5);

pub(super) async fn serve(
    web: Arc<WebChat>,
    listener: TcpListener,
    identity: Identity,
    origin: String,
    stop: CancellationToken,
) -> io::Result<()> {
    let permits = Arc::new(Semaphore::new(80));
    let identity = Arc::new(identity);
    let origin: Arc<str> = origin.into();
    let mut tasks = JoinSet::new();
    let game_changes = web.chat.notify_game_changes(&web.presence_changed);
    let departures = web.announce_departures();
    tokio::pin!(game_changes, departures);
    let result = loop {
        tokio::select! {
            () = stop.cancelled() => break Ok(()),
            () = &mut game_changes => break Err(io::Error::other("game presence observer stopped")),
            () = &mut departures => break Err(io::Error::other("web presence observer stopped")),
            Some(result) = tasks.join_next(), if !tasks.is_empty() => {
                if result.is_err() { eprintln!("mikd web chat connection task stopped unexpectedly"); }
            }
            incoming = listener.accept() => {
                let (stream, peer) = match incoming {
                    Ok(value) => value,
                    Err(error) => break Err(error),
                };
                let Ok(permit) = permits.clone().try_acquire_owned() else { continue };
                let web = web.clone();
                let origin = origin.clone();
                let identity = identity.clone();
                tasks.spawn(async move {
                    let _permit = permit;
                    drop(connect(&web, stream, peer.ip(), &origin, &identity).await);
                });
            }
        }
    };
    tasks.shutdown().await;
    result
}

async fn connect(
    web: &WebChat,
    stream: TcpStream,
    mut address: IpAddr,
    origin: &str,
    identity: &Identity,
) -> Result<()> {
    stream.set_nodelay(true)?;
    let config = WebSocketConfig::default()
        .read_buffer_size(16 * 1024)
        .write_buffer_size(0)
        .max_write_buffer_size(record::MAX_RESPONSE + 1024)
        .max_message_size(Some(framing::MAX_INPUT))
        .max_frame_size(Some(framing::MAX_INPUT));
    #[expect(
        clippy::result_large_err,
        reason = "the upgrade API takes ownership of error responses"
    )]
    let upgrade = |request: &Request, mut response: Response| {
        let headers = request.headers();
        let single = |name: &str| {
            let mut values = headers.get_all(name).iter();
            values.next().filter(|_| values.next().is_none())
        };
        let reject = |status| {
            let mut response = ErrorResponse::new(None);
            *response.status_mut() = status;
            response
        };
        if request
            .uri()
            .path_and_query()
            .is_none_or(|path| path.as_str() != PATH)
        {
            return Err(reject(StatusCode::NOT_FOUND));
        }
        if single("origin").is_none_or(|value| value != origin)
            || single("x-forwarded-proto").is_none_or(|value| value != "https")
        {
            return Err(reject(StatusCode::FORBIDDEN));
        }
        if single("sec-websocket-protocol").is_none_or(|value| value != VERSION) {
            return Err(reject(StatusCode::BAD_REQUEST));
        }
        if let Some(client) = single("cf-connecting-ip")
            .and_then(|value| value.to_str().ok())
            .and_then(|value| value.parse::<IpAddr>().ok())
        {
            address = client.to_canonical();
        }
        response.headers_mut().insert(
            header::SEC_WEBSOCKET_PROTOCOL,
            HeaderValue::from_static(VERSION),
        );
        Ok(response)
    };
    let mut socket = timeout(
        IO_TIMEOUT,
        accept_hdr_async_with_config(stream, upgrade, Some(config)),
    )
    .await??;
    let Some(mut session) = web.register(address) else {
        return Ok(());
    };
    let crypto = timeout(
        Duration::from_secs(15),
        authorize(&mut socket, identity, origin, &mut session),
    )
    .await??;
    web.confirm(&session.id);
    let (writer, reader) = socket.split();
    let stop = CancellationToken::new();
    let output = Output::new(writer, crypto.outgoing, crypto.deflate, stop.clone());
    let control = output.clone();
    let progress = progress::Progress::default();
    let (live_tx, live_rx) = mpsc::channel(8);
    let (history_tx, history_rx) = mpsc::channel(4);
    let result = tokio::select! {
        biased;
        () = stop.cancelled() => return Err(invalid()),
        result = receive(reader, crypto.incoming, &control, live_tx, history_tx) => result,
        result = active::live(web, &session, &output, live_rx, &progress) => result,
        result = history::serve(web, &session, &control, history_rx, &progress) => result,
        result = heartbeat(&control) => result,
        () = tokio::time::sleep(Duration::from_mins(30)) => Ok(()),
    };
    if !stop.is_cancelled() {
        drop(control.control(Message::Close(None)).await);
    }
    result
}

async fn authorize(
    socket: &mut Socket,
    identity: &Identity,
    origin: &str,
    session: &mut Registration<'_>,
) -> Result<crypto::Established> {
    let (exchange, hello) = crypto::Exchange::begin(identity, origin, &session.id)?;
    socket.send(Message::text(hello.to_string())).await?;
    let Some(Message::Binary(proof)) = socket.next().await.transpose()? else {
        return Err(invalid());
    };
    let mut established = exchange.accept(&proof)?;
    let lease = loop {
        let state = session.state.borrow_and_update().clone();
        match state {
            State::Authorized(lease) => break lease,
            State::Closed(_) => return Err(invalid()),
            State::Awaiting => {}
        }
        tokio::select! {
            state = session.state.changed() => state?,
            _ = socket.next() => return Err(invalid()),
        }
    };
    if lease.grant.client_public_key != established.client_key
        || !session
            .state
            .borrow()
            .permits_reply(&lease, crate::domains::chat::now())
    {
        return Err(invalid());
    }
    socket
        .send(Message::binary(std::mem::take(&mut established.enc)))
        .await?;
    Ok(established)
}

async fn receive(
    mut reader: SplitStream<Socket>,
    mut incoming: crypto::Receiver,
    output: &Output,
    live: mpsc::Sender<Zeroizing<Vec<u8>>>,
    history: mpsc::Sender<history::Action>,
) -> Result<()> {
    let mut limit = FrameLimit::default();
    let mut last_exchange = 0;
    let mut received = 0;
    loop {
        match timeout(Duration::from_secs(55), reader.next())
            .await?
            .transpose()?
        {
            Some(Message::Binary(frame)) => {
                limit.admit()?;
                if received >= framing::MAX_RECORDS {
                    return Ok(());
                }
                received += 1;
                let clear = Zeroizing::new(incoming.open(&frame, &[])?);
                match clear.split_first() {
                    Some((0 | 1, _)) => live.try_send(clear).map_err(|_| invalid())?,
                    Some((&kind @ (HISTORY | HISTORY_ABORT), body)) => {
                        let (id, body) = body.split_first_chunk::<4>().ok_or_else(invalid)?;
                        let id = u32::from_be_bytes(*id);
                        let action = match kind {
                            HISTORY if id > last_exchange && !body.is_empty() => {
                                last_exchange = id;
                                let mut request =
                                    Zeroizing::new(Vec::with_capacity(body.len() + 1));
                                request.push(record::APPLICATION);
                                request.extend_from_slice(body);
                                history::Action::Read(id, request)
                            }
                            HISTORY_ABORT if id > 0 && id <= last_exchange && body.is_empty() => {
                                history::Action::Cancel(id)
                            }
                            _ => return Err(invalid()),
                        };
                        history.try_send(action).map_err(|_| invalid())?;
                    }
                    _ => return Err(invalid()),
                }
            }
            Some(Message::Ping(_)) => {
                limit.admit()?;
                output.flush().await?;
            }
            Some(Message::Pong(_)) => limit.admit()?,
            None | Some(Message::Close(_)) => return Ok(()),
            _ => return Err(invalid()),
        }
    }
}

async fn heartbeat(output: &Output) -> Result<()> {
    let mut ticks = interval(Duration::from_secs(20));
    ticks.tick().await;
    loop {
        ticks.tick().await;
        output.control(Message::Ping(Vec::new().into())).await?;
    }
}

fn invalid() -> Failure {
    io::Error::new(io::ErrorKind::InvalidData, "invalid web chat record").into()
}
