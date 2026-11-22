use crate::domains::ai::reference::canonical_url;
use crate::http_egress;
use linkify::{LinkFinder, LinkKind};
use reqwest::{Client, StatusCode, Url, header};
use serde::{Deserialize, Serialize};
use std::{io, net::IpAddr, sync::Mutex, time::Duration};
use tokio::{sync::Semaphore, time::Instant};
use uuid::Uuid;

const ENDPOINT: &str = "https://api.tavily.com/search";
const TIMEOUT: Duration = Duration::from_secs(6);
const RATE_PAUSE: Duration = Duration::from_mins(1);
const QUOTA_PAUSE: Duration = Duration::from_hours(1);
const MAX_QUERY_BYTES: usize = 240;
const MAX_RESPONSE_BYTES: usize = 128 * 1024;
const MAX_RESULTS: usize = 3;

pub(in crate::domains::ai) struct WebQuery(String);

impl WebQuery {
    pub(in crate::domains::ai) fn parse(raw: &str) -> Option<Self> {
        let query = raw.trim();
        if query.len() > MAX_QUERY_BYTES
            || !query.chars().any(char::is_alphabetic)
            || raw.chars().any(char::is_control)
            || query.contains(['@', ':', '/', '\\', '<', '>', '\u{2028}', '\u{2029}'])
            || query
                .split_whitespace()
                .any(|term| term.parse::<IpAddr>().is_ok() || Uuid::try_parse(term).is_ok())
        {
            return None;
        }
        let mut finder = LinkFinder::new();
        finder.kinds(&[LinkKind::Url]).url_must_have_scheme(false);
        finder
            .links(query)
            .next()
            .is_none()
            .then(|| Self(query.into()))
    }
}

pub(in crate::domains::ai) struct Tavily {
    client: Client,
    endpoint: Url,
    authorization: header::HeaderValue,
    slots: Semaphore,
    pause: Mutex<Option<(Instant, SearchError)>>,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(in crate::domains::ai) enum SearchError {
    Busy,
    RateLimited,
    Quota,
    Unavailable,
}

#[derive(Serialize)]
struct SearchRequest<'a> {
    query: &'a str,
    search_depth: &'static str,
    max_results: usize,
    auto_parameters: bool,
    include_answer: bool,
    include_raw_content: bool,
}

#[derive(Deserialize)]
struct SearchResponse {
    results: Vec<SearchResult>,
}

#[derive(Deserialize)]
struct SearchResult {
    title: String,
    url: String,
    content: String,
}

#[derive(Serialize)]
pub(in crate::domains::ai) struct WebExcerpt {
    source: String,
    pub(in crate::domains::ai) web_url: String,
    pub(in crate::domains::ai) text: String,
}

impl Tavily {
    pub(in crate::domains::ai) fn new(api_key: &str) -> io::Result<Self> {
        Self::open(ENDPOINT, api_key)
    }

    #[cfg(test)]
    pub(in crate::domains::ai) fn with_test_endpoint(endpoint: &str) -> io::Result<Self> {
        Self::open(endpoint, "tvly-test-key")
    }

    fn open(endpoint: &str, api_key: &str) -> io::Result<Self> {
        let api_key = api_key.trim();
        let invalid_key = || {
            io::Error::new(
                io::ErrorKind::InvalidInput,
                "MIKD_TAVILY_API_KEY must be a nonempty ASCII token",
            )
        };
        if api_key.is_empty()
            || api_key.len() > 512
            || !api_key.bytes().all(|byte| byte.is_ascii_graphic())
        {
            return Err(invalid_key());
        }
        let mut authorization = header::HeaderValue::from_str(&format!("Bearer {api_key}"))
            .map_err(|_| invalid_key())?;
        authorization.set_sensitive(true);
        let endpoint = Url::parse(endpoint).map_err(io::Error::other)?;
        let client = http_egress::direct_client(false)
            .https_only(endpoint.scheme() == "https")
            .retry(reqwest::retry::never())
            .timeout(TIMEOUT)
            .build()
            .map_err(io::Error::other)?;
        Ok(Self {
            client,
            endpoint,
            authorization,
            slots: Semaphore::new(2),
            pause: Mutex::default(),
        })
    }

    pub(in crate::domains::ai) async fn search(
        &self,
        query: &WebQuery,
    ) -> Result<Vec<WebExcerpt>, SearchError> {
        let _slot = self.slots.try_acquire().map_err(|_| SearchError::Busy)?;
        let paused = *self.pause.lock().map_err(|_| SearchError::Unavailable)?;
        if let Some((until, error)) = paused
            && Instant::now() < until
        {
            return Err(error);
        }
        let body = serde_json::to_vec(&SearchRequest {
            query: &query.0,
            search_depth: "basic",
            max_results: MAX_RESULTS,
            auto_parameters: false,
            include_answer: false,
            include_raw_content: false,
        })
        .map_err(|_| SearchError::Unavailable)?;
        let mut response = self
            .client
            .post(self.endpoint.clone())
            .header(header::AUTHORIZATION, self.authorization.clone())
            .header(header::CONTENT_TYPE, "application/json")
            .body(body)
            .send()
            .await
            .map_err(|_| SearchError::Unavailable)?;
        match response.status() {
            StatusCode::TOO_MANY_REQUESTS => {
                return Err(self.cool_down(RATE_PAUSE, SearchError::RateLimited));
            }
            status if status.as_u16() == 432 => {
                return Err(self.cool_down(QUOTA_PAUSE, SearchError::Quota));
            }
            status if !status.is_success() => return Err(SearchError::Unavailable),
            _ => {}
        }
        if response
            .content_length()
            .is_some_and(|size| size > MAX_RESPONSE_BYTES as u64)
        {
            return Err(SearchError::Unavailable);
        }
        let mut bytes = Vec::new();
        while let Some(chunk) = response
            .chunk()
            .await
            .map_err(|_| SearchError::Unavailable)?
        {
            if chunk.len() > MAX_RESPONSE_BYTES - bytes.len() {
                return Err(SearchError::Unavailable);
            }
            bytes.extend_from_slice(&chunk);
        }
        let data: SearchResponse =
            serde_json::from_slice(&bytes).map_err(|_| SearchError::Unavailable)?;
        let mut excerpts: Vec<WebExcerpt> = Vec::with_capacity(MAX_RESULTS);
        for result in data.results {
            let Some(url) = canonical_url(&result.url) else {
                continue;
            };
            let text = snippet(&result.content, 1200);
            if text.trim().is_empty()
                || excerpts
                    .iter()
                    .any(|excerpt| excerpt.web_url == url.as_str())
            {
                continue;
            }
            excerpts.push(WebExcerpt {
                source: snippet(&result.title, 160),
                web_url: url.into(),
                text,
            });
            if excerpts.len() == MAX_RESULTS {
                break;
            }
        }
        Ok(excerpts)
    }

    fn cool_down(&self, duration: Duration, error: SearchError) -> SearchError {
        let until = Instant::now() + duration;
        if let Ok(mut pause) = self.pause.lock()
            && pause.is_none_or(|(current, _)| current < until)
        {
            *pause = Some((until, error));
        }
        error
    }
}

fn snippet(raw: &str, bytes: usize) -> String {
    let text = raw.trim();
    text[..text.floor_char_boundary(bytes)]
        .chars()
        .map(|character| {
            if character.is_whitespace() {
                ' '
            } else {
                character
            }
        })
        .filter(|character| !character.is_control())
        .collect()
}
