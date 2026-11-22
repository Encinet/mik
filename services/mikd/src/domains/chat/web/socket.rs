use super::{
    application,
    crypto::{APPLICATION, EXCHANGE, Exchange, Keys},
    session::{Lease, Registration, State, WebChat},
};
use crate::domains::chat::{now, presence::GameView};
use futures_util::{SinkExt, StreamExt};
use serde_json::{Value, json};
use std::{
    io,
    net::IpAddr,
    sync::{Arc, Weak},
    time::Duration,
};
use tokio::{
    net::{TcpListener, TcpStream},
    sync::{Semaphore, broadcast},
    task::JoinSet,
    time::{Instant, MissedTickBehavior, interval, timeout},
};
use tokio_tungstenite::{
    WebSocketStream, accept_hdr_async_with_config,
    tungstenite::{
        Message,
        handshake::server::{ErrorResponse, Request, Response},
        http::StatusCode,
        protocol::WebSocketConfig,
    },
};
use tokio_util::sync::CancellationToken;
use zeroize::Zeroizing;

type Socket = WebSocketStream<TcpStream>;
type Failure = Box<dyn std::error::Error + Send + Sync>;
type Result<T> = std::result::Result<T, Failure>;
const IO_TIMEOUT: Duration = Duration::from_secs(5);
const MAX_INPUT: usize = 8192 + 17;
const KEY_LIFETIME: Duration = Duration::from_mins(30);

pub(super) async fn serve(
    web: Arc<WebChat>,
    listener: TcpListener,
    origin: String,
    stop: CancellationToken,
) -> io::Result<()> {
    let permits = Arc::new(Semaphore::new(80));
    let origin: Arc<str> = origin.into();
    let mut tasks = JoinSet::new();
    let outcome = loop {
        tokio::select! {
            () = stop.cancelled() => break Ok(()),
            Some(result) = tasks.join_next(), if !tasks.is_empty() => {
                if result.is_err() {
                    eprintln!("mikd web chat connection task stopped unexpectedly");
                }
            },
            connection = listener.accept() => {
                let (stream, peer) = match connection { Ok(value) => value, Err(error) => break Err(error) };
                let Ok(permit) = permits.clone().try_acquire_owned() else { continue; };
                let web = web.clone();
                let origin = origin.clone();
                tasks.spawn(async move {
                    let _permit = permit;
                    // Handshake rejection and disconnects end only this connection.
                    drop(connect(&web, stream, peer.ip(), &origin).await);
                });
            }
        }
    };
    tasks.shutdown().await;
    outcome
}

async fn connect(
    web: &WebChat,
    stream: TcpStream,
    mut address: IpAddr,
    origin: &str,
) -> Result<()> {
    stream.set_nodelay(true)?;
    let config = WebSocketConfig::default()
        .read_buffer_size(16 * 1024)
        .max_message_size(Some(MAX_INPUT))
        .max_frame_size(Some(MAX_INPUT));
    #[expect(
        clippy::result_large_err,
        reason = "Tungstenite's upgrade callback requires an HTTP error response by value"
    )]
    let upgrade = |request: &Request, response: Response| {
        let headers = request.headers();
        let protocols = headers.get_all("x-forwarded-proto");
        let mut forwarded = protocols.iter();
        if request
            .uri()
            .path_and_query()
            .is_none_or(|path| path.as_str() != "/ws")
            || headers.get("origin").is_none_or(|value| value != origin)
            || forwarded.next().is_none_or(|value| value != "https")
            || forwarded.next().is_some()
        {
            let mut reject = ErrorResponse::new(None);
            *reject.status_mut() = StatusCode::FORBIDDEN;
            return Err(reject);
        }
        if let Some(client) = headers
            .get("cf-connecting-ip")
            .and_then(|value| value.to_str().ok())
            .and_then(|value| value.parse().ok())
        {
            address = client;
        }
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
    write(
        &mut socket,
        Message::Text(
            json!({"type":"hello", "connectionId":session.id})
                .to_string()
                .into(),
        ),
    )
    .await?;
    let lease = timeout(Duration::from_secs(10), async {
        loop {
            let state = session.state.borrow_and_update().clone();
            match state {
                State::Awaiting => {}
                State::Authorized(lease) => return Ok(lease),
                State::Closed(_) => return Err(invalid()),
            }
            tokio::select! {
                result = session.state.changed() => result?,
                _ = socket.next() => return Err(invalid()),
            }
        }
    })
    .await??;
    let mut pending = Vec::new();
    let key = lease.grant.client_public_key.clone();
    let keys = timeout(
        IO_TIMEOUT,
        exchange(web, &session.id, &key, &mut socket, None, &mut pending),
    )
    .await??;
    web.confirm(&session.id);
    active(web, &mut session, &mut socket, &key, keys).await
}

async fn active(
    web: &WebChat,
    session: &mut Registration<'_>,
    socket: &mut Socket,
    key: &str,
    mut keys: Keys,
) -> Result<()> {
    let mut live = web.chat.subscribe();
    let mut published = None;
    let mut presence = PresenceUpdates::default();
    let mut refreshed = 0;
    let connected = Instant::now();
    let mut ping_sent_at = connected;
    let mut last_pong = connected;
    let mut established = connected;
    let mut frame_window = connected;
    let mut frames = 0;
    let mut tick = interval(Duration::from_millis(500));
    tick.set_missed_tick_behavior(MissedTickBehavior::Skip);
    loop {
        let state = session.state.borrow_and_update().clone();
        let lease = match state {
            State::Authorized(lease) if lease.grant.expires_at > now() => lease,
            other => {
                let reason = match other {
                    State::Closed(reason) => reason,
                    _ => "authorization_expired",
                };
                send(
                    socket,
                    &mut keys,
                    &json!({"type":"closed", "reason":reason}),
                )
                .await?;
                return Ok(());
            }
        };
        let access = (
            lease.grant.expires_at,
            lease.staff,
            &lease.grant.player_name,
        );
        if published
            .as_ref()
            .is_none_or(|(expiry, staff, name)| (*expiry, *staff, name) != access)
        {
            send(socket, &mut keys, &json!({"type":"authorized", "playerUuid":lease.grant.player_uuid,
                "name":lease.grant.player_name, "staff":lease.staff, "expiresAt":lease.grant.expires_at, "history":true})).await?;
            published = Some((access.0, access.1, access.2.clone()));
        }
        if keys.needs_rotation() || established.elapsed() >= KEY_LIFETIME {
            let mut pending = Vec::new();
            keys = timeout(
                IO_TIMEOUT,
                exchange(web, &session.id, key, socket, Some(&mut keys), &mut pending),
            )
            .await??;
            established = Instant::now();
            for body in pending {
                process(web, session, socket, &mut keys, &body).await?;
            }
        }
        tokio::select! {
            biased;
            state = session.state.changed() => state?,
            _ = tick.tick() => {
                if last_pong.elapsed() >= Duration::from_secs(55) { return Ok(()); }
                if ping_sent_at.elapsed() >= Duration::from_secs(20) {
                    write(socket, Message::Ping(Vec::new().into())).await?;
                    ping_sent_at = Instant::now();
                }
                if now() - lease.grant.checked_at >= 45_000 && refreshed != lease.grant.checked_at
                    && web.renewal_connection(&session.id, &lease)
                {
                    refreshed = lease.grant.checked_at;
                    send(socket, &mut keys, &json!({"type":"reauthorize"})).await?;
                }
                if let Some(update) = presence.next(web, &lease)? {
                    let frame = Message::Binary(keys.seal(APPLICATION, update)?.into());
                    write(socket, frame).await?;
                }
            }
            frame = socket.next() => {
                if frame_window.elapsed() >= Duration::from_secs(1) { frames = 0; frame_window = Instant::now(); }
                frames += 1;
                if frames > 40 { return Err(invalid()); }
                match frame.transpose()? {
                    Some(Message::Binary(bytes)) => {
                        let body = keys.open(&bytes)?;
                        process(web, session, socket, &mut keys, &body).await?;
                    }
                    Some(Message::Pong(_)) => last_pong = Instant::now(),
                    Some(Message::Ping(_)) => timeout(IO_TIMEOUT, socket.flush()).await??,
                    Some(Message::Close(_)) | None => return Ok(()),
                    _ => return Err(invalid()),
                }
            }
            event = live.recv() => match event {
                Ok(event) => if let Some(output) = application::live(web, &lease, &event) {
                    send(socket, &mut keys, &output).await?;
                },
                Err(broadcast::error::RecvError::Lagged(_) | broadcast::error::RecvError::Closed) => return Ok(()),
            }
        }
    }
}

#[derive(Default)]
struct PresenceUpdates {
    source: Option<(Weak<GameView>, u64, bool)>,
    payload: Option<String>,
}

impl PresenceUpdates {
    fn next(&mut self, web: &WebChat, lease: &Lease) -> serde_json::Result<Option<&str>> {
        let update = if let Some(game) = web.chat.game() {
            let roster = web.web_roster();
            if self
                .source
                .as_ref()
                .is_some_and(|(previous, revision, staff)| {
                    std::ptr::eq(previous.as_ptr(), Arc::as_ptr(&game))
                        && *revision == roster.revision
                        && *staff == lease.staff
                })
            {
                return Ok(None);
            }
            let update = application::presence(Some(&game), &roster.players, lease)?;
            self.source = Some((Arc::downgrade(&game), roster.revision, lease.staff));
            update
        } else if self.source.take().is_some() {
            application::presence(None, &[], lease)?
        } else {
            return Ok(None);
        };
        if self.payload.as_ref() == Some(&update) {
            return Ok(None);
        }
        Ok(Some(self.payload.insert(update)))
    }
}

async fn process(
    web: &WebChat,
    session: &Registration<'_>,
    socket: &mut Socket,
    keys: &mut Keys,
    body: &[u8],
) -> Result<()> {
    let Some((&APPLICATION, body)) = body.split_first() else {
        return Err(invalid());
    };
    let state = session.state.borrow().clone();
    let State::Authorized(lease) = state else {
        return Ok(());
    };
    if lease.grant.expires_at <= now() {
        return Ok(());
    }
    let output = application::request(web, &lease, std::str::from_utf8(body)?).await;
    if !session.state.borrow().permits_reply(&lease, now()) {
        return Ok(());
    }
    if output["type"] == "accepted" {
        web.activity(&session.id);
    }
    send(socket, keys, &output).await
}

async fn exchange(
    web: &WebChat,
    id: &str,
    key: &str,
    socket: &mut Socket,
    mut old: Option<&mut Keys>,
    pending: &mut Vec<Zeroizing<Vec<u8>>>,
) -> Result<Keys> {
    let generation = old.as_ref().map_or(0, |keys| keys.generation + 1);
    let (exchange, announcement) = Exchange::begin(&web.identity, id, key, generation)?;
    key_frame(socket, old.as_deref_mut(), &announcement).await?;
    let reply = loop {
        match (read(socket).await?, old.as_deref_mut()) {
            (Message::Text(text), None) if text.len() <= 1024 => {
                break Zeroizing::new(text.as_bytes().to_vec());
            }
            (Message::Binary(frame), Some(keys)) => {
                let mut clear = keys.open(&frame)?;
                match clear.first() {
                    Some(&EXCHANGE) if clear.len() <= 1025 => {
                        clear.remove(0);
                        break clear;
                    }
                    Some(&APPLICATION) if pending.len() < 16 => pending.push(clear),
                    _ => return Err(invalid()),
                }
            }
            _ => return Err(invalid()),
        }
    };
    let (mut keys, confirm) = exchange.accept(&reply)?;
    key_frame(socket, old, &confirm).await?;
    let Message::Binary(finished) = read(socket).await? else {
        return Err(invalid());
    };
    keys.finish(&finished)?;
    Ok(keys)
}

async fn read(socket: &mut Socket) -> Result<Message> {
    loop {
        let frame = socket.next().await.ok_or_else(invalid)??;
        match frame {
            Message::Ping(_) => socket.flush().await?,
            Message::Pong(_) => {}
            _ => return Ok(frame),
        }
    }
}

async fn key_frame(socket: &mut Socket, keys: Option<&mut Keys>, value: &Value) -> Result<()> {
    let json = value.to_string();
    let frame = if let Some(keys) = keys {
        Message::Binary(keys.seal(EXCHANGE, &json)?.into())
    } else {
        Message::Text(json.into())
    };
    write(socket, frame).await
}

async fn send(socket: &mut Socket, keys: &mut Keys, value: &Value) -> Result<()> {
    write(
        socket,
        Message::Binary(keys.seal(APPLICATION, &value.to_string())?.into()),
    )
    .await
}

async fn write(socket: &mut Socket, frame: Message) -> Result<()> {
    timeout(IO_TIMEOUT, socket.send(frame)).await??;
    Ok(())
}

fn invalid() -> Failure {
    io::Error::new(io::ErrorKind::InvalidData, "invalid web chat frame").into()
}
