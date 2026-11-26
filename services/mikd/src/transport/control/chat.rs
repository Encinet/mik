use super::reply;
use crate::domains::chat::{ChatService, Committed, HistoryError};
use mik_protocol::{
    MAX_FRAME_BYTES,
    wire::{
        ChatDeliveries, ChatPublication, ChatResult, Response, chat_result,
        response::{Code, Result as ResponseResult},
    },
};
use prost::Message;
use std::{sync::Arc, time::Duration};
use tokio::{sync::broadcast, time::timeout};

pub(super) async fn publish(
    chat: &ChatService,
    publication: ChatPublication,
    budget: Duration,
) -> Response {
    let saved = chat.publish_paper(publication);
    match timeout(budget, saved).await {
        Ok(Ok(id)) => result(chat_result::Value::PublishedId(id)),
        Ok(Err(HistoryError::RecipientUnavailable)) => reply(Code::RecipientUnavailable),
        Ok(Err(HistoryError::Conflict)) => reply(Code::RequestConflict),
        Ok(Err(HistoryError::Invalid(_) | HistoryError::AuthorizationExpired)) => {
            reply(Code::InvalidRequest)
        }
        Ok(Err(HistoryError::Busy)) | Err(_) => reply(Code::Busy),
        Ok(Err(HistoryError::Database(_) | HistoryError::Io(_))) => reply(Code::StorageFailure),
    }
}

fn result(value: chat_result::Value) -> Response {
    Response {
        code: Code::Ok.into(),
        result: Some(ResponseResult::Chat(ChatResult { value: Some(value) })),
    }
}

pub(super) struct ChatFeed<'a> {
    chat: &'a ChatService,
    live: broadcast::Receiver<Arc<Committed>>,
    pending: Option<Arc<Committed>>,
}

impl<'a> ChatFeed<'a> {
    pub(super) fn new(chat: &'a ChatService) -> Self {
        Self {
            chat,
            live: chat.subscribe(),
            pending: None,
        }
    }

    pub(super) async fn poll(&mut self, deadline: u32) -> Response {
        const BATCH_ITEMS: usize = 16;
        const BATCH_BYTES: usize = MAX_FRAME_BYTES - 128;
        let first = match self.pending.take() {
            Some(entry) => Some(entry),
            None => timeout(Duration::from_millis(u64::from(deadline).min(750)), async {
                loop {
                    match self.live.recv().await {
                        Ok(message) if message.paper_event().is_some() => return Some(message),
                        Ok(_) => {}
                        Err(broadcast::error::RecvError::Lagged(count)) => {
                            eprintln!("mikd Paper chat missed {count} live records");
                        }
                        Err(broadcast::error::RecvError::Closed) => return None,
                    }
                }
            })
            .await
            .unwrap_or_default(),
        };
        let mut entries = Vec::new();
        let mut bytes = 0;
        if let Some(first) = first {
            let mut next = Some(first);
            for _ in 0..BATCH_ITEMS {
                let message = match next.take() {
                    Some(message) => message,
                    None => match self.live.try_recv() {
                        Ok(message) => message,
                        Err(broadcast::error::TryRecvError::Lagged(count)) => {
                            eprintln!("mikd Paper chat missed {count} live records");
                            continue;
                        }
                        Err(_) => break,
                    },
                };
                let Some(event) = message.paper_event() else {
                    continue;
                };
                let delivery = self.chat.paper_delivery(event);
                let size = delivery.encoded_len() + 5;
                if size > BATCH_BYTES {
                    eprintln!("mikd Paper chat dropped an oversized live record");
                    continue;
                }
                if bytes + size > BATCH_BYTES {
                    self.pending = Some(message);
                    break;
                }
                bytes += size;
                entries.push(delivery);
            }
        }
        result(chat_result::Value::Deliveries(ChatDeliveries { entries }))
    }
}
