//! Bounded transport for Rig's Chat Completions codec. Only the configured endpoint and
//! authentication are used; neither a model result nor Rig's default routing can change them.
mod summary;

use super::diagnostics::TurnLog;
use crate::http_egress::{direct_client, parse_url};
use reqwest::{
    Client, Url,
    header::{HeaderMap, HeaderName, HeaderValue},
};
use rig_core::{
    DynModel, Model, ProviderError,
    completion::{CompletionRequest, FinishReason},
    driver::{Exchange, Opened, Opening, Transport},
    error::{ErrorKind, ErrorReport},
    message::StopReason,
    operation::Completion,
    providers::openai::{OpenAIConfig, wire::Chat},
    wire::{Body, Encoded, Mode, WireFrame},
};
use serde::{Deserialize, de::DeserializeOwned};
use serde_json::{Map, Value};
use std::{
    collections::{HashMap, HashSet},
    io,
    sync::{Arc, Mutex},
    time::Duration,
};
use tokio::sync::{OwnedSemaphorePermit, Semaphore, watch};
use tokio_util::sync::CancellationToken;

const RESERVED: &[&str] = &[
    "model",
    "messages",
    "tools",
    "tool_choice",
    "stream",
    "stream_options",
    "n",
];
pub(super) const MAX_MODEL_OUTPUT: u64 = 3072;
const MAX_MODEL_REQUEST: usize = 256 * 1024;
const MAX_MODEL_RESPONSE: usize = 2 * 1024 * 1024;
pub(in crate::domains::ai) const REQUEST_TIMEOUT: Duration = Duration::from_mins(1);
const MAX_CALLS_PER_REPLY: usize = 4;
const BUSY: &str = "mik_completion_busy";
pub(in crate::domains::ai) type AiModel = DynModel<Completion>;

pub(in crate::domains::ai) struct Models {
    pub(in crate::domains::ai) interactive: Interactive,
    pub(in crate::domains::ai) background: AiModel,
    pub(in crate::domains::ai) capacity: TurnCapacity,
}

pub(in crate::domains::ai) struct TurnCapacity(Arc<TransportSettings>);

impl TurnCapacity {
    pub(in crate::domains::ai) fn enter(&self) -> Option<OwnedSemaphorePermit> {
        let slot = self.0.turns.clone().try_acquire_owned().ok()?;
        if self.0.turns.available_permits() == 0
            && let Ok(background) = self.0.background.lock()
            && let Some(cancel) = background.as_ref()
        {
            cancel.cancel();
        }
        Some(slot)
    }

    pub(in crate::domains::ai) fn close(&self) {
        self.0.turns.close();
    }
}

#[derive(Deserialize)]
#[serde(deny_unknown_fields, rename_all = "kebab-case")]
pub(in crate::domains::ai) struct ProviderSettings {
    endpoint: String,
    model: String,
    auth_header: Option<String>,
    auth_prefix: Option<String>,
    #[serde(default)]
    headers: HashMap<String, String>,
    #[serde(default)]
    request_options: Map<String, Value>,
    #[serde(default)]
    reasoning_summaries: bool,
}

pub(super) struct Interactive {
    wire: Chat,
    settings: Arc<TransportSettings>,
    reasoning_summaries: bool,
}

impl Interactive {
    pub(super) fn for_turn(&self, summaries: Option<watch::Sender<String>>) -> AiModel {
        let summaries = summaries.filter(|_| self.reasoning_summaries);
        self.model(false, summaries)
    }

    fn model(&self, background: bool, summaries: Option<watch::Sender<String>>) -> AiModel {
        Model::new(
            self.wire.clone(),
            BoundedChat {
                wire: self.wire.clone(),
                settings: self.settings.clone(),
                background,
                summaries,
            },
        )
        .erase()
    }
}

#[derive(Clone)]
struct BoundedChat {
    wire: Chat,
    settings: Arc<TransportSettings>,
    background: bool,
    summaries: Option<watch::Sender<String>>,
}

struct TransportSettings {
    endpoint: Url,
    client: Client,
    options: Map<String, Value>,
    turns: Arc<Semaphore>,
    background: Mutex<Option<CancellationToken>>,
}

struct BackgroundCall<'a> {
    cancel: CancellationToken,
    owner: &'a Mutex<Option<CancellationToken>>,
}

impl Drop for BackgroundCall<'_> {
    fn drop(&mut self) {
        if let Ok(mut owner) = self.owner.lock() {
            *owner = None;
        }
    }
}

pub(in crate::domains::ai) fn open(
    settings: ProviderSettings,
    concurrency: usize,
    api_key: Option<&str>,
) -> io::Result<Models> {
    let endpoint = parse_url(&settings.endpoint, true)
        .filter(|_| !settings.endpoint.contains('#'))
        .ok_or_else(|| invalid("invalid AI model endpoint"))?;
    if settings.request_options.contains_key("response_format") {
        return Err(invalid("AI request-options cannot set response_format"));
    }
    if settings.model.trim().is_empty()
        || settings.model.len() > 128
        || !valid_output_cap(&settings.request_options)
        || RESERVED
            .iter()
            .any(|key| settings.request_options.contains_key(*key))
    {
        return Err(invalid("invalid AI model options or limits"));
    }
    let client = direct_client(true)
        .default_headers(provider_headers(&settings, api_key)?)
        .connect_timeout(Duration::from_secs(10))
        .timeout(REQUEST_TIMEOUT)
        .build()
        .map_err(|_| invalid("could not initialize AI HTTP client"))?;
    // The codec needs a provider configuration to encode requests. Its URL and
    // headers are not used by BoundedChat, including this placeholder credential.
    let settings_by_transport = Arc::new(TransportSettings {
        endpoint,
        client,
        options: settings.request_options,
        turns: Arc::new(Semaphore::new(concurrency)),
        background: Mutex::default(),
    });
    let interactive = Interactive {
        wire: OpenAIConfig::new("unused").chat(settings.model),
        settings: settings_by_transport.clone(),
        reasoning_summaries: settings.reasoning_summaries,
    };
    Ok(Models {
        background: interactive.model(true, None),
        interactive,
        capacity: TurnCapacity(settings_by_transport),
    })
}

pub(in crate::domains::ai) async fn decision<T: DeserializeOwned>(
    model: &AiModel,
    log: TurnLog,
    stage: &'static str,
    prompt: &str,
    input: Value,
) -> Option<T> {
    let request = CompletionRequest::new(input.to_string())
        .preamble(prompt)
        .max_tokens(512);
    match tokio::time::timeout(Duration::from_secs(5), model.call(request)).await {
        Ok(Ok(reply))
            if matches!(reply.stop(), StopReason::Stop)
                && matches!(reply.finish_reason(), None | Some(FinishReason::Stop))
                && reply.tool_calls().next().is_none() =>
        {
            serde_json::from_str(&reply.text()).ok().or_else(|| {
                log.event(stage, "invalid_decision");
                None
            })
        }
        Ok(Ok(_)) => {
            log.event(stage, "unexpected_completion");
            None
        }
        Ok(Err(error)) => {
            log.provider(stage, &error.report());
            None
        }
        Err(_) => {
            log.event(stage, "deadline_exceeded");
            None
        }
    }
}

fn provider_headers(settings: &ProviderSettings, api_key: Option<&str>) -> io::Result<HeaderMap> {
    let mut headers = HeaderMap::new();
    for (name, value) in &settings.headers {
        headers.insert(header_name(name)?, header_value(value)?);
    }
    if let Some(key) = api_key.map(str::trim).filter(|key| !key.is_empty()) {
        let name = header_name(settings.auth_header.as_deref().unwrap_or("Authorization"))?;
        let prefix = settings.auth_prefix.as_deref().unwrap_or("Bearer ");
        let mut value = header_value(&format!("{prefix}{key}"))?;
        value.set_sensitive(true);
        if headers.insert(name, value).is_some() {
            return Err(invalid("duplicate AI authorization header"));
        }
    } else if settings.auth_header.is_some() || settings.auth_prefix.is_some() {
        return Err(invalid(
            "custom AI authentication requires MIKD_MODEL_API_KEY",
        ));
    }
    Ok(headers)
}

impl Transport<Chat> for BoundedChat {
    fn send(&self, payload: Encoded, exchange: Exchange) -> Opening<WireFrame> {
        if !matches!(exchange.mode, Mode::Unary) {
            return Opening::failed(ProviderError::request(
                "MIK's agent expects a complete reply",
            ));
        }
        let Body::Bytes(bytes) = payload.request.into_body() else {
            return Opening::failed(ProviderError::request(
                "expected a Chat Completions JSON request",
            ));
        };
        let transport = self.clone();
        Opening::new(async move {
            let frame = transport.complete(&bytes).await?;
            Ok(Opened::new(futures_util::stream::iter([Ok(frame)])))
        })
    }
}

#[allow(
    clippy::result_large_err,
    reason = "Rig's Transport::send returns ProviderError"
)]
impl BoundedChat {
    async fn complete(&self, bytes: &[u8]) -> Result<WireFrame, ProviderError> {
        if self.background {
            let call = self.settings.start_background().ok_or_else(busy)?;
            return tokio::select! {
                biased;
                () = call.cancel.cancelled() => Err(busy()),
                result = self.request(bytes) => result,
            };
        }

        self.request(bytes).await
    }

    async fn request(&self, bytes: &[u8]) -> Result<WireFrame, ProviderError> {
        let streaming = self.summaries.is_some();
        let body = request_body(bytes, &self.settings.options, streaming)
            .map_err(ProviderError::request)?;
        let mut response = self
            .settings
            .client
            .post(self.settings.endpoint.clone())
            .header("Content-Type", "application/json")
            .header(
                "Accept",
                if streaming {
                    "text/event-stream, application/json"
                } else {
                    "application/json"
                },
            )
            .body(body)
            .send()
            .await
            .map_err(|error| http_failure(&error, "model request failed"))?;
        if !response.status().is_success() {
            return Err(provider_status(response.status()));
        }
        if response
            .content_length()
            .is_some_and(|size| size > MAX_MODEL_RESPONSE as u64)
        {
            return Err(failure("model response exceeds its size limit"));
        }
        if let Some(updates) = &self.summaries
            && response
                .headers()
                .get(reqwest::header::CONTENT_TYPE)
                .and_then(|header| header.to_str().ok())
                .and_then(|value| value.split(';').next())
                .is_some_and(|mime| mime.trim().eq_ignore_ascii_case("text/event-stream"))
        {
            return summary::stream(response, &self.wire, updates).await;
        }
        let mut bytes = Vec::new();
        while let Some(chunk) = response
            .chunk()
            .await
            .map_err(|error| http_failure(&error, "could not read model response"))?
        {
            if chunk.len() > MAX_MODEL_RESPONSE - bytes.len() {
                return Err(failure("model response exceeds its size limit"));
            }
            bytes.extend_from_slice(&chunk);
        }
        let reply: Value =
            serde_json::from_slice(&bytes).map_err(|_| failure("invalid model JSON"))?;
        check_reply(&reply).map_err(failure)?;
        if let Some(updates) = &self.summaries {
            summary::whole(&reply, updates);
        }
        Ok(WireFrame::from_bytes(bytes))
    }
}

impl TransportSettings {
    fn start_background(&self) -> Option<BackgroundCall<'_>> {
        let mut owner = self.background.lock().ok()?;
        if owner.is_some() || self.turns.is_closed() || self.turns.available_permits() == 0 {
            return None;
        }
        let cancel = CancellationToken::new();
        *owner = Some(cancel.clone());
        Some(BackgroundCall {
            cancel,
            owner: &self.background,
        })
    }
}

fn busy() -> ProviderError {
    let mut report = ErrorReport::new(ErrorKind::Other, "AI completion capacity reached");
    report.code = Some(BUSY.into());
    ProviderError::Relayed(Box::new(report))
}

fn request_body(
    bytes: &[u8],
    options: &Map<String, Value>,
    streaming: bool,
) -> Result<Vec<u8>, &'static str> {
    let mut body: Map<String, Value> =
        serde_json::from_slice(bytes).map_err(|_| "invalid chat request")?;
    let encoded_cap = body.remove("max_tokens");
    let completion_cap = body.remove("max_completion_tokens");
    let wire_cap = completion_cap
        .as_ref()
        .or(encoded_cap.as_ref())
        .and_then(Value::as_u64)
        .unwrap_or(MAX_MODEL_OUTPUT);
    body.extend(options.clone());
    // A provider may lower the limit and choose a compatible spelling, but cannot raise the
    // smaller budget for web-query and memory checks.
    let cap_key = if body.contains_key("max_completion_tokens") {
        "max_completion_tokens"
    } else if body.contains_key("max_tokens") || completion_cap.is_none() {
        "max_tokens"
    } else {
        "max_completion_tokens"
    };
    let cap = body
        .get(cap_key)
        .and_then(Value::as_u64)
        .map_or(wire_cap, |configured| wire_cap.min(configured));
    body.insert(cap_key.into(), cap.into());
    body.insert("stream".into(), Value::Bool(streaming));
    let body = serde_json::to_vec(&body).map_err(|_| "invalid chat request")?;
    if body.len() > MAX_MODEL_REQUEST {
        return Err("AI context exceeds its size limit");
    }
    Ok(body)
}

fn valid_output_cap(options: &Map<String, Value>) -> bool {
    let caps = [
        options.get("max_tokens"),
        options.get("max_completion_tokens"),
    ];
    !(caps[0].is_some() && caps[1].is_some())
        && caps.into_iter().flatten().all(|value| {
            value
                .as_u64()
                .is_some_and(|value| (1..=MAX_MODEL_OUTPUT).contains(&value))
        })
}

fn check_reply(reply: &Value) -> Result<(), &'static str> {
    let choice = primary_choice(reply).ok_or("model response has no primary choice")?;
    let message = choice
        .get("message")
        .and_then(Value::as_object)
        .ok_or("model response has no assistant message")?;
    check_tool_calls(message)
}

fn primary_choice(reply: &Value) -> Option<&Map<String, Value>> {
    let choices = reply.get("choices").and_then(Value::as_array)?;
    choices.iter().filter_map(Value::as_object).find(|choice| {
        choice
            .get("index")
            .and_then(Value::as_u64)
            .is_none_or(|index| index == 0)
    })
}

fn check_tool_calls(message: &Map<String, Value>) -> Result<(), &'static str> {
    let calls = match message.get("tool_calls") {
        None | Some(Value::Null) => &[][..],
        Some(Value::Array(calls)) if calls.len() <= MAX_CALLS_PER_REPLY => calls.as_slice(),
        _ => return Err("invalid or excessive model tool calls"),
    };
    let mut ids = HashSet::with_capacity(calls.len());
    for call in calls {
        let id = call
            .get("id")
            .and_then(Value::as_str)
            .filter(|id| !id.is_empty() && id.len() <= 128 && !id.chars().any(char::is_control))
            .ok_or("invalid model tool call identifier")?;
        if !ids.insert(id)
            || call.get("type").and_then(Value::as_str) != Some("function")
            || call
                .pointer("/function/name")
                .and_then(Value::as_str)
                .is_none_or(|name| name.is_empty() || name.len() > 64)
            || call
                .pointer("/function/arguments")
                .and_then(Value::as_str)
                .is_none_or(|args| args.len() > 2048)
        {
            return Err("invalid model tool call");
        }
    }
    Ok(())
}

fn http_failure(error: &reqwest::Error, message: &'static str) -> ProviderError {
    let kind = if error.is_timeout() {
        ErrorKind::Timeout
    } else {
        ErrorKind::Http
    };
    ProviderError::Relayed(Box::new(ErrorReport::new(kind, message)))
}

fn provider_status(status: reqwest::StatusCode) -> ProviderError {
    ProviderError::Relayed(Box::new(
        ErrorReport::new(
            ErrorKind::ProviderResponse,
            "AI provider returned an HTTP error",
        )
        .with_http_status(status.as_u16()),
    ))
}

fn failure(message: &'static str) -> ProviderError {
    ProviderError::Response(message.into())
}

fn invalid(message: &'static str) -> io::Error {
    io::Error::new(io::ErrorKind::InvalidInput, message)
}
fn header_name(name: &str) -> io::Result<HeaderName> {
    HeaderName::from_bytes(name.as_bytes()).map_err(|_| invalid("invalid AI HTTP header name"))
}
fn header_value(value: &str) -> io::Result<HeaderValue> {
    HeaderValue::from_str(value).map_err(|_| invalid("invalid AI HTTP header value"))
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    #[tokio::test]
    async fn streamed_provider_summary_arrives_before_the_complete_answer() {
        use axum::{
            Json,
            body::{Body, Bytes},
            response::IntoResponse,
            routing::post,
        };
        use futures_util::StreamExt;
        use tokio::sync::Notify;

        let listener = tokio::net::TcpListener::bind((std::net::Ipv4Addr::LOCALHOST, 0))
            .await
            .unwrap();
        let address = listener.local_addr().unwrap();
        let finish = Arc::new(Notify::new());
        let app = axum::Router::new().route("/chat", post({
            let finish = finish.clone();
            move |Json(request): Json<Value>| async move {
                assert_eq!(request["stream"], true);
                let frames = futures_util::stream::once(async {
                    Ok::<_, io::Error>(Bytes::from_static(concat!(
                        "data: {\"object\":\"chat.completion.chunk\",\"id\":\"1\",\"model\":\"test\",\"created\":1,",
                        "\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"reasoning\":\"private reasoning\",",
                        "\"reasoning_details\":[{\"type\":\"reasoning.text\",\"text\":\"private detail\"},",
                        "{\"type\":\"reasoning.summary\",\"index\":0,\"summary\":\"Checking the\\nrecipe.\"}]}}]}\n\n",
                    ).as_bytes()))
                }).chain(futures_util::stream::once(async move {
                    finish.notified().await;
                    Ok(Bytes::from_static(concat!(
                        "data: {\"object\":\"chat.completion.chunk\",\"id\":\"1\",\"model\":\"test\",\"created\":1,",
                        "\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Use \",\"reasoning_details\":",
                        "[{\"type\":\"reasoning.summary\",\"index\":0,\"summary\":\" Then checking the inventory.\"}]}}]}\n\n",
                        "data: {\"object\":\"chat.completion.chunk\",\"id\":\"1\",\"model\":\"test\",\"created\":1,",
                        "\"choices\":[{\"index\":0,\"delta\":{\"content\":\"iron blocks.\"},\"finish_reason\":\"stop\"}]}\n\n",
                        "data: [DONE]\n\n",
                    ).as_bytes()))
                }));
                ([("Content-Type", "text/event-stream")], Body::from_stream(frames)).into_response()
            }
        }));
        let server = tokio::spawn(async move { axum::serve(listener, app).await.unwrap() });
        let settings = serde_json::from_value(json!({
            "endpoint": format!("http://{address}/chat"),
            "model": "test",
            "reasoning-summaries": true,
        }))
        .unwrap();
        let (updates, mut progress) = watch::channel(String::new());
        let model = open(settings, 1, None)
            .unwrap()
            .interactive
            .for_turn(Some(updates));
        let result = tokio::spawn(async move {
            model
                .call(CompletionRequest::new("How can I build an elevator?"))
                .await
        });
        tokio::time::timeout(Duration::from_secs(5), progress.changed())
            .await
            .unwrap()
            .unwrap();
        assert_eq!(*progress.borrow_and_update(), "Checking the recipe.");
        assert!(!result.is_finished());
        finish.notify_one();
        let reply = result.await.unwrap().unwrap();
        assert_eq!(reply.text(), "Use iron blocks.");
        assert_eq!(
            *progress.borrow(),
            "Checking the recipe. Then checking the inventory."
        );
        server.abort();
    }

    #[tokio::test]
    async fn provider_http_failure_keeps_status_without_exposing_the_response_body() {
        let listener = tokio::net::TcpListener::bind((std::net::Ipv4Addr::LOCALHOST, 0))
            .await
            .unwrap();
        let address = listener.local_addr().unwrap();
        let app = axum::Router::new().route(
            "/chat",
            axum::routing::post(|| async {
                (
                    reqwest::StatusCode::TOO_MANY_REQUESTS,
                    "private provider payload",
                )
            }),
        );
        let server = tokio::spawn(async move { axum::serve(listener, app).await.unwrap() });
        let settings = serde_json::from_value(json!({
            "endpoint": format!("http://{address}/chat"),
            "model": "player-assistant",
        }))
        .unwrap();
        let model = open(settings, 1, None).unwrap();
        let result = model
            .interactive
            .for_turn(None)
            .call(rig_core::completion::CompletionRequest::new("test prompt"))
            .await;
        server.abort();
        let Err(error) = result else {
            panic!("HTTP failure must not produce a model answer");
        };
        let report = error.report();
        assert_eq!(report.kind, ErrorKind::ProviderResponse);
        assert_eq!(report.http_status, Some(429));
        assert!(report.provider_response.is_none());
        assert_eq!(report.message, "AI provider returned an HTTP error");
    }

    #[test]
    fn builds_optional_provider_authentication_without_duplicate_headers() {
        let mut settings: ProviderSettings = serde_json::from_value(json!({
            "endpoint": "https://model.example/v1/chat/completions",
            "model": "player-assistant",
        }))
        .unwrap();
        assert!(provider_headers(&settings, None).unwrap().is_empty());
        assert!(provider_headers(&settings, Some(" ")).unwrap().is_empty());
        let headers = provider_headers(&settings, Some("example-key")).unwrap();
        assert_eq!(headers["Authorization"], "Bearer example-key");
        assert!(headers["Authorization"].is_sensitive());

        settings.auth_header = Some("api-key".into());
        settings.auth_prefix = Some(String::new());
        assert!(provider_headers(&settings, None).is_err());
        let headers = provider_headers(&settings, Some("example-key")).unwrap();
        assert_eq!(headers["api-key"], "example-key");
        assert!(headers["api-key"].is_sensitive());
        settings.headers.insert("API-Key".into(), "conflict".into());
        assert!(provider_headers(&settings, Some("example-key")).is_err());
    }

    #[test]
    fn rejects_provider_options_that_force_an_output_format() {
        let settings = serde_json::from_value(json!({
            "endpoint": "https://model.example/v1/chat/completions",
            "model": "player-assistant",
            "request-options": {"response_format": {"type": "json_object"}}
        }))
        .unwrap();
        let error = open(settings, 1, None).err().unwrap();
        assert_eq!(error.kind(), io::ErrorKind::InvalidInput);
        assert!(error.to_string().contains("response_format"));
    }

    #[test]
    fn preserves_wire_tools_and_respects_a_lower_provider_specific_cap() {
        let raw = json!({"max_tokens":3072, "tools":[{"type":"function"}], "messages": []});
        let options = Map::from_iter([
            ("max_completion_tokens".into(), json!(1500)),
            ("temperature".into(), json!(0.2)),
        ]);
        assert!(valid_output_cap(&options));
        assert!(!valid_output_cap(&Map::from_iter([(
            "max_tokens".into(),
            json!(5000)
        )])));
        let body: Value = serde_json::from_slice(
            &request_body(&serde_json::to_vec(&raw).unwrap(), &options, false).unwrap(),
        )
        .unwrap();
        assert_eq!(
            body,
            json!({"max_completion_tokens":1500, "tools":[{"type":"function"}], "messages":[], "temperature":0.2, "stream":false})
        );

        let guard = json!({"max_tokens": 1200, "messages": []});
        let guard: Value = serde_json::from_slice(
            &request_body(&serde_json::to_vec(&guard).unwrap(), &options, false).unwrap(),
        )
        .unwrap();
        assert_eq!(guard["max_completion_tokens"], 1200);
    }

    #[test]
    fn rejects_duplicate_calls() {
        let mut reply = json!({"choices":[{"message":{"content":"test"}}]});
        let call =
            json!({"id":"c1", "type":"function", "function":{"name":"lookup","arguments":"{}"}});
        reply["choices"][0]["message"]["tool_calls"] = json!([call, call]);
        assert_eq!(check_reply(&reply), Err("invalid model tool call"));
    }
}
