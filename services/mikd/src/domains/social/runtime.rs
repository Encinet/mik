use super::{
    avatar, chat, commands,
    config::Conversation,
    followups::{FollowUpKey, QuestionOffer, QuestionOffers},
    membership::Directory,
    platform::{ReplyDelivery, matrix::MatrixReply},
};
use crate::domains::{
    access::Access,
    ai::{AssistantSlot, DeliveryReceipt, ReasoningSummaries, VisibleReply},
    chat::{ChatService, Committed},
    community::IdentityService,
};
use crate::game::GameBridge;
use mik_protocol::wire::ChatExternalIdentity;
use std::{
    collections::HashMap,
    sync::{Arc, Mutex},
    time::{Duration, Instant},
};
use tokio::sync::broadcast;

type EventKey = (chat::Platform, String, String, String);

pub(crate) struct Runtime {
    pub(super) chat: Arc<ChatService>,
    pub(super) identity: Arc<IdentityService>,
    pub(super) access: Arc<Access>,
    pub(super) directory: Arc<Directory>,
    pub(super) game: Arc<GameBridge>,
    pub(super) confirmations: commands::Confirmations,
    pub(super) assistant: AssistantSlot,
    command_events: Mutex<HashMap<EventKey, Instant>>,
    question_offers: QuestionOffers,
}

pub(super) enum ReplyBody {
    Plain(String),
    Matrix(MatrixReply),
    QqMarkdown {
        markdown: serde_json::Value,
        plain: String,
        plain_visible: VisibleReply,
        offer: Option<QuestionOffer>,
    },
}

pub(super) struct CommandReply {
    pub(super) body: ReplyBody,
    pub(super) avatar: Option<Vec<u8>>,
    pub(super) receipt: Option<DeliveryReceipt>,
}

pub(super) struct Feed {
    chat: Option<broadcast::Receiver<Arc<Committed>>>,
}

impl Runtime {
    pub(crate) fn new(
        chat: Arc<ChatService>,
        identity: Arc<IdentityService>,
        directory: Arc<Directory>,
        access: Arc<Access>,
        game: Arc<GameBridge>,
        assistant: AssistantSlot,
    ) -> Self {
        Self {
            chat,
            identity,
            access,
            directory,
            game,
            assistant,
            confirmations: commands::Confirmations::default(),
            command_events: Mutex::default(),
            question_offers: QuestionOffers::default(),
        }
    }

    pub(super) fn membership(&self) -> &Directory {
        &self.directory
    }

    pub(super) fn subscribe(&self, conversation: &Conversation) -> Feed {
        Feed {
            chat: conversation
                .outbound
                .as_ref()
                .map(|_| self.chat.subscribe()),
        }
    }
    pub(super) async fn publish(&self, event: chat::Inbound, conversation: &Conversation) {
        chat::publish(&self.chat, event, conversation).await;
    }

    pub(super) async fn answer<F: Future<Output = ()>>(
        &self,
        event: &chat::Inbound,
        conversation: &Conversation,
        command: commands::Command,
        replied_author: Option<impl Future<Output = Option<ChatExternalIdentity>>>,
        delivery: ReplyDelivery<'_>,
        feedback: impl FnOnce(Option<ReasoningSummaries>) -> F,
    ) -> Option<CommandReply> {
        if !event.commands || !conversation.commands || !self.accept_command(event) {
            return None;
        }
        let response =
            commands::answer(self, event, conversation, command, replied_author, delivery).await;
        let reply = match response {
            commands::Response::Plain(reply) => reply,
            commands::Response::Assistant(turn) => match turn.answer(feedback).await {
                Ok((body, receipt)) => {
                    return Some(CommandReply {
                        body,
                        avatar: None,
                        receipt: Some(receipt),
                    });
                }
                Err(reply) => reply,
            },
        };
        let text = chat::reply_text(event.platform, &reply.text);
        if reply.dynamic && !delivery.allows_text(&text).await {
            let fallback =
                super::locale::Locale::get(&conversation.locale).text("social-reply-unavailable");
            return Some(CommandReply {
                body: ReplyBody::Plain(chat::reply_text(event.platform, &fallback)),
                avatar: None,
                receipt: None,
            });
        }
        let avatar = match reply.skin_texture {
            Some(texture) => avatar::fetch(&texture).await,
            None => None,
        };
        Some(CommandReply {
            body: ReplyBody::Plain(text),
            avatar,
            receipt: None,
        })
    }

    pub(super) fn begin_questions(&self, event: &chat::Inbound) -> Option<uuid::Uuid> {
        if event.sender.trim().is_empty() {
            return None;
        }
        self.question_offers
            .begin(event.assistant_scope(), Instant::now())
    }

    pub(super) fn offer_questions(
        &self,
        event: &chat::Inbound,
        offer: &QuestionOffer,
        keys: Vec<FollowUpKey>,
    ) -> bool {
        self.question_offers
            .offer(&event.assistant_scope(), offer, keys, Instant::now())
    }

    pub(super) fn claim_question(
        &self,
        event: &chat::Inbound,
        key: &FollowUpKey,
    ) -> Option<String> {
        self.question_offers
            .claim(&event.assistant_scope(), key, Instant::now())
    }

    fn accept_command(&self, event: &chat::Inbound) -> bool {
        let Ok(mut seen) = self.command_events.lock() else {
            return false;
        };
        let key = (
            event.platform,
            event.native.event_scope.clone(),
            event.native.source.id.clone(),
            event.native.event_id.clone(),
        );
        seen.retain(|_, at| at.elapsed() <= Duration::from_secs(300));
        if seen.len() >= 2048 {
            return false;
        }
        if let std::collections::hash_map::Entry::Vacant(entry) = seen.entry(key) {
            entry.insert(Instant::now());
            return true;
        }
        false
    }
}

impl Feed {
    pub(super) async fn recv(&mut self) -> Option<Arc<Committed>> {
        let feed = self.chat.as_mut()?;
        loop {
            match feed.recv().await {
                Ok(output) => return Some(output),
                Err(broadcast::error::RecvError::Lagged(_)) => {
                    eprintln!("mikd social output is backpressured");
                }
                Err(broadcast::error::RecvError::Closed) => return None,
            }
        }
    }
}
