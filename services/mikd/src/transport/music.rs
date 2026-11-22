use super::SocketListener;
use crate::domains::music::{Lease, Outcome, ReadFailure, Service, wire_failure};
use mik_protocol::music::{
    MusicFailure, MusicMediaEof, MusicMediaOpened, MusicReadEnd, MusicReadMedia, MusicReadReady,
    MusicReply, MusicRequest, music_failure::Code, music_read_end::Outcome as ReadOutcome,
    music_reply::Result as Reply, music_request::Operation,
};
use mik_protocol::{MUSIC_FRAME_BYTES, MUSIC_OUTPUTS, MUSIC_PREFACE};
use prost::Message;
use std::{
    collections::HashMap,
    io,
    sync::{Arc, Mutex, PoisonError},
    time::Duration,
};
use tokio::{
    io::{AsyncReadExt, AsyncWrite, AsyncWriteExt},
    net::UnixStream,
    sync::{OwnedSemaphorePermit, Semaphore},
    task::JoinSet,
    time::timeout,
};
use tokio_util::sync::CancellationToken;
use uuid::Uuid;

const OPEN_TIME: Duration = Duration::from_secs(90);
const CONTROL_TIME: Duration = Duration::from_secs(20);
const STREAM_WRITE_TIME: Duration = Duration::from_secs(15);
const BLOCK: usize = 64 * 1024;

struct Limits {
    control: Semaphore,
    browse: Semaphore,
    lyrics: Semaphore,
    opens: Semaphore,
    streams: Semaphore,
    leases: Mutex<HashMap<Uuid, Arc<LiveLease>>>,
}

struct LiveLease {
    media: Lease,
    profile: u8,
    active: Mutex<(u64, CancellationToken)>,
}

struct Registration<'a> {
    limits: &'a Limits,
    id: Uuid,
}

impl Drop for Registration<'_> {
    fn drop(&mut self) {
        // Clean up the owned lease even if a task failed while updating either lock.
        let lease = self
            .limits
            .leases
            .lock()
            .unwrap_or_else(PoisonError::into_inner)
            .remove(&self.id);
        if let Some(lease) = lease {
            lease
                .active
                .lock()
                .unwrap_or_else(PoisonError::into_inner)
                .1
                .cancel();
        }
    }
}

pub(crate) async fn serve(
    listener: SocketListener,
    service: Arc<Service>,
    stop: CancellationToken,
) -> io::Result<()> {
    let pending = Arc::new(Semaphore::new(64));
    let limits = Arc::new(Limits {
        control: Semaphore::new(16),
        browse: Semaphore::new(2),
        lyrics: Semaphore::new(8),
        opens: Semaphore::new(40),
        streams: Semaphore::new(48),
        leases: Mutex::default(),
    });
    let mut tasks = JoinSet::new();
    let outcome = loop {
        tokio::select! {
            () = stop.cancelled() => break Ok(()),
            Some(result) = tasks.join_next(), if !tasks.is_empty() => {
                if result.is_err() {
                    eprintln!("mikd music connection task stopped unexpectedly");
                }
            },
            accepted = listener.accept() => {
                let socket = match accepted { Ok(socket) => socket, Err(error) => break Err(error) };
                let Ok(permit) = pending.clone().try_acquire_owned() else { continue; };
                let service = service.clone();
                let limits = limits.clone();
                tasks.spawn(async move {
                    // Malformed frames, timeouts, and dropped peers only close their connection.
                    drop(handle_connection(socket, &service, &limits, permit).await);
                });
            }
        }
    };
    tasks.shutdown().await;
    outcome
}

async fn handle_connection(
    mut socket: UnixStream,
    service: &Service,
    limits: &Limits,
    pending: OwnedSemaphorePermit,
) -> io::Result<()> {
    let (profile, request) = timeout(Duration::from_secs(5), async {
        let mut prefix = [0; MUSIC_PREFACE.len()];
        socket.read_exact(&mut prefix).await?;
        if prefix != MUSIC_PREFACE {
            return Err(io::Error::other("invalid music protocol version"));
        }
        let profile = socket.read_u8().await? & MUSIC_OUTPUTS;
        if profile == 0 {
            return Err(io::Error::other("unsupported music output profile"));
        }
        socket.write_u8(profile).await?;
        Ok::<_, io::Error>((profile, read_request(&mut socket).await?))
    })
    .await??;
    drop(pending);
    if let Some(Operation::ReadMedia(read)) = request.operation {
        return open_stream(socket, limits, profile, read).await;
    }
    let opening = matches!(request.operation, Some(Operation::OpenMedia(_)));
    let lane = match &request.operation {
        Some(Operation::OpenMedia(_)) => &limits.opens,
        Some(Operation::Lyrics(_)) => &limits.lyrics,
        Some(Operation::List(_) | Operation::Collection(_)) => &limits.browse,
        _ => &limits.control,
    };
    let Ok(_permit) = lane.try_acquire() else {
        return reply(&mut socket, failed(Code::Busy)).await;
    };
    let deadline = if opening { OPEN_TIME } else { CONTROL_TIME };
    let outcome = tokio::select! {
        outcome = timeout(deadline, service.execute(request, profile)) => outcome,
        _ = socket.read_u8() => return Ok(()),
    };
    let Ok(outcome) = outcome else {
        return reply(&mut socket, failed(Code::DeadlineExceeded)).await;
    };
    match outcome {
        Ok(Outcome::Reply(result)) => reply(&mut socket, result).await,
        Ok(Outcome::Media(media)) => {
            let id = Uuid::new_v4();
            let opened = MusicMediaOpened {
                lease_id: id.as_bytes().to_vec(),
                media_version: media.version().simple().to_string(),
                descriptor: Some(media.descriptor.clone()),
            };
            limits
                .leases
                .lock()
                .map_err(|_| io::Error::other("music lease registry unavailable"))?
                .insert(
                    id,
                    Arc::new(LiveLease {
                        media,
                        profile,
                        active: Mutex::new((0, CancellationToken::new())),
                    }),
                );
            let _registration = Registration { limits, id };
            reply(&mut socket, Reply::MediaOpened(opened)).await?;
            // EOF, unexpected bytes, and I/O failures all release this lease.
            drop(socket.read_u8().await);
            Ok(())
        }
        Err(error) => {
            let fallback = if opening {
                Code::TransferFailed
            } else {
                Code::Unavailable
            };
            reply(&mut socket, Reply::Failure(wire_failure(&error, fallback))).await
        }
    }
}

async fn read_request(socket: &mut UnixStream) -> io::Result<MusicRequest> {
    let size = socket.read_u32().await? as usize;
    if !(1..=MUSIC_FRAME_BYTES).contains(&size) {
        return Err(io::Error::other("invalid music request frame"));
    }
    let mut buffer = vec![0; size];
    socket.read_exact(&mut buffer).await?;
    MusicRequest::decode(buffer.as_slice()).map_err(io::Error::other)
}

async fn reply(socket: &mut (impl AsyncWrite + Unpin), result: Reply) -> io::Result<()> {
    let mut message = MusicReply {
        result: Some(result),
    };
    if message.encoded_len() > MUSIC_FRAME_BYTES {
        message.result = Some(failed(Code::Unavailable));
    }
    let bytes = message.encode_to_vec();
    let size = u32::try_from(bytes.len()).map_err(io::Error::other)?;
    timeout(Duration::from_secs(5), async {
        socket.write_u32(size).await?;
        socket.write_all(&bytes).await
    })
    .await?
}

fn failure(code: Code) -> MusicFailure {
    MusicFailure { code: code.into() }
}
fn failed(code: Code) -> Reply {
    Reply::Failure(failure(code))
}

async fn open_stream(
    mut socket: UnixStream,
    limits: &Limits,
    profile: u8,
    read: MusicReadMedia,
) -> io::Result<()> {
    let lease = {
        let leases = limits
            .leases
            .lock()
            .map_err(|_| io::Error::other("music lease registry unavailable"))?;
        Uuid::from_slice(&read.lease_id).ok().and_then(|id| {
            leases
                .get(&id)
                .filter(|lease| lease.profile == profile)
                .cloned()
        })
    };
    let Some(lease) = lease else {
        return reply(&mut socket, failed(Code::NotFound)).await;
    };
    let Ok(_permit) = limits.streams.try_acquire() else {
        return reply(&mut socket, failed(Code::Busy)).await;
    };
    let cancel = {
        let mut active = lease
            .active
            .lock()
            .map_err(|_| io::Error::other("music lease reader unavailable"))?;
        if active.1.is_cancelled() || read.read_seq <= active.0 {
            None
        } else {
            active.1.cancel();
            let cancel = CancellationToken::new();
            *active = (read.read_seq, cancel.clone());
            Some(cancel)
        }
    };
    let Some(cancel) = cancel else {
        return reply(&mut socket, failed(Code::Superseded)).await;
    };
    let (mut peer, mut output) = socket.split();
    tokio::select! {
        () = cancel.cancelled() => Ok(()),
        _ = peer.read_u8() => Ok(()),
        result = stream(&mut output, &lease.media, read) => result,
    }
}

async fn stream(
    output: &mut (impl AsyncWrite + Unpin),
    lease: &Lease,
    read: MusicReadMedia,
) -> io::Result<()> {
    reply(output, Reply::ReadReady(MusicReadReady {})).await?;
    let end = match lease.reader(read.byte_offset).await {
        Ok(mut reader) => {
            let mut buffer = vec![0; BLOCK];
            loop {
                match reader.read(&mut buffer).await {
                    Ok(0) => break ReadOutcome::Eof(MusicMediaEof {}),
                    Ok(length) => {
                        timeout(STREAM_WRITE_TIME, async {
                            output
                                .write_u32(u32::try_from(length).map_err(io::Error::other)?)
                                .await?;
                            output.write_all(&buffer[..length]).await
                        })
                        .await??;
                    }
                    Err(error) => break read_failure(lease, error),
                }
            }
        }
        Err(error) => read_failure(lease, error),
    };
    timeout(STREAM_WRITE_TIME, output.write_u32(0)).await??;
    reply(output, Reply::ReadEnd(MusicReadEnd { outcome: Some(end) })).await
}

fn read_failure(lease: &Lease, error: ReadFailure) -> ReadOutcome {
    let (code, detail) = match error {
        ReadFailure::Transfer(detail) => (Code::TransferFailed, detail),
        ReadFailure::Io(error) => (Code::MediaIo, error.to_string()),
    };
    eprintln!(
        "mikd music read version={} {} {}: {detail}",
        lease.version().simple(),
        lease.origin(),
        code.as_str_name()
    );
    ReadOutcome::Failure(failure(code))
}
