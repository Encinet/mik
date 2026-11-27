use super::{IO_TIMEOUT, Result, Socket, crypto, invalid, record};
use futures_util::{SinkExt, stream::SplitSink};
use serde_json::Value;
use std::{sync::Arc, time::Duration};
use tokio::{
    sync::Mutex,
    time::{Instant, timeout},
};
use tokio_tungstenite::tungstenite::Message;
use tokio_util::sync::CancellationToken;
use zeroize::Zeroizing;

pub(super) const HISTORY: u8 = 2;
pub(super) const HISTORY_DONE: u8 = 4;
pub(super) const HISTORY_ABORT: u8 = 5;
pub(super) const MAX_RECORDS: u32 = 16_384;
pub(super) const MAX_INPUT: usize = record::MAX_REQUEST + 5 + crypto::TAG_SIZE;

#[derive(Clone)]
pub(super) struct Output {
    socket: Arc<Mutex<WireWriter>>,
    stop: CancellationToken,
    pub(super) deflate: bool,
    exchange: Option<u32>,
}

struct WireWriter {
    socket: SplitSink<Socket, Message>,
    outgoing: crypto::Sender,
    sent: u32,
}

impl Output {
    pub(super) fn new(
        socket: SplitSink<Socket, Message>,
        outgoing: crypto::Sender,
        deflate: bool,
        stop: CancellationToken,
    ) -> Self {
        Self {
            socket: Arc::new(Mutex::new(WireWriter {
                socket,
                outgoing,
                sent: 0,
            })),
            stop,
            deflate,
            exchange: None,
        }
    }

    pub(super) fn history(&self, id: u32) -> Self {
        Self {
            exchange: Some(id),
            ..self.clone()
        }
    }

    pub(super) async fn json(&self, value: &Value) -> Result<()> {
        self.write(record::APPLICATION, &serde_json::to_vec(value)?)
            .await
    }

    pub(super) async fn record(&self, record: &record::Record) -> Result<()> {
        self.write(record.kind, &record.payload).await
    }

    async fn write(&self, kind: u8, payload: &[u8]) -> Result<()> {
        if payload.len() > record::MAX_RESPONSE {
            return Err(invalid());
        }
        let mut clear = Zeroizing::new(Vec::with_capacity(payload.len() + 5));
        if let Some(id) = self.exchange {
            clear.push(kind + HISTORY);
            clear.extend_from_slice(&id.to_be_bytes());
        } else {
            clear.push(kind);
        }
        clear.extend_from_slice(payload);
        self.seal(&clear).await
    }

    pub(super) async fn history_end(&self, success: bool) -> Result<()> {
        let mut clear = [if success { HISTORY_DONE } else { HISTORY_ABORT }; 5];
        clear[1..].copy_from_slice(&self.exchange.ok_or_else(invalid)?.to_be_bytes());
        self.seal(&clear).await
    }

    async fn seal(&self, clear: &[u8]) -> Result<()> {
        let mut writer = self.socket.lock().await;
        // Losing a write after sealing would desynchronize the HPKE sequence. Close the socket
        // if a timed out, revoked or cancelled write has already acquired the wire.
        let guard = self.stop.clone().drop_guard();
        if self.stop.is_cancelled() || writer.sent >= MAX_RECORDS {
            return Err(invalid());
        }
        let bytes = writer.outgoing.seal(clear, &[])?;
        writer.sent += 1;
        timeout(IO_TIMEOUT, writer.socket.send(Message::binary(bytes))).await??;
        drop(guard.disarm());
        Ok(())
    }

    pub(super) async fn control(&self, frame: Message) -> Result<()> {
        let mut writer = self.socket.lock().await;
        if self.stop.is_cancelled() {
            return Err(invalid());
        }
        timeout(IO_TIMEOUT, writer.socket.send(frame)).await??;
        Ok(())
    }

    pub(super) async fn flush(&self) -> Result<()> {
        let mut writer = self.socket.lock().await;
        if self.stop.is_cancelled() {
            return Err(invalid());
        }
        timeout(IO_TIMEOUT, writer.socket.flush()).await??;
        Ok(())
    }
}

#[derive(Default)]
pub(super) struct FrameLimit {
    window: Option<Instant>,
    frames: u32,
}

impl FrameLimit {
    pub(super) fn admit(&mut self) -> Result<()> {
        if self
            .window
            .is_none_or(|start| start.elapsed() >= Duration::from_secs(1))
        {
            self.frames = 0;
            self.window = Some(Instant::now());
        }
        self.frames += 1;
        if self.frames > 40 {
            return Err(invalid());
        }
        Ok(())
    }
}
