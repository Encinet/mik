use super::LiveEvent;
use crate::domains::chat::{model::Channel, service::LIVE_ITEMS};
use std::{
    collections::{BTreeMap, VecDeque},
    io,
    sync::{Arc, Mutex},
};
use tokio::sync::Notify;

/// Each channel has its own bound. A private backlog cannot evict a public or staff message.
#[derive(Default)]
pub(in crate::domains::chat::web) struct Inbox {
    queues: Mutex<Queues>,
    ready: Notify,
}

#[derive(Default)]
struct Queues {
    channels: BTreeMap<&'static str, Pending>,
    sent_gap: bool,
}

#[derive(Default)]
struct Pending {
    events: VecDeque<Arc<LiveEvent>>,
    last_missed: Option<Arc<LiveEvent>>,
}

pub(in crate::domains::chat::web) enum Item {
    Event(Arc<LiveEvent>),
    Gaps(Vec<(&'static str, Arc<LiveEvent>)>),
}

impl Inbox {
    pub(in crate::domains::chat::web) fn push(&self, channel: Channel, event: &Arc<LiveEvent>) {
        if let Ok(mut queues) = self.queues.lock() {
            let queue = queues.channels.entry(channel.name()).or_default();
            if queue.last_missed.is_some() || queue.events.len() == LIVE_ITEMS {
                // One committed event identifies both the watermark and its permission to reveal the channel.
                queue.events.clear();
                queue.last_missed = Some(Arc::clone(event));
            } else {
                queue.events.push_back(Arc::clone(event));
            }
        }
        self.ready.notify_one();
    }

    pub(in crate::domains::chat::web) async fn recv(&self) -> io::Result<Item> {
        loop {
            if let Some(item) = self.take()? {
                return Ok(item);
            }
            self.ready.notified().await;
        }
    }

    fn take(&self) -> io::Result<Option<Item>> {
        let mut queues = self
            .queues
            .lock()
            .map_err(|_| io::Error::other("chat inbox unavailable"))?;
        // Under continuous overload, alternate recovery notices with unaffected traffic.
        if queues.sent_gap
            && let Some(event) = queues.pop_event()
        {
            queues.sent_gap = false;
            return Ok(Some(Item::Event(event)));
        }
        let gaps: Vec<_> = queues
            .channels
            .iter_mut()
            .filter_map(|(&channel, queue)| Some((channel, queue.last_missed.take()?)))
            .collect();
        if gaps.is_empty() {
            return Ok(queues.pop_event().map(Item::Event));
        }
        queues.sent_gap = true;
        Ok(Some(Item::Gaps(gaps)))
    }
}

impl Queues {
    fn pop_event(&mut self) -> Option<Arc<LiveEvent>> {
        // Keep the original commit order across healthy channels.
        self.channels
            .values_mut()
            .filter_map(|queue| Some((queue.events.front()?.cursor(), queue)))
            .min_by_key(|(cursor, _)| *cursor)
            .and_then(|(_, queue)| queue.events.pop_front())
    }
}
