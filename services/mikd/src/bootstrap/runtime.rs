use super::{
    config::{Config, MusicStartup},
    web::Web,
};
use crate::{
    domains::{
        access::Access,
        ai::{AiConfig, Assistant, AssistantSlot, AssistantState},
        announcements::Announcements,
        chat::{ChatService, HistoryStore},
        community::{CommunityStore, IdentityService},
        music,
        social::{self, Directory, QqModeration},
    },
    game::GameBridge,
    transport::{PaperServices, SocketListener, admin, serve_control, serve_music},
    web_api::WebApi,
};
use std::{
    future::Future,
    io,
    path::Path,
    sync::{Arc, atomic::Ordering},
    time::Duration,
};
use tokio::{
    signal::unix::{SignalKind, signal},
    task::{JoinError, JoinSet, spawn_blocking},
    time::{self, MissedTickBehavior},
};
use tokio_util::sync::CancellationToken;

#[derive(Default)]
struct Services {
    chat: Option<Arc<ChatService>>,
    community: Option<Arc<CommunityStore>>,
    assistant: AssistantSlot,
    announcements: Option<Arc<Announcements>>,
}

pub(super) async fn run(config: Config, qq_moderation: Option<QqModeration>) -> io::Result<()> {
    let mut interrupt = signal(SignalKind::interrupt())?;
    let mut terminate = signal(SignalKind::terminate())?;
    let mut services = Services::default();
    let mut result = services
        .serve(config, qq_moderation, async {
            tokio::select! { _ = interrupt.recv() => {}, _ = terminate.recv() => {} }
        })
        .await;
    record(&mut result, services.close().await);
    result
}

impl Services {
    async fn serve(
        &mut self,
        config: Config,
        qq_moderation: Option<QqModeration>,
        shutdown: impl Future<Output = ()>,
    ) -> io::Result<()> {
        let control = SocketListener::bind(&config.control_socket, config.game_uid)?;
        self.open_core(&config.community, &config.history).await?;

        let game = Arc::new(GameBridge::new());
        let directory = Arc::new(
            config
                .social
                .as_ref()
                .map_or_else(Directory::default, Directory::new),
        );
        let identity = self
            .community
            .as_ref()
            .map(|store| Arc::new(IdentityService::new(store.clone(), directory.clone())));
        let access = Arc::new(Access::new(game.clone(), self.community.clone()));
        self.announcements = Some(Arc::new(Announcements::new(&config.announcements)));

        let web = Web::open(config.web_api, config.web_chat, self.chat.as_ref()).await;
        let stop = CancellationToken::new();
        let mut background = self.maintain(&stop);
        self.assistant = start_assistant(config.ai, &game, &stop, &mut background);
        let paper = PaperServices {
            game: game.clone(),
            access: access.clone(),
            chat: self.chat.clone(),
            web_chat: web.as_ref().and_then(Web::chat),
            assistant: self.assistant.clone(),
            announcements: self.announcements.clone(),
            identity: identity.clone(),
            governance: self.community.clone(),
            web_api_port: web.as_ref().map_or_else(Arc::default, Web::api_port),
            web_chat_port: web.as_ref().map_or_else(Arc::default, Web::chat_port),
            qq_moderation: qq_moderation.is_some(),
        };
        if let Some(web) = web {
            let api = WebApi::new(
                game.clone(),
                access.clone(),
                paper.web_chat.clone(),
                self.announcements.clone(),
            );
            web.start(api, &stop, &mut background);
        }
        let music_slot = start_admin(&config.control_socket, &paper, &stop, &mut background);
        {
            let music = config.music;
            let stop = stop.clone();
            let game_uid = config.game_uid;
            background.spawn(async move {
                if let Err(error) = run_music(music, game_uid, &music_slot, stop).await {
                    eprintln!("mikd music unavailable until restart: {error}");
                }
            });
        }
        if let (Some(config), Some(chat), Some(identity)) =
            (config.social, self.chat.as_ref(), identity)
        {
            periodic(
                &mut background,
                &stop,
                Duration::from_secs(30),
                identity.clone(),
                |identity| async move {
                    identity.reconcile_departures().await;
                },
            );
            background.spawn({
                let stop = stop.clone();
                let runtime = social::Runtime::new(
                    chat.clone(),
                    identity,
                    directory,
                    access,
                    game,
                    self.assistant.clone(),
                );
                async move {
                    let social = social::run(config, runtime, qq_moderation);
                    if let Some(Err(error)) = stop.run_until_cancelled(social).await {
                        eprintln!("mikd social connectors stopped: {error}");
                    }
                }
            });
        }

        supervise(
            shutdown,
            &stop,
            serve_control(control, paper, stop.clone()),
            background,
        )
        .await
    }

    async fn open_core(&mut self, community: &Path, history: &Path) -> io::Result<()> {
        self.community = Some(Arc::new(
            CommunityStore::open(community)
                .await
                .map_err(io::Error::other)?,
        ));
        let history = HistoryStore::open(history)
            .await
            .map_err(io::Error::other)?;
        self.chat = Some(Arc::new(ChatService::new(history, self.community.clone())));
        Ok(())
    }

    fn maintain(&self, stop: &CancellationToken) -> JoinSet<()> {
        let mut background = JoinSet::new();
        if let Some(announcements) = &self.announcements {
            let announcements = announcements.clone();
            let stop = stop.clone();
            background.spawn(async move { announcements.maintain(&stop).await });
        }
        if let Some(chat) = &self.chat {
            periodic(
                &mut background,
                stop,
                Duration::from_mins(1),
                chat.clone(),
                |chat| async move {
                    if let Err(error) = chat.prune_history().await {
                        eprintln!("mikd history retention: {error}");
                    }
                },
            );
        }
        if let Some(community) = &self.community {
            periodic(
                &mut background,
                stop,
                Duration::from_secs(10),
                community.clone(),
                |community| async move {
                    if let Err(error) = community.advance_governance().await {
                        eprintln!("mikd governance advancement: {error}");
                    }
                },
            );
        }
        background
    }

    async fn close(self) -> io::Result<()> {
        let mut result = Ok(());
        if let AssistantState::Ready(assistant) = self.assistant.state() {
            assistant.close().await;
        }
        if let Some(chat) = self.chat {
            record(&mut result, chat.close().await);
        }
        if let Some(community) = self.community {
            record(
                &mut result,
                community.close().await.map_err(io::Error::other),
            );
        }
        result
    }
}

fn start_assistant(
    settings: Option<AiConfig>,
    game: &Arc<GameBridge>,
    stop: &CancellationToken,
    background: &mut JoinSet<()>,
) -> AssistantSlot {
    let slot = AssistantSlot::new(settings.is_some());
    if let Some(config) = settings {
        background.spawn(run_assistant(
            config,
            game.clone(),
            slot.clone(),
            stop.clone(),
        ));
    }
    slot
}

async fn run_assistant(
    config: AiConfig,
    game: Arc<GameBridge>,
    slot: AssistantSlot,
    stop: CancellationToken,
) {
    let assistant = match stop
        .run_until_cancelled(Assistant::open(config, game))
        .await
    {
        Some(Ok(assistant)) => Arc::new(assistant),
        Some(Err(error)) => {
            eprintln!("mikd AI assistant disabled until restart: {error}");
            slot.finish(None);
            return;
        }
        None => return,
    };
    slot.finish(Some(assistant.clone()));
    let mut tasks = JoinSet::new();
    periodic(
        &mut tasks,
        &stop,
        Duration::from_mins(1),
        assistant.clone(),
        |assistant| async move {
            assistant.maintain();
        },
    );
    periodic(
        &mut tasks,
        &stop,
        Duration::from_mins(10),
        assistant,
        |assistant| async move {
            assistant.refresh_wiki().await;
        },
    );
    while let Some(ended) = tasks.join_next().await {
        report_background_exit(ended);
    }
}

fn start_admin(
    control_socket: &Path,
    paper: &PaperServices,
    stop: &CancellationToken,
    background: &mut JoinSet<()>,
) -> admin::MusicSlot {
    let music_slot = admin::MusicSlot::default();
    match SocketListener::bind_admin(&admin::socket_path(control_socket)) {
        Ok(listener) => {
            let paper = Arc::new(paper.clone());
            let stop = stop.clone();
            let music = music_slot.clone();
            background.spawn(async move {
                if let Err(error) = admin::serve(listener, paper, music, stop).await {
                    eprintln!("mikd admin IPC stopped: {error}");
                }
            });
        }
        Err(error) => eprintln!("mikd admin IPC unavailable until restart: {error}"),
    }
    music_slot
}

async fn run_music(
    config: MusicStartup,
    game_uid: u32,
    slot: &admin::MusicSlot,
    stop: CancellationToken,
) -> io::Result<()> {
    let Some(music) = stop
        .run_until_cancelled(music::Service::open(config.service))
        .await
        .transpose()?
    else {
        return Ok(());
    };
    let music = Arc::new(music);
    *slot.service.write().await = Some(music.clone());
    // The admin plane remains available even if Paper's separate listener fails.
    let playback = async {
        let listener = spawn_blocking(move || {
            SocketListener::bind(&config.socket, game_uid).map_err(|error| {
                io::Error::new(
                    error.kind(),
                    format!("music IPC {}: {error}", config.socket.display()),
                )
            })
        })
        .await
        .map_err(io::Error::other)??;
        slot.paper_ready.store(true, Ordering::Relaxed);
        serve_music(listener, music.clone(), stop.clone()).await
    };
    if let Err(error) = playback.await {
        eprintln!("mikd Paper music listener: {error}");
    }
    slot.paper_ready.store(false, Ordering::Relaxed);
    stop.cancelled().await;
    // The write lock waits for admitted commands and excludes new ones before close.
    *slot.service.write().await = None;
    music.close().await
}

async fn supervise(
    shutdown: impl Future<Output = ()>,
    stop: &CancellationToken,
    control: impl Future<Output = io::Result<()>> + Send + 'static,
    mut background: JoinSet<()>,
) -> io::Result<()> {
    let mut control = tokio::spawn(control);
    tokio::pin!(shutdown);
    let ended = loop {
        tokio::select! {
            result = &mut control => break result,
            () = &mut shutdown => {
                stop.cancel();
                break control.await;
            },
            Some(ended) = background.join_next() => report_background_exit(ended),
        }
    };
    let shutting_down = stop.is_cancelled();
    stop.cancel();
    while let Some(ended) = background.join_next().await {
        report_background_exit(ended);
    }
    match ended.map_err(io::Error::other).flatten() {
        Ok(()) if shutting_down => Ok(()),
        Ok(()) => Err(io::Error::other("Paper IPC stopped unexpectedly")),
        Err(error) => Err(io::Error::new(error.kind(), format!("Paper IPC: {error}"))),
    }
}

fn report_background_exit(result: Result<(), JoinError>) {
    if let Err(error) = result {
        eprintln!("mikd background task failed: {error}");
    }
}

fn periodic<T, F>(
    tasks: &mut JoinSet<()>,
    stop: &CancellationToken,
    period: Duration,
    service: Arc<T>,
    work: impl Fn(Arc<T>) -> F + Send + 'static,
) where
    T: Send + Sync + 'static,
    F: Future<Output = ()> + Send + 'static,
{
    let stop = stop.clone();
    tasks.spawn(async move {
        let mut ticks = time::interval(period);
        ticks.set_missed_tick_behavior(MissedTickBehavior::Skip);
        let job = async move {
            loop {
                ticks.tick().await;
                work(service.clone()).await;
            }
        };
        let _ = stop.run_until_cancelled(job).await;
    });
}

fn record(result: &mut io::Result<()>, outcome: io::Result<()>) {
    if let Err(error) = outcome {
        if result.is_ok() {
            *result = Err(error);
        } else {
            eprintln!("mikd shutdown: {error}");
        }
    }
}
