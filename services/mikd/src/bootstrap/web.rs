use super::config::WebChatConfig;
use crate::{
    domains::chat::{ChatService, web::WebChat},
    web_api::WebApi,
};
use std::{
    fs, io,
    net::Ipv4Addr,
    path::Path,
    sync::{
        Arc,
        atomic::{AtomicU16, Ordering},
    },
};
use tokio::{
    net::TcpListener,
    task::{JoinSet, spawn_blocking},
};
use tokio_util::sync::CancellationToken;

/// The WebSocket depends on the private HTTP API to authorize or revoke sessions.
pub(super) struct Web {
    api: Bound,
    socket: Option<WebSocket>,
}

struct Bound {
    listener: TcpListener,
    port: PublishedPort,
}

// Keep IPC status accurate even if a listener task unwinds or is cancelled.
struct PublishedPort(Arc<AtomicU16>);

impl Drop for PublishedPort {
    fn drop(&mut self) {
        self.0.store(0, Ordering::Relaxed);
    }
}

impl Bound {
    async fn open(port: u16) -> io::Result<Self> {
        let listener = TcpListener::bind((Ipv4Addr::LOCALHOST, port)).await?;
        let port = PublishedPort(Arc::new(AtomicU16::new(listener.local_addr()?.port())));
        Ok(Self { listener, port })
    }
}

struct WebSocket {
    bound: Bound,
    chat: Arc<WebChat>,
    origin: String,
}

impl WebSocket {
    async fn open(config: WebChatConfig, chat: Option<&Arc<ChatService>>) -> io::Result<Self> {
        let WebChatConfig { port, key, origin } = config;
        let chat = chat
            .cloned()
            .ok_or_else(|| io::Error::other("shared chat history unavailable"))?;
        let bound = Bound::open(port).await.map_err(|error| {
            io::Error::new(error.kind(), format!("bind loopback port {port}: {error}"))
        })?;
        let chat = spawn_blocking(move || {
            open_chat(chat, &key).map_err(|error| {
                io::Error::new(
                    error.kind(),
                    format!("signing key {}: {error}", key.display()),
                )
            })
        })
        .await
        .map_err(io::Error::other)??;
        Ok(Self {
            bound,
            chat,
            origin,
        })
    }

    async fn serve(self, stop: CancellationToken) {
        let Bound { listener, port } = self.bound;
        let result = self.chat.serve(listener, self.origin, stop.clone()).await;
        drop(port);
        report_exit("WebSocket", result, &stop);
    }
}

impl Web {
    pub(super) async fn open(
        api_port: Option<u16>,
        socket: Option<WebChatConfig>,
        chat: Option<&Arc<ChatService>>,
    ) -> Option<Self> {
        let port = api_port?;
        let api = match Bound::open(port).await {
            Ok(api) => api,
            Err(error) => {
                eprintln!("mikd Web API unavailable until restart (port {port}): {error}");
                if socket.is_some() {
                    eprintln!("mikd WebSocket not started: Web API unavailable");
                }
                return None;
            }
        };
        let socket = if let Some(config) = socket {
            match WebSocket::open(config, chat).await {
                Ok(socket) => Some(socket),
                Err(error) => {
                    eprintln!("mikd WebSocket unavailable until restart: {error}");
                    None
                }
            }
        } else {
            None
        };
        Some(Self { api, socket })
    }

    pub(super) fn chat(&self) -> Option<Arc<WebChat>> {
        self.socket.as_ref().map(|socket| socket.chat.clone())
    }

    pub(super) fn api_port(&self) -> Arc<AtomicU16> {
        self.api.port.0.clone()
    }

    pub(super) fn chat_port(&self) -> Arc<AtomicU16> {
        self.socket
            .as_ref()
            .map_or_else(Arc::default, |socket| socket.bound.port.0.clone())
    }

    pub(super) fn start(self, api: WebApi, stop: &CancellationToken, tasks: &mut JoinSet<()>) {
        let Bound { listener, port } = self.api;
        let socket_stop = stop.child_token();
        if let Some(socket) = self.socket {
            tasks.spawn(socket.serve(socket_stop.clone()));
        }
        // Losing HTTP also closes live WebSocket sessions, but never cancels Paper IPC.
        let stop_socket = socket_stop.drop_guard();
        let stop = stop.clone();
        tasks.spawn(async move {
            let result = api.serve(listener, stop.clone()).await;
            drop(port);
            drop(stop_socket);
            report_exit("Web API", result, &stop);
        });
    }
}

fn open_chat(chat: Arc<ChatService>, key: &Path) -> io::Result<Arc<WebChat>> {
    // A FIFO is not an existing signing key and must not wait for a writer.
    match fs::symlink_metadata(key) {
        Ok(data) if !data.is_file() => {
            return Err(io::Error::other("expected a regular signing-key file"));
        }
        Ok(_) => {}
        Err(error) if error.kind() == io::ErrorKind::NotFound => {}
        Err(error) => return Err(error),
    }
    WebChat::open(chat, key).map(Arc::new)
}

fn report_exit(name: &str, result: io::Result<()>, stop: &CancellationToken) {
    match result {
        Ok(()) if stop.is_cancelled() => {}
        Ok(()) => eprintln!("mikd {name} stopped unexpectedly; unavailable until restart"),
        Err(error) => eprintln!("mikd {name} unavailable until restart: {error}"),
    }
}
