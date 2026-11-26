use super::session::{Grant, Lease, WebChat};
use crate::domains::{access::Access, chat::now};
use futures_util::{StreamExt, stream};
use reqwest::{Client, StatusCode};
use serde::Deserialize;
use serde_json::json;
use std::{collections::HashMap, sync::Arc, time::Duration};
use tokio::time::{sleep, timeout};
use tokio_util::sync::CancellationToken;

const SESSION_CHECK: &str = "https://data.mcmik.top/chat/check";

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct WebsiteLease {
    player_uuid: String,
    player_name: String,
    authenticated_at: i64,
    checked_at: i64,
    expires_at: i64,
}

#[derive(Deserialize)]
struct CheckResponse {
    results: Vec<CheckResult>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct CheckResult {
    session_ref: String,
    status: u16,
    lease: Option<WebsiteLease>,
}

impl WebsiteLease {
    fn apply(self, previous: &Grant, at: i64) -> Option<Grant> {
        if previous.player_uuid != self.player_uuid
            || previous.authenticated_at != self.authenticated_at
            || self.player_name.trim().is_empty()
            || self.player_name.encode_utf16().count() > 64
            || self.player_name.chars().any(char::is_control)
            || !(at - 60_000..=at + 5_000).contains(&self.checked_at)
            || !(at + 1..=self.checked_at + 60_000).contains(&self.expires_at)
        {
            return None;
        }
        Some(Grant {
            player_name: self.player_name,
            checked_at: self.checked_at,
            expires_at: self.expires_at,
            ..previous.clone()
        })
    }
}

#[derive(Default)]
struct RenewalRetry {
    checked_at: i64,
    next: i64,
    delay_ms: i64,
}

impl RenewalRetry {
    fn ready(&self, checked_at: i64) -> i64 {
        let due = (checked_at + 30_999) / 1000 * 1000;
        if self.checked_at == checked_at {
            due.max(self.next)
        } else {
            due
        }
    }

    fn attempt(&mut self, checked_at: i64, now: i64) {
        self.delay_ms = if self.checked_at == checked_at && self.delay_ms > 0 {
            (self.delay_ms * 2).min(12_000)
        } else {
            3000
        };
        self.checked_at = checked_at;
        self.next = now + self.delay_ms + rand::random_range(0..1000);
    }
}

impl WebChat {
    pub(crate) async fn renew_leases(
        self: Arc<Self>,
        access: Arc<Access>,
        stop: CancellationToken,
    ) {
        let Ok(client) = crate::http_egress::direct_client(false)
            .timeout(Duration::from_secs(4))
            .build()
        else {
            eprintln!("mikd web chat session check client unavailable");
            return;
        };
        let mut retries = HashMap::new();
        loop {
            let at = now();
            let mut next = None;
            let leases = self.renewal_leases(at);
            retries.retain(|session, _| {
                leases
                    .iter()
                    .any(|(lease, _)| &lease.grant.session_ref == session)
            });
            let mut due = Vec::new();
            for (lease, activity) in leases {
                let grant = &lease.grant;
                let retry = retries
                    .entry(grant.session_ref.clone())
                    .or_insert_with(RenewalRetry::default);
                // Group freshly due sessions into the same one-second dispatch window.
                let ready = retry.ready(grant.checked_at);
                if ready <= at {
                    retry.attempt(grant.checked_at, at);
                    due.push((lease, activity));
                } else {
                    let wake = ready.min(grant.expires_at);
                    next = Some(next.map_or(wake, |previous: i64| previous.min(wake)));
                }
            }
            if due.is_empty() {
                let wake = async {
                    if let Some(next) = next {
                        sleep(Duration::from_millis((next - now()).max(0).unsigned_abs())).await;
                    } else {
                        std::future::pending::<()>().await;
                    }
                };
                tokio::select! { () = stop.cancelled() => return, () = self.renew_changed.notified() => {}, () = wake => {} }
                continue;
            }
            let renew = stream::iter(due.chunks(16)).for_each_concurrent(2, |batch| {
                let work = self.check_leases(&client, SESSION_CHECK, &access, batch);
                async move {
                    timeout(Duration::from_secs(6), work).await.ok();
                }
            });
            tokio::select! { () = stop.cancelled() => return, () = renew => {} }
        }
    }

    pub(super) async fn check_leases(
        &self,
        client: &Client,
        endpoint: &str,
        access: &Access,
        batch: &[(Arc<Lease>, i64)],
    ) {
        let checks: Vec<_> = batch
            .iter()
            .map(|(lease, activity)| {
                json!({
                    "sessionRef": lease.grant.session_ref,
                    "renewalToken": lease.grant.renewal_token,
                    "lastActivityAt": activity
                })
            })
            .collect();
        let body = json!({"checks": checks});
        let Ok(mut response) = client
            .post(endpoint)
            .header("content-type", "application/json")
            .body(body.to_string())
            .send()
            .await
        else {
            return;
        };
        if response.status() != StatusCode::OK {
            return;
        }
        let mut bytes = Vec::new();
        loop {
            match response.chunk().await {
                Ok(Some(chunk)) if chunk.len() <= 16 * 1024 - bytes.len() => {
                    bytes.extend_from_slice(&chunk);
                }
                Ok(None) => break,
                _ => return,
            }
        }
        let Ok(response) = serde_json::from_slice::<CheckResponse>(&bytes) else {
            return;
        };
        let mut results = HashMap::new();
        for result in response.results {
            if results.insert(result.session_ref.clone(), result).is_some() {
                return;
            }
        }
        if results.len() != batch.len()
            || batch
                .iter()
                .any(|(lease, _)| !results.contains_key(&lease.grant.session_ref))
        {
            return;
        }
        let renewals = batch.iter().filter_map(|(lease, _)| {
            let grant = &lease.grant;
            let result = results.remove(&grant.session_ref)?;
            match result.status {
                200 => Some(self.apply_check(access, grant, result.lease?)),
                403 => {
                    self.reject_renewal_proof(grant);
                    None
                }
                401 => {
                    self.revoke(vec![grant.session_ref.clone()]);
                    None
                }
                _ => None,
            }
        });
        stream::iter(renewals)
            .for_each_concurrent(16, |renew| renew)
            .await;
    }

    async fn apply_check(&self, access: &Access, grant: &Grant, facts: WebsiteLease) {
        let queried_at = now();
        let Some(updated) = facts.apply(grant, queried_at) else {
            return;
        };
        let Ok(account) = access
            .account(&updated.player_uuid, updated.player_name.clone())
            .await
        else {
            return;
        };
        if account.policy.banned
            || updated.authenticated_at <= account.policy.credentials_changed_at
        {
            self.revoke(vec![grant.session_ref.clone()]);
            return;
        }
        if now() < grant.expires_at {
            self.authorize(updated, &account.policy, account.staff(), queried_at);
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn retry_backoff_is_scoped_to_the_checked_lease() {
        let mut retry = RenewalRetry::default();
        let checked_at = 21_500;
        let mut at = retry.ready(checked_at);
        assert_eq!(at, 52_000);
        for delay in [3000, 6000, 12_000, 12_000] {
            retry.attempt(checked_at, at);
            let next = retry.ready(checked_at);
            assert!((delay..delay + 1000).contains(&(next - at)));
            at = next;
        }
        let renewed_at = 60_000;
        assert_eq!(retry.ready(renewed_at), renewed_at + 30_000);
        // Only attempt a renewal once its own dispatch window opens.
        at = retry.ready(renewed_at);
        retry.attempt(renewed_at, at);
        assert!((3000..4000).contains(&(retry.ready(renewed_at) - at)));
    }
}
