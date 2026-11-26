use super::{
    chat::{self, ChatFeed},
    reply,
    state::StateSession,
};

use crate::{
    domains::{
        access::Access,
        ai::{AssistantSlot, AssistantState},
        announcements::{Announcements, CatalogStatus},
        chat::{ChatService, web::WebChat},
        community::{CommunityStore, Error as CommunityError, IdentityService},
    },
    game::{GameBridge, Job},
};
use mik_protocol::{
    VERSION,
    wire::{
        self, ChatCommand, ConnectionRole, Request, Response,
        announcement_command::Operation as AnnouncementOperation,
        chat_command::Operation as ChatOperation,
        request::Operation,
        response::{Code, Result as ResponseResult},
        status::DomainProblem,
    },
};
use std::{
    sync::{
        Arc,
        atomic::{AtomicU16, Ordering},
    },
    time::{Duration, Instant},
};
use tokio::time::timeout;
use uuid::Uuid;

#[derive(Clone)]
pub(crate) struct PaperServices {
    pub(crate) game: Arc<GameBridge>,
    pub(crate) access: Arc<Access>,
    pub(crate) chat: Option<Arc<ChatService>>,
    pub(crate) web_chat: Option<Arc<WebChat>>,
    pub(crate) assistant: AssistantSlot,
    pub(crate) announcements: Option<Arc<Announcements>>,
    pub(crate) identity: Option<Arc<IdentityService>>,
    pub(crate) governance: Option<Arc<CommunityStore>>,
    pub(crate) web_api_port: Arc<AtomicU16>,
    pub(crate) web_chat_port: Arc<AtomicU16>,
    pub(crate) qq_moderation: bool,
}

pub(super) struct Dispatcher {
    services: PaperServices,
    status: wire::Status,
}

pub(super) struct Session<'a> {
    paper: &'a Dispatcher,
    role: ConnectionRole,
    accepts_assistant: bool,
    instance: &'a str,
    publisher: Option<StateSession<'a>>,
    game_job: Option<Job>,
    chat_feed: Option<ChatFeed<'a>>,
}

impl PaperServices {
    pub(crate) async fn readiness(&self) -> wire::Status {
        let mut status = wire::Status {
            ready_domains: [
                (self.chat.is_some(), "chat"),
                (self.qq_moderation, "qq-moderation"),
                (self.identity.is_some(), "identity"),
                (self.governance.is_some(), "governance"),
            ]
            .into_iter()
            .filter(|(enabled, _)| *enabled)
            .map(|(_, label)| label.into())
            .collect(),
            ..Default::default()
        };
        status.web_api_port = u32::from(self.web_api_port.load(Ordering::Relaxed));
        status.web_chat_port = u32::from(self.web_chat_port.load(Ordering::Relaxed));
        if status.web_api_port > 0 {
            status.ready_domains.push("web-api".into());
        }
        if status.web_chat_port > 0 {
            status.ready_domains.push("web-chat".into());
        }
        match self.assistant.state() {
            AssistantState::Ready(_) => status.ready_domains.push("assistant".into()),
            AssistantState::Starting | AssistantState::Failed => {
                status
                    .domain_problems
                    .insert("assistant".into(), DomainProblem::Unavailable.into());
            }
            AssistantState::Disabled => (),
        }
        if let Some(catalog) = &self.announcements {
            let domain = "announcements";
            let problem = match catalog.status().await {
                CatalogStatus::Ready => {
                    status.ready_domains.push(domain.into());
                    None
                }
                CatalogStatus::Stale => Some(DomainProblem::Stale),
                CatalogStatus::Unavailable => Some(DomainProblem::Unavailable),
            };
            if let Some(problem) = problem {
                status.domain_problems.insert(domain.into(), problem.into());
            }
        }
        status
    }
}

impl Dispatcher {
    pub(super) fn new(services: PaperServices) -> Self {
        let status = wire::Status {
            daemon_id: Uuid::new_v4().to_string(),
            protocol_version: VERSION,
            ..Default::default()
        };
        Self { services, status }
    }

    pub(super) fn session<'a>(
        &'a self,
        role: ConnectionRole,
        instance: &'a str,
        session: &str,
        accepts_assistant: bool,
    ) -> Session<'a> {
        let publisher = (role == ConnectionRole::PaperState).then(|| {
            StateSession::new(
                &self.services.game,
                self.services.chat.as_deref(),
                instance,
                session,
            )
        });
        let chat_feed = self
            .services
            .chat
            .as_deref()
            .filter(|_| role == ConnectionRole::ChatDownlink)
            .map(ChatFeed::new);
        Session {
            paper: self,
            role,
            accepts_assistant,
            instance,
            publisher,
            game_job: None,
            chat_feed,
        }
    }

    async fn status(&self, instance: &str) -> Response {
        let mut status = self.status.clone();
        if let Some(snapshot) = self.services.game.state.current_for(instance) {
            let Ok(player_count) = u32::try_from(snapshot.players.len()) else {
                return reply(Code::Busy);
            };
            status.snapshot_version = snapshot.version;
            status.player_count = player_count;
        }
        let readiness = self.services.readiness().await;
        status.ready_domains = readiness.ready_domains;
        status.domain_problems = readiness.domain_problems;
        status.web_api_port = readiness.web_api_port;
        status.web_chat_port = readiness.web_chat_port;
        success(ResponseResult::Status(status))
    }
}

impl Session<'_> {
    #[expect(
        clippy::too_many_lines,
        reason = "Keep IPC operation routing, role guards, and response codes together"
    )]
    pub(super) async fn handle(&mut self, request: Request) -> Response {
        let services = &self.paper.services;
        let budget = Duration::from_millis(u64::from(request.timeout_ms));
        match request.operation {
            Some(Operation::Status(_)) => self.paper.status(self.instance).await,
            Some(Operation::PaperState(next)) => self.publisher.as_mut().map_or_else(
                || reply(Code::Unsupported),
                |publisher| publisher.update(next),
            ),
            Some(Operation::GameQuery(exchange))
                if matches!(
                    self.role,
                    ConnectionRole::GameQueries | ConnectionRole::AccountQueries
                ) =>
            {
                let work = services
                    .game
                    .queries(self.role == ConnectionRole::AccountQueries)
                    .exchange(
                        self.instance,
                        exchange.reply,
                        &mut self.game_job,
                        budget,
                        self.accepts_assistant,
                    )
                    .await;
                success(ResponseResult::GameQuery(work))
            }
            Some(Operation::WebLogin(approval)) if self.role == ConnectionRole::Control => {
                let receipt = if services.web_api_port.load(Ordering::Relaxed) == 0 {
                    wire::WebLoginReceipt {
                        outcome: wire::web_login_receipt::Outcome::Unavailable.into(),
                    }
                } else {
                    services.access.approve(self.instance, approval)
                };
                success(ResponseResult::WebLogin(receipt))
            }
            Some(Operation::InvalidateWebChat(change)) if self.role == ConnectionRole::Control => {
                let Ok(id) = Uuid::parse_str(&change.player_id) else {
                    return reply(Code::InvalidRequest);
                };
                if let Some(web) = &services.web_chat {
                    web.invalidate(id.to_string());
                }
                reply(Code::Ok)
            }
            Some(Operation::Chat(ChatCommand {
                operation: Some(ChatOperation::Poll(_)),
            })) if self.role == ConnectionRole::ChatDownlink => match &mut self.chat_feed {
                Some(feed) => feed.poll(request.timeout_ms).await,
                None => reply(Code::Unsupported),
            },
            Some(Operation::Chat(ChatCommand {
                operation: Some(ChatOperation::Publish(publication)),
            })) if self.role == ConnectionRole::ChatUplink => {
                let Some(service) = &services.chat else {
                    return reply(Code::Unsupported);
                };
                chat::publish(service, publication, budget).await
            }
            Some(Operation::Governance(command)) if self.role == ConnectionRole::Governance => {
                let Some(store) = &services.governance else {
                    return reply(Code::Unsupported);
                };
                match timeout(budget, store.governance(command)).await {
                    Ok(Ok(result)) => success(ResponseResult::Governance(result)),
                    Ok(Err(CommunityError::Invalid(_))) => reply(Code::InvalidRequest),
                    Ok(Err(CommunityError::Busy)) | Err(_) => reply(Code::Busy),
                    Ok(Err(error)) => {
                        eprintln!("mikd governance: {error}");
                        reply(Code::StorageFailure)
                    }
                }
            }
            Some(Operation::Identity(command)) if self.role == ConnectionRole::Identity => {
                let Some(identity) = &services.identity else {
                    return reply(Code::Unsupported);
                };
                match timeout(budget, identity.execute(command, Instant::now() + budget)).await {
                    Ok(Ok(result)) => success(ResponseResult::Identity(result)),
                    Ok(Err(CommunityError::Invalid(_))) => reply(Code::InvalidRequest),
                    Ok(Err(CommunityError::Busy | CommunityError::Denied)) | Err(_) => {
                        reply(Code::Busy)
                    }
                    Ok(Err(error)) => {
                        eprintln!("mikd identity: {error}");
                        reply(Code::StorageFailure)
                    }
                }
            }
            Some(Operation::Assistant(command)) if self.role == ConnectionRole::General => success(
                ResponseResult::Assistant(services.assistant.state().paper(self.instance, command)),
            ),
            Some(Operation::Announcements(command)) if self.role == ConnectionRole::General => {
                let Some(announcements) = &services.announcements else {
                    return reply(Code::Unsupported);
                };
                let Some(AnnouncementOperation::Snapshot(_)) = command.operation else {
                    return reply(Code::InvalidRequest);
                };
                match timeout(budget, announcements.snapshot()).await {
                    Ok(Some(result)) => success(ResponseResult::Announcements(result)),
                    Ok(None) | Err(_) => reply(Code::Busy),
                }
            }
            _ => reply(Code::Unsupported),
        }
    }
}

fn success(result: ResponseResult) -> Response {
    Response {
        code: Code::Ok.into(),
        result: Some(result),
    }
}
