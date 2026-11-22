use super::{super::super::config::secret, Qq};
use qqbot_sdk_rs::{Bot, GroupJoinRequest, SdkError};
use serde::Deserialize;
use serde_json::{Value, json};
use std::{
    collections::{BTreeMap, HashMap},
    io,
    sync::Arc,
    time::Duration,
};
use time::{OffsetDateTime, format_description::well_known::Rfc3339};
use tokio::{
    sync::mpsc,
    time::{Instant, MissedTickBehavior, interval, sleep, sleep_until},
};
use totp_rs::{Builder, Secret, Totp};
use zeroize::Zeroizing;

pub(super) struct Approvals {
    rules: BTreeMap<String, Totp>,
    attempts: HashMap<(String, String), Attempt>,
    next_review: Instant,
}

const REVIEW_GAP: Duration = Duration::from_millis(1100);
const RETRY_DELAY: Duration = Duration::from_secs(30);
const RETAIN_ATTEMPT: Duration = Duration::from_secs(600);

struct Attempt {
    number: u32,
    finished: Instant,
    retry_at: Option<Instant>,
}

struct Pending {
    group: Arc<str>,
    observed: Instant,
    request: GroupJoinRequest,
}

#[derive(Deserialize)]
struct Page {
    list: Vec<Value>,
    next_cursor: Option<String>,
}

impl Approvals {
    pub(super) fn open(config: &Qq) -> io::Result<Self> {
        let mut rules = BTreeMap::new();
        for (group, variable) in &config.approval_secret_env {
            if !config
                .groups
                .get(group)
                .is_some_and(|conversation| conversation.membership)
            {
                return Err(io::Error::other(
                    "QQ approval requires a configured membership group",
                ));
            }
            let value = Zeroizing::new(secret(variable)?);
            let key = Secret::try_from_base32(value.as_str())
                .map_err(|_| io::Error::other("invalid QQ approval secret"))?;
            let totp = Builder::new()
                .with_secret(key)
                .with_skew(0)
                .build()
                .map_err(|_| io::Error::other("invalid QQ approval secret"))?;
            if rules.values().any(|used| used == &totp) {
                return Err(io::Error::other(
                    "QQ membership groups must not share approval secrets",
                ));
            }
            rules.insert(group.clone(), totp);
        }
        Ok(Self {
            rules,
            attempts: HashMap::new(),
            next_review: Instant::now(),
        })
    }

    pub(super) async fn run(mut self, bot: &Bot, events: mpsc::Receiver<GroupJoinRequest>) {
        let (listed, pending) = mpsc::channel(64);
        let groups = self
            .rules
            .keys()
            .map(|group| Arc::from(group.as_str()))
            .collect();
        tokio::select! {
            () = poll(bot, groups, listed) => {},
            () = self.review(bot, events, pending) => {},
        }
    }

    async fn review(
        &mut self,
        bot: &Bot,
        mut events: mpsc::Receiver<GroupJoinRequest>,
        mut pending: mpsc::Receiver<Pending>,
    ) {
        loop {
            tokio::select! {
                request = events.recv() => {
                    let Some(request) = request else { break };
                    self.apply(bot, None, request).await;
                }
                entry = pending.recv() => {
                    let Some(Pending { group, observed, request }) = entry else { break };
                    self.apply(bot, Some((&group, observed)), request).await;
                }
            }
        }
    }

    async fn apply(
        &mut self,
        bot: &Bot,
        from_list: Option<(&str, Instant)>,
        request: GroupJoinRequest,
    ) {
        let group = request
            .group_openid
            .as_deref()
            .filter(|group| !group.is_empty())
            .or(from_list.map(|(group, _)| group));
        let (Some(group), Some(member), Some(id), Some(applied)) = (
            group,
            request.member_openid.as_deref(),
            request.join_request_id.as_deref(),
            request
                .apply_at
                .as_deref()
                .and_then(|raw| OffsetDateTime::parse(raw, &Rfc3339).ok()),
        ) else {
            return;
        };
        if from_list.is_some_and(|(listed, _)| listed != group)
            || request.bot == Some(true)
            || request.auto_approved.is_some()
            || member.is_empty()
            || id.is_empty()
            || applied > OffsetDateTime::now_utc() + time::Duration::seconds(30)
        {
            return;
        }
        let (Some(rule), Ok(applied)) = (
            self.rules.get(group),
            u64::try_from(applied.unix_timestamp()),
        ) else {
            return;
        };
        let key = (group.to_owned(), id.to_owned());
        self.attempts
            .retain(|_, attempt| attempt.finished.elapsed() < RETAIN_ATTEMPT);
        let number = match self.attempts.get(&key) {
            Some(attempt) => {
                let (Some(retry_at), Some((_, observed))) = (attempt.retry_at, from_list) else {
                    return;
                };
                if observed < retry_at {
                    return;
                }
                attempt.number.saturating_add(1)
            }
            None => 1,
        };
        let approve = accepts_request(rule, applied, &request);
        let body =
            json!({ "op": if approve { "approve" } else { "decline" }, "join_request_id": id });
        sleep_until(self.next_review).await;
        let result = bot
            .api()
            .groups()
            .approve_join_request(group, member, &body)
            .await;
        let finished = Instant::now();
        self.next_review = finished + REVIEW_GAP;
        let retry_at = match result {
            Ok(_) => None,
            Err(
                SdkError::InvalidInput(_)
                | SdkError::Api {
                    status: 400 | 403 | 404,
                    ..
                },
            ) => {
                eprintln!("mikd QQ join review rejected");
                None
            }
            Err(error) => {
                if matches!(error, SdkError::Api { status: 429, .. }) {
                    self.next_review = finished + RETRY_DELAY;
                }
                if number == 1 {
                    eprintln!(
                        "mikd QQ join review not confirmed; waiting for a fresh pending list"
                    );
                }
                Some(finished + RETRY_DELAY * (1 << (number - 1).min(2)))
            }
        };
        self.attempts.insert(
            key,
            Attempt {
                number,
                finished,
                retry_at,
            },
        );
    }
}

fn accepts_request(rule: &Totp, applied: u64, request: &GroupJoinRequest) -> bool {
    let accepts = |answer: &str| {
        let answer = answer.trim();
        rule.check(answer, applied).is_some()
            || applied
                .checked_sub(30)
                .is_some_and(|earlier| rule.check(answer, earlier).is_some())
    };
    request
        .verify_info
        .as_ref()
        .is_some_and(|verification| match verification.method.as_deref() {
            Some("verify_message") => verification.verify_message.as_deref().is_some_and(accepts),
            Some("admin_review_qa") => verification
                .review_qa_list
                .iter()
                .take(10)
                .filter_map(|question| question.answer.as_deref())
                .any(accepts),
            _ => false,
        })
}

async fn poll(bot: &Bot, groups: Vec<Arc<str>>, listed: mpsc::Sender<Pending>) {
    let mut scan = interval(Duration::from_secs(30));
    scan.set_missed_tick_behavior(MissedTickBehavior::Delay);
    let mut cursors = vec![(None::<String>, 0_u8); groups.len()];
    loop {
        scan.tick().await;
        for (group, (cursor, pages)) in groups.iter().zip(&mut cursors) {
            let observed = Instant::now();
            if let Ok(payload) = bot
                .api()
                .groups()
                .join_request_list_page(group, cursor.as_deref(), Some(50))
                .await
                && let Ok(page) = serde_json::from_value::<Page>(payload)
            {
                for request in requests(page.list) {
                    if listed
                        .send(Pending {
                            group: group.clone(),
                            observed,
                            request,
                        })
                        .await
                        .is_err()
                    {
                        return;
                    }
                }
                *pages += 1;
                let next = page.next_cursor.filter(|next| !next.is_empty());
                if next.is_none() || next == *cursor || *pages >= 64 {
                    *cursor = None;
                    *pages = 0;
                } else {
                    *cursor = next;
                }
            }
            sleep(Duration::from_millis(2100)).await;
        }
    }
}

fn requests(items: Vec<Value>) -> impl Iterator<Item = GroupJoinRequest> {
    items
        .into_iter()
        .take(50)
        .filter_map(|item| serde_json::from_value(item).ok())
}

#[cfg(test)]
mod tests {
    use super::*;
    use axum::{
        Json, Router,
        http::StatusCode,
        routing::{get, post},
    };
    use qqbot_sdk_rs::ClientConfig;
    use std::{net::Ipv4Addr, sync::Mutex};

    #[tokio::test]
    async fn retries_an_unconfirmed_review_only_from_a_fresh_pending_list() {
        let totp = Builder::new()
            .with_secret(Secret::new_stack([1; 20]))
            .with_skew(0)
            .build()
            .unwrap();
        let now = OffsetDateTime::now_utc();
        let application = json!({
            "group_openid": "group", "member_openid": "member", "join_request_id": "retry",
            "apply_at": now.format(&Rfc3339).unwrap(),
            "verify_info": {
                "method": "verify_message",
                "verify_message": totp.generate(u64::try_from(now.unix_timestamp()).unwrap()).to_string()
            }
        });
        let operations = Arc::new(Mutex::new(Vec::new()));
        let recorded = operations.clone();
        let page = json!({ "list": [application.clone()] });
        let router = Router::new()
            .route(
                "/app/getAppAccessToken",
                post(|| async { Json(json!({ "access_token": "test", "expires_in": 7200 })) }),
            )
            .route(
                "/v2/groups/group/join_request_list",
                get(move || async { Json(page) }),
            )
            .route(
                "/v2/groups/group/approval_join_request/member",
                post(move |Json(body): Json<Value>| async move {
                    let mut recorded = recorded.lock().unwrap();
                    recorded.push(body);
                    if recorded.len() == 1 {
                        StatusCode::BAD_GATEWAY
                    } else {
                        StatusCode::NO_CONTENT
                    }
                }),
            );
        let listener = tokio::net::TcpListener::bind((Ipv4Addr::LOCALHOST, 0))
            .await
            .unwrap();
        let bot = Bot::with_config(
            "test",
            "test",
            ClientConfig {
                api_base_url: format!("http://{}", listener.local_addr().unwrap()),
                ..ClientConfig::default()
            },
        )
        .unwrap();
        let server = tokio::spawn(async move { axum::serve(listener, router).await.unwrap() });
        let mut approvals = Approvals {
            rules: BTreeMap::from([("group".into(), totp)]),
            attempts: HashMap::new(),
            next_review: Instant::now(),
        };
        let decode = || serde_json::from_value(application.clone()).unwrap();
        let stale = Instant::now();
        approvals.apply(&bot, None, decode()).await;
        approvals.apply(&bot, None, decode()).await;
        approvals
            .apply(&bot, Some(("group", stale)), decode())
            .await;

        let mut unrelated: GroupJoinRequest = decode();
        unrelated.join_request_id = Some("other".into());
        approvals.apply(&bot, None, unrelated).await;
        let attempt = approvals
            .attempts
            .get_mut(&("group".into(), "retry".into()))
            .unwrap();
        assert_eq!(attempt.retry_at, Some(attempt.finished + RETRY_DELAY));
        attempt.retry_at = Some(Instant::now());
        approvals.apply(&bot, None, decode()).await;
        let (listed, mut pending) = mpsc::channel(1);
        let entry = tokio::select! {
            () = poll(&bot, vec![Arc::from("group")], listed) => panic!("poll stopped"),
            entry = pending.recv() => entry.unwrap(),
            () = sleep(Duration::from_secs(5)) => panic!("pending list was not read"),
        };
        approvals
            .apply(&bot, Some((&entry.group, entry.observed)), entry.request)
            .await;
        approvals.apply(&bot, None, decode()).await;
        assert_eq!(
            *operations.lock().unwrap(),
            [
                json!({ "op": "approve", "join_request_id": "retry" }),
                json!({ "op": "approve", "join_request_id": "other" }),
                json!({ "op": "approve", "join_request_id": "retry" }),
            ]
        );
        server.abort();
    }

    #[test]
    fn malformed_application_does_not_block_valid_applications_on_the_page() {
        let page: Page = serde_json::from_value(json!({
            "list": [
                {"member_openid": 27},
                {"join_request_id": "app", "member_openid": "member", "apply_at": "2026-11-18T10:00:00Z"}
            ]
        })).unwrap();
        let mut decoded = requests(page.list);
        assert_eq!(
            decoded.next().and_then(|request| request.join_request_id),
            Some("app".into())
        );
        assert!(decoded.next().is_none());
    }
}
