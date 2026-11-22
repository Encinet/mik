mod host_functions;

use super::super::track::valid_platform;
use super::http;
use base64::{Engine, engine::general_purpose::STANDARD};
use rquickjs::{
    AsyncContext, AsyncRuntime, CatchResultExt, Ctx, Function, Object, Promise, function::Func,
};
use serde::Serialize;
use serde_json::{Value, json};
use std::{
    collections::{BTreeMap, HashMap},
    io,
    sync::{Arc, Mutex},
    time::{Duration, Instant},
};
use tokio::sync::{MutexGuard, OnceCell, OwnedSemaphorePermit, Semaphore};
use tokio_util::sync::CancellationToken;

const INIT_TIMEOUT: Duration = Duration::from_secs(5);
const JS_DEADLINE: Duration = Duration::from_secs(2);

#[derive(Eq, PartialEq)]
pub(super) struct Channel {
    pub qualities: Vec<String>,
    pub search: bool,
    pub lyrics: bool,
    pub collection: Option<CollectionRoute>,
}

/// The script opts into one HTTPS path namespace. Only its extracted opaque key is dispatched.
#[derive(Eq, PartialEq)]
pub(super) struct CollectionRoute {
    origin: url::Origin,
    prefix: String,
}

impl CollectionRoute {
    fn parse(value: &str) -> Option<Self> {
        let url = url::Url::parse(value).ok()?;
        let prefix = url.path();
        if !Self::eligible(&url)
            || prefix == "/"
            || !prefix.ends_with('/')
            || prefix.len() > 256
            || !prefix
                .bytes()
                .all(|byte| byte.is_ascii_alphanumeric() || b"/_-".contains(&byte))
        {
            return None;
        }
        Some(Self {
            origin: url.origin(),
            prefix: prefix.into(),
        })
    }

    pub fn key<'a>(&self, reference: &'a url::Url) -> Option<&'a str> {
        if !Self::eligible(reference) || self.origin != reference.origin() {
            return None;
        }
        let key = reference.path().strip_prefix(&self.prefix)?;
        (!key.is_empty()
            && key.len() <= 128
            && key
                .bytes()
                .all(|byte| byte.is_ascii_alphanumeric() || b"_-".contains(&byte)))
        .then_some(key)
    }

    fn eligible(url: &url::Url) -> bool {
        url.scheme() == "https"
            && url.domain().is_some()
            && url.port().is_none()
            && url.username().is_empty()
            && url.password().is_none()
            && url.query().is_none()
            && url.fragment().is_none()
    }
}

pub(super) struct Script {
    primary: Runtime,
    extra: Option<ExtraRuntime>,
    health: Mutex<Health>,
    pub name: String,
    pub channels: BTreeMap<String, Channel>,
}

struct Runtime {
    context: AsyncContext,
    deadline: Arc<Mutex<Instant>>,
    gate: tokio::sync::Mutex<()>,
}

struct Lease<'runtime> {
    runtime: &'runtime Runtime,
    _guard: MutexGuard<'runtime, ()>,
}

struct ExtraRuntime {
    name: String,
    script: Box<str>,
    client: reqwest::Client,
    budget: Arc<Semaphore>,
    loaded: OnceCell<Option<ResidentRuntime>>,
}

struct ResidentRuntime {
    runtime: Runtime,
    _permit: OwnedSemaphorePermit,
}

#[derive(Default)]
struct Health {
    failed: i32,
    retry: Option<Instant>,
    error: String,
}

impl Health {
    fn available(&self) -> bool {
        self.retry.is_none_or(|retry| retry <= Instant::now())
    }
}

#[derive(Serialize)]
struct Request<'action, Info> {
    source: &'action str,
    action: &'action str,
    info: Info,
}

impl Script {
    pub async fn load(
        name: &str,
        script: &str,
        client: reqwest::Client,
        extra_budget: Option<Arc<Semaphore>>,
    ) -> io::Result<Self> {
        let extra = extra_budget.map(|budget| ExtraRuntime {
            name: name.into(),
            script: script.into(),
            client: client.clone(),
            budget,
            loaded: OnceCell::new(),
        });
        let (primary, name, channels) = Runtime::load(name, script, client).await?;
        Ok(Self {
            primary,
            extra,
            name,
            channels,
            health: Mutex::default(),
        })
    }

    async fn lease(&self) -> Lease<'_> {
        if let Ok(guard) = self.primary.gate.try_lock() {
            return Lease {
                runtime: &self.primary,
                _guard: guard,
            };
        }
        if let Some(extra) = &self.extra {
            tokio::select! {
                biased;
                lease = self.primary.lease() => lease,
                Some(lease) = extra.lease(&self.channels) => lease,
            }
        } else {
            self.primary.lease().await
        }
    }

    pub async fn call(
        &self,
        source: &str,
        action: &str,
        info: impl Serialize + Send,
    ) -> io::Result<Value> {
        let lease = tokio::time::timeout(http::TIMEOUT, self.lease())
            .await
            .map_err(|_| io::Error::other("LX source busy"))?;
        {
            let mut health = self
                .health
                .lock()
                .map_err(|_| io::Error::other("LX health unavailable"))?;
            if !health.available() {
                return Err(io::Error::other("LX source is temporarily unavailable"));
            }
            if health.retry.take().is_some() {
                health.failed = 0;
            }
        }
        let request = serde_json::to_string(&Request {
            source,
            action,
            info,
        })
        .map_err(io::Error::other)?;
        let result = lease.runtime.execute(request).await;
        if let Ok(mut health) = self.health.lock() {
            match &result {
                Ok(_) => *health = Health::default(),
                Err(error) => {
                    health.error = error.to_string();
                    health.failed += 1;
                    if health.failed >= 3 {
                        health.retry = Some(Instant::now() + Duration::from_secs(60));
                    }
                }
            }
        }
        result
    }

    pub fn status(&self) -> (bool, String) {
        self.health.lock().map_or_else(
            |_| (false, "LX health unavailable".into()),
            |health| (health.available(), health.error.clone()),
        )
    }

    pub fn available(&self) -> bool {
        self.health.lock().is_ok_and(|health| health.available())
    }
}

impl ExtraRuntime {
    async fn lease(&self, expected: &BTreeMap<String, Channel>) -> Option<Lease<'_>> {
        let loaded = self.loaded.get_or_try_init(|| async {
            let permit = self.budget.clone().try_acquire_owned().map_err(|_| ())?;
            let runtime = match Runtime::load(&self.name, &self.script, self.client.clone()).await {
                Ok((runtime, _, channels)) if channels == *expected => Some(ResidentRuntime { runtime, _permit: permit }),
                Ok(_) => {
                    eprintln!("mikd LX parallel runtime declarations differ for {:?}; retaining serial execution", self.name);
                    None
                }
                Err(error) => {
                    eprintln!("mikd LX parallel runtime unavailable for {:?}: {error}", self.name);
                    None
                }
            };
            Ok::<_, ()>(runtime)
        }).await.ok()?.as_ref()?;
        Some(loaded.runtime.lease().await)
    }
}

impl Runtime {
    async fn load(
        name: &str,
        script: &str,
        client: reqwest::Client,
    ) -> io::Result<(Self, String, BTreeMap<String, Channel>)> {
        let runtime = AsyncRuntime::new().map_err(io::Error::other)?;
        runtime.set_memory_limit(64 * 1024 * 1024).await;
        runtime.set_max_stack_size(512 * 1024).await;
        let deadline = Arc::new(Mutex::new(Instant::now() + JS_DEADLINE));
        let interrupt = deadline.clone();
        runtime
            .set_interrupt_handler(Some(Box::new(move || {
                interrupt.lock().map_or(true, |end| Instant::now() >= *end)
            })))
            .await;
        let context = AsyncContext::full(&runtime)
            .await
            .map_err(io::Error::other)?;
        let info = metadata(name, script);
        let name = info["name"].as_str().unwrap_or(name).to_owned();
        let source = tokio::time::timeout(
            INIT_TIMEOUT,
            context.async_with(async |ctx| {
                let initialized = setup(&ctx, script, info.to_string(), client)
                    .catch(&ctx)
                    .map_err(js_error)?;
                initialized
                    .into_future::<String>()
                    .await
                    .catch(&ctx)
                    .map_err(js_error)
            }),
        )
        .await
        .map_err(|_| io::Error::other("LX initialization timed out"))??;
        let data: Value = serde_json::from_str(&source).map_err(io::Error::other)?;
        let channels = declarations(&data)?;
        Ok((
            Self {
                context,
                deadline,
                gate: tokio::sync::Mutex::default(),
            },
            name,
            channels,
        ))
    }

    async fn lease(&self) -> Lease<'_> {
        Lease {
            runtime: self,
            _guard: self.gate.lock().await,
        }
    }

    async fn execute(&self, request: String) -> io::Result<Value> {
        *self
            .deadline
            .lock()
            .map_err(|_| io::Error::other("LX runtime unavailable"))? =
            Instant::now() + http::TIMEOUT;
        tokio::time::timeout(
            http::TIMEOUT,
            self.context.async_with(async |ctx| {
                let promise = dispatch(&ctx, request).catch(&ctx).map_err(js_error)?;
                promise
                    .into_future::<String>()
                    .await
                    .catch(&ctx)
                    .map_err(js_error)
            }),
        )
        .await
        .map_err(|_| io::Error::other("LX request timed out"))
        .and_then(|result| result)
        .and_then(|result| {
            if result.len() > host_functions::MAX_BYTES {
                return Err(io::Error::other("LX result exceeds size limit"));
            }
            serde_json::from_str(&result).map_err(io::Error::other)
        })
    }
}

fn dispatch<'js>(ctx: &Ctx<'js>, request: String) -> rquickjs::Result<Promise<'js>> {
    let dispatch: Function = ctx.globals().get("__mikDispatch")?;
    dispatch.call((request,))
}

fn setup<'js>(
    ctx: &Ctx<'js>,
    script: &str,
    info: String,
    client: reqwest::Client,
) -> rquickjs::Result<Promise<'js>> {
    let host = Object::new(ctx.clone())?;
    host_functions::install(&host)?;
    let timers = Pending::default();
    let waiting = timers.clone();
    host.set(
        "sleep",
        Func::from(move |ctx, id: u64, delay: u64| sleep(&ctx, &waiting, id, delay)),
    )?;
    host.set("cancelTimer", Func::from(move |id| timers.cancel(id)))?;
    let requests = Pending::default();
    let pending = requests.clone();
    let limit = Arc::new(Semaphore::new(16));
    host.set(
        "request",
        Func::from(move |ctx, id, url, options| {
            request(&ctx, &pending, &limit, &client, id, url, options)
        }),
    )?;
    host.set("cancelRequest", Func::from(move |id| requests.cancel(id)))?;
    let bootstrap: Function = ctx.eval(include_str!("host.js"))?;
    let bridge: Object = bootstrap.call((host, info))?;
    let dispatch: Function = bridge.get("dispatch")?;
    ctx.globals().set("__mikDispatch", dispatch)?;
    ctx.eval::<(), _>(script)?;
    bridge.get("initialized")
}

fn js_error(error: impl std::fmt::Display) -> io::Error {
    io::Error::other(error.to_string().chars().take(512).collect::<String>())
}

fn declarations(value: &Value) -> io::Result<BTreeMap<String, Channel>> {
    if value.get("status").and_then(Value::as_bool) == Some(false) {
        return Err(io::Error::other("LX script reported failed initialization"));
    }
    let mut channels = BTreeMap::new();
    if let Some(sources) = value["sources"].as_object() {
        for (name, source) in sources.iter().take(32) {
            let name = name.to_ascii_lowercase();
            if !valid_platform(&name) || source["type"] != "music" {
                continue;
            }
            let actions = strings(&source["actions"]);
            let mut qualities = strings(&source["qualitys"])
                .into_iter()
                .map(|quality| super::metadata::quality(&quality))
                .collect::<Vec<_>>();
            if !actions.iter().any(|action| action == "musicurl") {
                qualities.clear();
            }
            let search = actions.iter().any(|action| action == "musicsearch");
            let lyrics = actions.iter().any(|action| action == "lyric");
            let collection = if actions.iter().any(|action| action == "musiccollection") {
                Some(
                    source["collectionPrefix"]
                        .as_str()
                        .and_then(CollectionRoute::parse)
                        .ok_or_else(|| io::Error::other("invalid LX collectionPrefix"))?,
                )
            } else {
                None
            };
            if !qualities.is_empty() || search || lyrics || collection.is_some() {
                channels.insert(
                    name,
                    Channel {
                        qualities,
                        search,
                        lyrics,
                        collection,
                    },
                );
            }
        }
    }
    if channels.is_empty() {
        return Err(io::Error::other(
            "LX script declares no supported music action",
        ));
    }
    Ok(channels)
}

fn strings(value: &Value) -> Vec<String> {
    value
        .as_array()
        .into_iter()
        .flatten()
        .filter_map(Value::as_str)
        .map(str::trim)
        .filter(|value| !value.is_empty() && value.len() <= 64)
        .take(32)
        .map(str::to_ascii_lowercase)
        .collect()
}

fn metadata(name: &str, script: &str) -> Value {
    let mut info = json!({"name": name, "description": "", "version": "", "author": "", "homepage": "", "rawScript": script});
    for line in script.lines().take(100) {
        let Some((tag, value)) = line
            .trim()
            .trim_start_matches('*')
            .trim()
            .strip_prefix('@')
            .and_then(|line| line.split_once(char::is_whitespace))
        else {
            continue;
        };
        if matches!(
            tag,
            "name" | "description" | "version" | "author" | "homepage"
        ) && !value.trim().is_empty()
        {
            info[tag] = Value::String(value.trim().chars().take(512).collect());
        }
    }
    info
}

#[derive(Clone, Default)]
struct Pending(Arc<Mutex<HashMap<u64, CancellationToken>>>);

struct Job(Pending, u64, CancellationToken);

impl Pending {
    fn start(&self, id: u64) -> Job {
        let stop = CancellationToken::new();
        if let Ok(mut active) = self.0.lock() {
            active.insert(id, stop.clone());
        }
        Job(self.clone(), id, stop)
    }

    fn cancel(&self, id: u64) {
        if let Ok(mut active) = self.0.lock()
            && let Some(stop) = active.remove(&id)
        {
            stop.cancel();
        }
    }
}

impl Drop for Job {
    fn drop(&mut self) {
        self.0.cancel(self.1);
    }
}

fn sleep<'js>(
    ctx: &Ctx<'js>,
    pending: &Pending,
    id: u64,
    delay: u64,
) -> rquickjs::Result<Promise<'js>> {
    let job = pending.start(id);
    Promise::wrap_future(ctx, async move {
        tokio::select! {
            () = job.2.cancelled() => {},
            () = tokio::time::sleep(Duration::from_millis(delay.min(86_400_000))) => {},
        }
        drop(job);
    })
}

fn request<'js>(
    ctx: &Ctx<'js>,
    pending: &Pending,
    limit: &Arc<Semaphore>,
    client: &reqwest::Client,
    id: u64,
    url: String,
    options: String,
) -> rquickjs::Result<Promise<'js>> {
    let job = pending.start(id);
    let permit = limit.clone().try_acquire_owned();
    let client = client.clone();
    Promise::wrap_future(ctx, async move {
        let result = match permit {
            Ok(_permit) => tokio::select! {
                () = job.2.cancelled() => Err(io::Error::other("LX request cancelled")),
                result = fetch(client, &url, &options) => result,
            },
            Err(_) => Err(io::Error::other("too many simultaneous LX HTTP requests")),
        };
        drop(job);
        result
            .unwrap_or_else(|error| json!({"error": error.to_string()}))
            .to_string()
    })
}

async fn fetch(client: reqwest::Client, url: &str, options: &str) -> io::Result<Value> {
    if options.len() > 6 * 1024 * 1024 {
        return Err(io::Error::other("LX request options exceed size limit"));
    }
    let options: Value = serde_json::from_str(options).map_err(io::Error::other)?;
    let method = options["method"]
        .as_str()
        .unwrap_or("GET")
        .to_ascii_uppercase()
        .parse()
        .map_err(io::Error::other)?;
    let mut request = client.request(method, http::address(url)?);
    if let Some(timeout) = options["timeout"].as_u64() {
        request = request.timeout(Duration::from_millis(timeout.clamp(1, 20_000)));
    }
    if let Some(headers) = options["headers"].as_object() {
        for (name, value) in headers {
            if let Some(value) = scalar(value) {
                request = request.header(name.as_str(), value);
            }
        }
    }
    if !options["body"].is_null() {
        let body = &options["body"];
        let bytes = if let Some(value) = body["__mikBytesBase64"].as_str() {
            STANDARD.decode(value).map_err(io::Error::other)?
        } else {
            scalar(body)
                .unwrap_or_else(|| body.to_string())
                .into_bytes()
        };
        if bytes.len() > host_functions::MAX_BYTES {
            return Err(io::Error::other("LX request body exceeds size limit"));
        }
        request = request.body(bytes);
    } else if let Some(form) = options["form"].as_object() {
        let body = url::form_urlencoded::Serializer::new(String::new())
            .extend_pairs(
                form.iter()
                    .filter_map(|(key, value)| scalar(value).map(|value| (key, value))),
            )
            .finish();
        if body.len() > host_functions::MAX_BYTES {
            return Err(io::Error::other("LX form body exceeds size limit"));
        }
        request = request
            .header("content-type", "application/x-www-form-urlencoded")
            .body(body);
    } else if let Some(form) = options["formData"].as_object() {
        if options["formData"].to_string().len() > host_functions::MAX_BYTES {
            return Err(io::Error::other("LX multipart body exceeds size limit"));
        }
        let form = form
            .iter()
            .fold(reqwest::multipart::Form::new(), |form, (key, value)| {
                form.text(
                    key.clone(),
                    scalar(value).unwrap_or_else(|| value.to_string()),
                )
            });
        request = request.multipart(form);
    }
    let response = http::send(request).await?;
    let status = response.status();
    let headers = response
        .headers()
        .iter()
        .filter_map(|(name, value)| {
            value
                .to_str()
                .ok()
                .map(|value| (name.to_string(), value.into()))
        })
        .collect::<serde_json::Map<String, Value>>();
    let bytes = http::body(response, host_functions::MAX_BYTES).await?;
    let body = serde_json::from_slice::<Value>(&bytes)
        .unwrap_or_else(|_| Value::String(String::from_utf8_lossy(&bytes).into_owned()));
    Ok(
        json!({"statusCode": status.as_u16(), "statusMessage": status.canonical_reason(), "headers": headers, "bytes": bytes.len(), "raw": STANDARD.encode(&bytes), "body": body}),
    )
}

fn scalar(value: &Value) -> Option<String> {
    match value {
        Value::String(text) => Some(text.clone()),
        Value::Number(_) | Value::Bool(_) => Some(value.to_string()),
        _ => None,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn music_url_aes_encodes_string_arguments_as_utf8() {
        let source = r"
            const { on, send, EVENT_NAMES, utils: { buffer, crypto } } = lx;
            on(EVENT_NAMES.request, ({ info }) => {
                switch (info) {
                    case 'cbc':
                        return buffer.bufToString(crypto.aesEncrypt(
                            '音乐', 'aes-128-cbc', 'é123456789abcde', '0123456789abcdef'), 'hex');
                    case 'ecb':
                        return buffer.bufToString(crypto.aesEncrypt(
                            buffer.from('MIK'), 'aes-128-ecb', '0123456789abcdef', null), 'hex');
                    case 'invalid-key':
                        return crypto.aesEncrypt('MIK', 'aes-128-cbc', 'short', '0123456789abcdef');
                }
            });
            send(EVENT_NAMES.inited, {
                status: true,
                sources: { demo: { type: 'music', actions: ['musicUrl'], qualitys: ['128k'] } },
            });
        ";
        let client = crate::http_egress::client_builder().build().unwrap();
        let script = Script::load("demo", source, client, None).await.unwrap();
        // Reference ciphertexts from Node's createCipheriv, which LX desktop uses.
        for (variant, expected) in [
            ("cbc", "690cd8773d7fc6b3d3bc3eec794cc068"),
            ("ecb", "9e133c5fd5053bf94692771cf8adb38e"),
        ] {
            assert_eq!(
                script.call("demo", "musicUrl", variant).await.unwrap(),
                json!(expected)
            );
        }
        let error = script
            .call("demo", "musicUrl", "invalid-key")
            .await
            .unwrap_err();
        assert!(error.to_string().contains("invalid AES key"), "{error}");
    }

    #[tokio::test(start_paused = true)]
    async fn default_preserves_script_state_while_opted_in_runtimes_overlap_waits() {
        let source = r"
            let calls = 0;
            const { on, send, EVENT_NAMES } = lx;
            on(EVENT_NAMES.request, () => new Promise(resolve => {
                const call = ++calls;
                setTimeout(() => resolve(call), 200);
            }));
            send(EVENT_NAMES.inited, {
                status: true,
                sources: { demo: { type: 'music', actions: ['musicUrl'], qualitys: ['128k'] } },
            });
        ";
        let client = crate::http_egress::client_builder().build().unwrap();
        let serial = Script::load("demo", source, client.clone(), None)
            .await
            .unwrap();
        let start = tokio::time::Instant::now();
        let (first, second) = tokio::join!(
            serial.call("demo", "musicUrl", ()),
            serial.call("demo", "musicUrl", ())
        );
        assert_eq!((first.unwrap(), second.unwrap()), (json!(1), json!(2)));
        let serial_elapsed = start.elapsed();

        let budget = Arc::new(Semaphore::new(1));
        let parallel = Script::load("demo", source, client, Some(budget.clone()))
            .await
            .unwrap();
        assert_eq!(budget.available_permits(), 1);
        let start = tokio::time::Instant::now();
        let (first, second) = tokio::join!(
            parallel.call("demo", "musicUrl", ()),
            parallel.call("demo", "musicUrl", ())
        );
        assert_eq!((first.unwrap(), second.unwrap()), (json!(1), json!(1)));
        assert!(start.elapsed() < serial_elapsed);
        assert_eq!(budget.available_permits(), 0);
        drop(parallel);
        assert_eq!(budget.available_permits(), 1);
    }
}
