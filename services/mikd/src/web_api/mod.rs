mod chat;
mod routes;

use crate::{
    domains::{access::Access, announcements::Announcements, chat::web::WebChat},
    game::GameBridge,
};
use hyper::server::conn::http1;
use hyper_util::{
    rt::{TokioIo, TokioTimer},
    service::TowerToHyperService,
};
use std::{io, sync::Arc, time::Duration};
use tokio::{net::TcpListener, sync::Semaphore, task::JoinSet, time::timeout};
use tokio_util::sync::CancellationToken;

pub(crate) struct WebApi {
    game: Arc<GameBridge>,
    access: Arc<Access>,
    chat: Option<Arc<WebChat>>,
    announcements: Option<Arc<Announcements>>,
}

impl WebApi {
    pub(crate) fn new(
        game: Arc<GameBridge>,
        access: Arc<Access>,
        chat: Option<Arc<WebChat>>,
        announcements: Option<Arc<Announcements>>,
    ) -> Self {
        Self {
            game,
            access,
            chat,
            announcements,
        }
    }

    pub(crate) async fn serve(
        self,
        listener: TcpListener,
        stop: CancellationToken,
    ) -> io::Result<()> {
        let router = routes::router(Arc::new(self));
        let incoming = Arc::new(Semaphore::new(64));
        let mut tasks = JoinSet::new();
        let outcome = loop {
            tokio::select! {
                () = stop.cancelled() => break Ok(()),
            Some(result) = tasks.join_next(), if !tasks.is_empty() => {
                if result.is_err() {
                    eprintln!("mikd web API connection task stopped unexpectedly");
                }
            },
                accepted = listener.accept() => {
                    let (stream, peer) = match accepted { Ok(value) => value, Err(error) => break Err(error) };
                    if !peer.ip().is_loopback() { continue; }
                    let Ok(permit) = incoming.clone().try_acquire_owned() else { continue; };
                    let service = TowerToHyperService::new(router.clone());
                    tasks.spawn(async move {
                        let _permit = permit;
                        let mut http = http1::Builder::new();
                        http.keep_alive(false).timer(TokioTimer::new()).header_read_timeout(Duration::from_secs(2));
                        // A peer error or timeout closes only this connection.
                        drop(timeout(Duration::from_secs(10), http.serve_connection(TokioIo::new(stream), service)).await);
                    });
                }
            }
        };
        tasks.shutdown().await;
        outcome
    }
}
