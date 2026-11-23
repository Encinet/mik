use super::{Bot, Qq, Runtime};
use serde::Deserialize;
use std::{
    collections::BTreeSet,
    time::{Duration, Instant},
};
use tokio::{
    sync::Notify,
    time::{MissedTickBehavior, interval_at, sleep},
};

#[derive(Deserialize)]
struct Page {
    list: Vec<Member>,
    next_cursor: Option<String>,
    has_more: Option<bool>,
}

#[derive(Deserialize)]
struct Member {
    member_openid: String,
}

enum Progress {
    Next(String),
    Complete,
}

impl Page {
    fn merge(self, members: &mut BTreeSet<String>) -> Option<Progress> {
        let progress = match (self.has_more, self.next_cursor.as_deref()) {
            (Some(false), None | Some("")) | (None, Some("")) => Progress::Complete,
            (Some(true) | None, Some(next)) if !next.is_empty() => {
                Progress::Next(self.next_cursor?)
            }
            _ => return None,
        };
        for member in self.list {
            if member.member_openid.is_empty() || !members.insert(member.member_openid) {
                return None;
            }
        }
        Some(progress)
    }
}

pub(super) async fn refresh(config: &Qq, runtime: &Runtime, bot: &Bot, wakeup: &Notify) {
    let period = Duration::from_secs(120);
    let mut tick = interval_at(tokio::time::Instant::now() + period, period);
    tick.set_missed_tick_behavior(MissedTickBehavior::Delay);
    loop {
        tokio::select! {
            _ = tick.tick() => {},
            () = wakeup.notified() => tick.reset(),
        }
        for (group, conversation) in &config.groups {
            if conversation.membership || conversation.outbound.is_some() {
                let started = Instant::now();
                if let Some(members) = read_group(bot, group).await {
                    runtime.membership().replace_qq(group, started, members);
                }
                sleep(Duration::from_secs(2)).await;
            }
        }
    }
}

async fn read_group(bot: &Bot, group: &str) -> Option<BTreeSet<String>> {
    let mut cursor = None;
    let mut cursors = BTreeSet::new();
    let mut members = BTreeSet::new();
    for _ in 0..64 {
        let response = bot
            .api()
            .groups()
            .members_page(group, cursor.as_deref())
            .await
            .ok()?;
        match serde_json::from_value::<Page>(response)
            .ok()?
            .merge(&mut members)?
        {
            Progress::Complete => return Some(members),
            Progress::Next(next) if cursors.insert(next.clone()) => cursor = Some(next),
            Progress::Next(_) => return None,
        }
        sleep(Duration::from_secs(2)).await;
    }
    None
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    #[test]
    fn ambiguous_or_overlapping_roster_pages_do_not_prove_absence() {
        let mut members = BTreeSet::new();
        let first: Page = serde_json::from_value(json!({
            "list": [{"member_openid": "alice"}], "next_cursor": "second", "has_more": true
        }))
        .unwrap();
        assert!(matches!(first.merge(&mut members), Some(Progress::Next(_))));
        for response in [
            json!({"list": [], "next_cursor": "third", "has_more": false}),
            json!({"list": [], "next_cursor": "", "has_more": true}),
            json!({"list": [{"member_openid": "alice"}], "has_more": false}),
        ] {
            let page: Page = serde_json::from_value(response).unwrap();
            assert!(page.merge(&mut members).is_none());
        }
        let last: Page = serde_json::from_value(json!({"list": [], "next_cursor": ""})).unwrap();
        assert!(matches!(last.merge(&mut members), Some(Progress::Complete)));
    }
}
