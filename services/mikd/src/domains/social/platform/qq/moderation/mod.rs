mod provider;

use self::provider::{InvalidResponse, Provider, Verdict};
use crate::http_egress::direct_client;
use reqwest::{Client, Response, StatusCode, header};
use std::{
    env, io,
    sync::Mutex,
    time::{Duration, SystemTime},
};
use tokio::{
    sync::Semaphore,
    time::{Instant, timeout, timeout_at},
};

const MAX_INPUT: usize = 30_000;
const MAX_RESPONSE: usize = 65_536;
const CONNECT_TIMEOUT: Duration = Duration::from_secs(5);
const MODERATION_BUDGET: Duration = Duration::from_secs(10);
const FAILURE_COOLDOWN: Duration = Duration::from_secs(5);
const RATE_LIMIT_COOLDOWN: Duration = Duration::from_secs(60);
const INVALID_RESPONSE_COOLDOWN: Duration = Duration::from_mins(5);

pub(crate) struct QqModeration {
    pool: Pool,
    slots: Semaphore,
    reply_slot: Semaphore,
}

enum Traffic {
    Bridge,
    Reply,
}

#[derive(Clone, Copy)]
pub(in crate::domains::social::platform) enum Gate<'review> {
    Unavailable,
    Check(&'review QqModeration),
}

impl Gate<'_> {
    pub(in crate::domains::social::platform) async fn reply(self, text: &str) -> bool {
        self.allows(Traffic::Reply, text).await
    }

    pub(super) async fn bridge(self, text: &str) -> bool {
        self.allows(Traffic::Bridge, text).await
    }

    async fn allows(self, traffic: Traffic, text: &str) -> bool {
        match self {
            Self::Unavailable => false,
            Self::Check(moderation) => moderation.allows(traffic, text).await,
        }
    }
}

struct Pool {
    client: Client,
    preferred: Vec<Route>,
    fallback: Route,
}

struct Route {
    provider: Provider,
    model: &'static str,
    key: String,
    state: Mutex<Availability>,
}

enum Availability {
    Ready,
    Cooldown(Instant),
    Disabled,
}

impl QqModeration {
    /// # Errors
    /// Rejects invalid provider credentials or HTTP client setup failures.
    pub(in crate::domains::social) fn from_env() -> io::Result<Self> {
        let ovh = api_key("MIKD_QQ_OVH_API_KEY")?;
        let eden = api_key("MIKD_QQ_EDEN_API_KEY")?;
        let mut preferred = vec![Route::new(Provider::Ovh, "Qwen3Guard-Gen-8B", ovh.clone())];
        if !eden.is_empty() {
            preferred.push(Route::new(
                Provider::Eden,
                "text/moderation/ovhcloud/Qwen3Guard-Gen-8B",
                eden,
            ));
        }
        let client = direct_client(false)
            .connect_timeout(CONNECT_TIMEOUT)
            .build()
            .map_err(io::Error::other)?;
        Ok(Self {
            pool: Pool {
                client,
                preferred,
                fallback: Route::new(Provider::Ovh, "Qwen3Guard-Gen-0.6B", ovh),
            },
            slots: Semaphore::new(4),
            reply_slot: Semaphore::new(1),
        })
    }

    async fn allows(&self, traffic: Traffic, text: &str) -> bool {
        if text.len() > MAX_INPUT {
            return false;
        }
        if text.trim().is_empty() {
            return true;
        }
        let deadline = Instant::now() + MODERATION_BUDGET;
        let _reply = if matches!(traffic, Traffic::Reply) {
            let Ok(Ok(permit)) = timeout_at(deadline, self.reply_slot.acquire()).await else {
                return false;
            };
            Some(permit)
        } else {
            None
        };
        let Ok(Ok(_permit)) = timeout_at(deadline, self.slots.acquire()).await else {
            return false;
        };
        self.pool.allows(text, deadline).await
    }
}

impl Pool {
    async fn allows(&self, text: &str, deadline: Instant) -> bool {
        let mut routes: Vec<_> = self
            .preferred
            .iter()
            .filter(|route| route.available())
            .collect();
        if self.fallback.available() {
            routes.push(&self.fallback);
        }
        let Ok(route_count) = u32::try_from(routes.len()) else {
            return false;
        };
        for (route, remaining_routes) in routes.into_iter().zip((1..=route_count).rev()) {
            let remaining = deadline.saturating_duration_since(Instant::now());
            if remaining.is_zero() {
                break;
            }
            if !route.available() {
                continue;
            }
            let budget = remaining / remaining_routes;
            match timeout(budget, self.attempt(route, text)).await {
                Ok(Ok(Attempt::Verdict(Verdict::Accepted))) => {
                    return true;
                }
                Ok(Ok(Attempt::Verdict(Verdict::Rejected))) => {
                    return false;
                }
                Ok(Ok(Attempt::Http(status, retry))) => match status.as_u16() {
                    401..=404 => route.disable(),
                    408 | 425 | 500..=599 => route.cooldown(retry.unwrap_or(FAILURE_COOLDOWN)),
                    429 => route.cooldown(retry.unwrap_or(RATE_LIMIT_COOLDOWN)),
                    _ => route.cooldown(FAILURE_COOLDOWN),
                },
                Ok(Ok(Attempt::Invalid(reason))) => {
                    route.cooldown(INVALID_RESPONSE_COOLDOWN);
                    eprintln!(
                        "mikd QQ moderation invalid response (provider={}, model={}, HTTP 200): {reason}",
                        route.provider.name(),
                        route.model,
                    );
                }
                Ok(Err(_)) | Err(_) => {
                    route.cooldown(FAILURE_COOLDOWN);
                }
            }
        }
        false
    }

    async fn attempt(&self, route: &Route, text: &str) -> reqwest::Result<Attempt> {
        let provider = route.provider;
        let request = self
            .client
            .post(provider.endpoint())
            .header(header::ACCEPT, "application/json")
            .header(header::CONTENT_TYPE, "application/json; charset=utf-8")
            .body(provider.request(route.model, text).to_string());
        let request = if route.key.is_empty() {
            request
        } else {
            request.bearer_auth(&route.key)
        };
        let mut response = request.send().await?;
        if response.status() != StatusCode::OK {
            return Ok(Attempt::Http(response.status(), retry_after(&response)));
        }
        let mut body = Vec::new();
        while let Some(chunk) = response.chunk().await? {
            if chunk.len() > MAX_RESPONSE - body.len() {
                return Ok(Attempt::Invalid(InvalidResponse::TooLarge));
            }
            body.extend_from_slice(&chunk);
        }
        Ok(provider
            .verdict(&body)
            .map_or_else(Attempt::Invalid, Attempt::Verdict))
    }
}

enum Attempt {
    Verdict(Verdict),
    Http(StatusCode, Option<Duration>),
    Invalid(InvalidResponse),
}

impl Route {
    fn new(provider: Provider, model: &'static str, key: String) -> Self {
        Self {
            provider,
            model,
            key,
            state: Mutex::new(Availability::Ready),
        }
    }

    fn available(&self) -> bool {
        self.state.lock().is_ok_and(|state| match *state {
            Availability::Ready => true,
            Availability::Cooldown(until) => Instant::now() >= until,
            Availability::Disabled => false,
        })
    }

    fn cooldown(&self, delay: Duration) {
        if let Ok(mut state) = self.state.lock() {
            let until = Instant::now() + delay;
            match *state {
                Availability::Disabled => (),
                Availability::Cooldown(previous) if previous >= until => (),
                _ => *state = Availability::Cooldown(until),
            }
        }
    }

    fn disable(&self) {
        if let Ok(mut state) = self.state.lock() {
            *state = Availability::Disabled;
        }
    }
}

fn retry_after(response: &Response) -> Option<Duration> {
    let value = response
        .headers()
        .get(header::RETRY_AFTER)?
        .to_str()
        .ok()?
        .trim();
    let delay = value
        .parse::<u64>()
        .ok()
        .map(Duration::from_secs)
        .or_else(|| {
            httpdate::parse_http_date(value)
                .ok()?
                .duration_since(SystemTime::now())
                .ok()
        })?;
    (!delay.is_zero()).then_some(delay.min(Duration::from_hours(24)))
}

fn api_key(name: &str) -> io::Result<String> {
    match env::var(name) {
        Ok(value) if value.trim().bytes().all(|byte| byte.is_ascii_graphic()) => {
            Ok(value.trim().to_owned())
        }
        Err(env::VarError::NotPresent) => Ok(String::new()),
        _ => Err(io::Error::new(
            io::ErrorKind::InvalidInput,
            format!("invalid {name}"),
        )),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn unavailable_moderation_blocks_forwarded_qq_chat() {
        assert!(!Gate::Unavailable.bridge("forwarded chat").await);
    }
}
