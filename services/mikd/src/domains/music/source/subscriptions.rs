use super::{SourceImport, SourceRefresh, SourceState, http, lx::Script};
use futures_util::{StreamExt, stream};
use reqwest::{
    Client, StatusCode, Url,
    header::{ETAG, IF_MODIFIED_SINCE, IF_NONE_MATCH, LAST_MODIFIED},
};
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::{
    collections::{BTreeMap, HashSet},
    io::{self, BufWriter, Write},
    num::NonZeroUsize,
    path::{Path, PathBuf},
    sync::Arc,
    thread::available_parallelism,
};
use tokio::{
    fs,
    io::AsyncReadExt,
    sync::{Mutex, OwnedMutexGuard, RwLock, Semaphore},
};

const SCRIPT_LIMIT: usize = 2 * 1024 * 1024;
const SOURCE_LIMIT: usize = 32;
const PREPARE_LIMIT: usize = 4;

#[derive(Serialize, Deserialize)]
struct Saved {
    // Older snapshots derive their ID from the URL. Keep an explicit ID after a link change.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    id: Option<String>,
    #[serde(default)]
    disabled: bool,
    #[serde(default)]
    name: String,
    script: String,
    #[serde(flatten)]
    origin: Origin,
}

impl Saved {
    fn matches_id(&self, id: &str) -> bool {
        match self.id.as_deref() {
            None => source_id(&self.origin.url) == id,
            Some(saved) => {
                saved == id
                    && id.strip_prefix("remote-").is_some_and(|hex| {
                        hex.len() == 32 && hex.bytes().all(|byte| byte.is_ascii_hexdigit())
                    })
            }
        }
    }
}

#[derive(PartialEq, Serialize, Deserialize)]
struct Origin {
    url: String,
    etag: Option<String>,
    modified: Option<String>,
}

struct Subscription {
    origin: Origin,
    checksum: [u8; 32],
    disabled: bool,
}

#[derive(Serialize, Deserialize)]
#[serde(rename_all = "snake_case", tag = "state")]
enum LocalState {
    Disabled { name: String },
    Removed,
}

#[derive(Serialize, Deserialize)]
struct LocalSetting {
    id: String,
    #[serde(flatten)]
    state: LocalState,
}

enum Installed {
    Local { path: PathBuf, disabled: bool },
    Subscription(Subscription),
}

#[derive(Clone)]
struct Entry {
    name: String,
    script: Option<Arc<Script>>,
    installed: Arc<Installed>,
    error: String,
}

impl Entry {
    fn subscription(&self) -> Option<&Subscription> {
        match self.installed.as_ref() {
            Installed::Subscription(subscription) => Some(subscription),
            Installed::Local { .. } => None,
        }
    }

    fn enabled(&self) -> bool {
        match self.installed.as_ref() {
            Installed::Local { disabled, .. }
            | Installed::Subscription(Subscription { disabled, .. }) => !disabled,
        }
    }
}

struct Prepared {
    saved: Saved,
    checksum: [u8; 32],
    script: Arc<Script>,
    changed: bool,
}

type UpdateGuard = Arc<OwnedMutexGuard<()>>;

struct RuntimeSettings {
    parallel: HashSet<String>,
    extra: Arc<Semaphore>,
}

impl RuntimeSettings {
    async fn load(root: &Path) -> Self {
        let path = root.join("parallel-sources.txt");
        let configured = match read(&path, 16 * 1024).await {
            Ok(configured) => configured,
            Err(error) if error.kind() == io::ErrorKind::NotFound => String::new(),
            Err(error) => {
                eprintln!("mikd LX parallel sources {}: {error}", path.display());
                String::new()
            }
        };
        let parallel: HashSet<_> = configured
            .lines()
            .map(str::trim)
            .filter(|id| !id.is_empty())
            .map(str::to_owned)
            .collect();
        let workers = available_parallelism()
            .map_or(1, NonZeroUsize::get)
            .min(parallel.len());
        Self {
            parallel,
            extra: Arc::new(Semaphore::new(workers)),
        }
    }

    fn budget_for(&self, id: &str) -> Option<Arc<Semaphore>> {
        self.parallel.contains(id).then(|| self.extra.clone())
    }
}

pub(super) struct Sources {
    root: PathBuf,
    client: Client,
    scripts: RwLock<BTreeMap<String, Entry>>,
    updates: Arc<Mutex<()>>,
    refreshing: Mutex<()>,
    preparing: Semaphore,
    runtimes: RuntimeSettings,
}

impl Sources {
    pub async fn open(root: PathBuf) -> io::Result<Self> {
        for folder in ["local", "imports", "subscriptions", "local-state"] {
            fs::create_dir_all(root.join(folder)).await?;
        }
        let client = crate::http_egress::client_builder()
            .timeout(http::TIMEOUT)
            .no_proxy()
            .build()
            .map_err(io::Error::other)?;
        let runtimes = RuntimeSettings::load(&root).await;
        let mut scripts = BTreeMap::new();
        let local = root.join("local");
        let local_paths = files(&local, "js", 8).await?;
        for path in local_paths {
            if scripts.len() == SOURCE_LIMIT {
                break;
            }
            let name = path
                .strip_prefix(&local)
                .unwrap_or(&path)
                .to_string_lossy()
                .into_owned();
            let id = format!("local:{name}");
            let (name, disabled, loaded) = match local_state(&root, &id).await {
                Ok(Some(LocalState::Removed)) => continue,
                Ok(Some(LocalState::Disabled { name })) => (name, true, Ok(None)),
                Ok(None) => {
                    let loaded = load_local(&id, &path, &client, &runtimes).await;
                    (name, false, loaded.map(Some))
                }
                Err(error) => (name, false, Err(error)),
            };
            scripts.insert(id, entry(name, loaded, Installed::Local { path, disabled }));
        }
        let subscription_paths = files(&root.join("subscriptions"), "json", 1).await?;
        for path in subscription_paths.into_iter().take(SOURCE_LIMIT) {
            if scripts.len() == SOURCE_LIMIT {
                break;
            }
            let Some(id) = path.file_stem().and_then(|name| name.to_str()) else {
                continue;
            };
            let saved = match load_saved(&path).await {
                Ok(saved) if saved.matches_id(id) => saved,
                Ok(_) => {
                    eprintln!("ignoring misnamed LX subscription {id}");
                    continue;
                }
                Err(error) => {
                    eprintln!("cannot read LX subscription {id}: {error}");
                    continue;
                }
            };
            if source_at(&scripts, &saved.origin.url).is_some() {
                eprintln!("ignoring duplicate LX subscription {id}");
                continue;
            }
            let name = if saved.name.is_empty() {
                id.to_owned()
            } else {
                saved.name
            };
            let loaded = if saved.disabled {
                Ok(None)
            } else {
                Script::load(id, &saved.script, client.clone(), runtimes.budget_for(id))
                    .await
                    .map(Some)
            };
            let installed = Installed::Subscription(Subscription {
                checksum: Sha256::digest(&saved.script).into(),
                origin: saved.origin,
                disabled: saved.disabled,
            });
            scripts.insert(id.into(), entry(name, loaded, installed));
        }
        Ok(Self {
            root,
            client,
            scripts: RwLock::new(scripts),
            updates: Arc::default(),
            refreshing: Mutex::default(),
            preparing: Semaphore::new(PREPARE_LIMIT),
            runtimes,
        })
    }

    pub async fn drain(&self) {
        let _refresh = self.refreshing.lock().await;
        drop(self.updates.lock().await);
    }

    pub async fn active(&self) -> Vec<(String, Arc<Script>)> {
        self.scripts
            .read()
            .await
            .iter()
            .filter_map(|(id, entry)| {
                let script = entry.script.as_ref()?;
                script.available().then(|| (id.clone(), script.clone()))
            })
            .collect()
    }

    pub async fn script_for_playback(&self, id: &str) -> io::Result<Arc<Script>> {
        let scripts = self.scripts.read().await;
        let entry = scripts.get(id).ok_or_else(unknown_source)?;
        if !entry.enabled() {
            return Err(io::Error::other("LX source is disabled"));
        }
        entry
            .script
            .clone()
            .ok_or_else(|| io::Error::other("LX source could not be loaded"))
    }

    pub async fn snapshot(&self) -> Vec<SourceState> {
        self.scripts
            .read()
            .await
            .iter()
            .map(|(id, entry)| {
                let (available, failure) = entry
                    .script
                    .as_ref()
                    .map_or((false, String::new()), |script| script.status());
                let last_error = if entry.error.is_empty() {
                    failure
                } else {
                    entry.error.clone()
                };
                let actions: BTreeMap<_, _> = entry
                    .script
                    .as_ref()
                    .into_iter()
                    .flat_map(|script| &script.channels)
                    .map(|(name, channel)| {
                        let actions = [
                            (!channel.qualities.is_empty(), "musicUrl"),
                            (channel.search, "musicSearch"),
                            (channel.collection.is_some(), "musicCollection"),
                            (channel.lyrics, "lyric"),
                        ]
                        .into_iter()
                        .filter(|(supported, _)| *supported)
                        .map(|(_, action)| action.to_owned())
                        .collect();
                        (name.clone(), actions)
                    })
                    .collect();
                SourceState {
                    id: id.clone(),
                    name: entry.name.clone(),
                    available,
                    last_error,
                    channels: actions.keys().cloned().collect(),
                    actions,
                    subscription: entry.subscription().is_some(),
                    enabled: entry.enabled(),
                    origin: entry
                        .subscription()
                        .and_then(|saved| display_origin(&saved.origin.url)),
                }
            })
            .collect()
    }

    pub async fn import(self: &Arc<Self>, address: &str) -> io::Result<SourceImport> {
        let url = subscription_url(address)?;
        let (id, previous) = {
            let scripts = self.scripts.read().await;
            if let Some(id) = source_at(&scripts, url.as_str()) {
                (id.to_owned(), scripts.get(id).cloned())
            } else {
                check_capacity(&scripts)?;
                let mut id = source_id(url.as_str());
                while scripts.contains_key(&id) {
                    id = format!("remote-{}", uuid::Uuid::new_v4().simple());
                }
                (id, None)
            }
        };
        let prepared = self.prepare(&id, &url, previous.as_ref()).await?;
        let update = self.begin_update().await;
        {
            let scripts = self.scripts.read().await;
            check_installation(&scripts, &id, previous.as_ref())?;
            check_origin(&scripts, &id, url.as_str())?;
            if previous.is_none() {
                check_capacity(&scripts)?;
            }
        }
        let changed = self.install(update, &id, prepared).await?;
        Ok(SourceImport {
            id,
            added: previous.is_none(),
            changed,
        })
    }

    pub async fn update_url(self: &Arc<Self>, id: &str, address: &str) -> io::Result<()> {
        let url = subscription_url(address)?;
        let previous = {
            let scripts = self.scripts.read().await;
            let previous = subscription(&scripts, id)?;
            check_origin(&scripts, id, url.as_str())?;
            previous.clone()
        };
        let prepared = self.prepare(id, &url, Some(&previous)).await?;
        let update = self.begin_update().await;
        {
            let scripts = self.scripts.read().await;
            check_installation(&scripts, id, Some(&previous))?;
            check_origin(&scripts, id, url.as_str())?;
        }
        self.install(update, id, prepared).await?;
        Ok(())
    }

    pub async fn set_enabled(self: &Arc<Self>, id: &str, enabled: bool) -> io::Result<()> {
        let previous = self
            .scripts
            .read()
            .await
            .get(id)
            .cloned()
            .ok_or_else(unknown_source)?;
        if previous.enabled() == enabled {
            return Ok(());
        }
        let installed = match previous.installed.as_ref() {
            Installed::Local { path, .. } => {
                return self.set_local_enabled(id, path, &previous, enabled).await;
            }
            Installed::Subscription(installed) => installed,
        };
        let mut saved = load_saved(&self.path(id)).await?;
        if !saved.matches_id(id)
            || saved.origin != installed.origin
            || saved.disabled != installed.disabled
            || <[u8; 32]>::from(Sha256::digest(&saved.script)) != installed.checksum
        {
            return Err(changed_subscription());
        }
        let script = if enabled {
            let _permit = self.preparing.acquire().await.map_err(io::Error::other)?;
            Some(Arc::new(
                Script::load(
                    id,
                    &saved.script,
                    self.client.clone(),
                    self.runtimes.budget_for(id),
                )
                .await?,
            ))
        } else {
            None
        };
        saved.disabled = !enabled;
        saved.name = script
            .as_ref()
            .map_or(previous.name.clone(), |script| script.name.clone());
        let update = self.begin_update().await;
        check_installation(&*self.scripts.read().await, id, Some(&previous))?;
        self.persist_entry(update, id, saved, installed.checksum, script)
            .await
    }

    async fn set_local_enabled(
        self: &Arc<Self>,
        id: &str,
        path: &Path,
        previous: &Entry,
        enabled: bool,
    ) -> io::Result<()> {
        let script = if enabled {
            let _permit = self.preparing.acquire().await.map_err(io::Error::other)?;
            Some(load_local(id, path, &self.client, &self.runtimes).await?)
        } else {
            None
        };
        let update = self.begin_update().await;
        check_installation(&*self.scripts.read().await, id, Some(previous))?;
        let id = id.to_owned();
        let path = path.to_owned();
        let name = previous.name.clone();
        self.commit(update, move |sources| {
            let setting_path = local_state_path(&sources.root, &id);
            if enabled {
                match std::fs::remove_file(setting_path) {
                    Ok(()) => {}
                    Err(error) if error.kind() == io::ErrorKind::NotFound => {}
                    Err(error) => return Err(error),
                }
            } else {
                persist_local_state(
                    &sources.root,
                    &id,
                    LocalState::Disabled { name: name.clone() },
                )?;
            }
            let entry = entry(
                name,
                Ok(script),
                Installed::Local {
                    path,
                    disabled: !enabled,
                },
            );
            let replaced = sources.scripts.blocking_write().insert(id, entry);
            drop(replaced);
            Ok(())
        })
        .await
    }

    pub async fn remove(self: &Arc<Self>, id: &str) -> io::Result<bool> {
        let update = self.begin_update().await;
        let Some(subscription) = self
            .scripts
            .read()
            .await
            .get(id)
            .map(|entry| entry.subscription().is_some())
        else {
            return Ok(false);
        };
        let id = id.to_owned();
        self.commit(update, move |sources| {
            if subscription {
                std::fs::remove_file(sources.path(&id))?;
            } else {
                persist_local_state(&sources.root, &id, LocalState::Removed)?;
            }
            let removed = sources.scripts.blocking_write().remove(&id);
            drop(removed);
            Ok(())
        })
        .await?;
        Ok(true)
    }

    pub async fn refresh(self: &Arc<Self>, id: Option<&str>) -> io::Result<SourceRefresh> {
        let _refresh = self.refreshing.lock().await;
        let entries = self
            .scripts
            .read()
            .await
            .iter()
            .filter(|(name, entry)| {
                entry
                    .subscription()
                    .is_some_and(|saved| id.map_or(!saved.disabled, |id| *name == id))
            })
            .map(|(id, entry)| (id.clone(), entry.clone()))
            .collect::<Vec<_>>();
        if id.is_some() && entries.is_empty() {
            return Err(unknown_subscription());
        }
        let mut summary = SourceRefresh {
            total: entries.len(),
            ..Default::default()
        };
        let mut prepared = stream::iter(entries)
            .map(|(id, current)| async move {
                let result = match current
                    .subscription()
                    .map(|saved| subscription_url(&saved.origin.url))
                {
                    Some(Ok(url)) => self.prepare(&id, &url, Some(&current)).await,
                    Some(Err(error)) => Err(error),
                    None => Ok(None),
                };
                (id, current, result)
            })
            .buffer_unordered(PREPARE_LIMIT);
        while let Some((id, current, prepared)) = prepared.next().await {
            match self.finish_refresh(&id, &current, prepared).await {
                Ok(changed) => summary.changed += usize::from(changed),
                Err(_) => summary.failed_ids.push(id),
            }
        }
        summary.failed_ids.sort_unstable();
        summary.failed = summary.failed_ids.len();
        Ok(summary)
    }

    async fn begin_update(&self) -> UpdateGuard {
        Arc::new(self.updates.clone().lock_owned().await)
    }

    async fn finish_refresh(
        self: &Arc<Self>,
        id: &str,
        current: &Entry,
        prepared: io::Result<Option<Prepared>>,
    ) -> io::Result<bool> {
        let update = self.begin_update().await;
        if !same_installation(Some(current), self.scripts.read().await.get(id)) {
            return Ok(false);
        }
        let result = match prepared {
            Ok(prepared) => self.install(update.clone(), id, prepared).await,
            Err(error) => Err(error),
        };
        if let Err(error) = &result
            && let Some(entry) = self.scripts.write().await.get_mut(id)
        {
            entry.error = error.to_string();
        }
        result
    }

    async fn prepare(
        &self,
        id: &str,
        url: &Url,
        previous: Option<&Entry>,
    ) -> io::Result<Option<Prepared>> {
        let _permit = self.preparing.acquire().await.map_err(io::Error::other)?;
        let old = previous.and_then(Entry::subscription);
        let validators = old
            .filter(|old| old.origin.url == url.as_str())
            .map(|old| &old.origin);
        let downloaded = self.download(url, validators).await?;
        let mut saved = match downloaded {
            Some(saved) => saved,
            None if previous.is_some_and(|entry| entry.script.is_none()) => {
                load_saved(&self.path(id)).await?
            }
            None => return Ok(None),
        };
        let checksum = Sha256::digest(&saved.script).into();
        let changed = old.is_none_or(|old| old.checksum != checksum);
        let existing = previous
            .and_then(|entry| entry.script.as_ref())
            .filter(|_| !changed);
        if existing.is_some() && old.is_some_and(|old| old.origin == saved.origin) {
            return Ok(None);
        }
        let script = match existing {
            Some(script) => script.clone(),
            None => Arc::new(
                Script::load(
                    id,
                    &saved.script,
                    self.client.clone(),
                    self.runtimes.budget_for(id),
                )
                .await?,
            ),
        };
        saved.disabled = old.is_some_and(|old| old.disabled);
        saved.name.clone_from(&script.name);
        Ok(Some(Prepared {
            saved,
            checksum,
            script,
            changed,
        }))
    }

    async fn install(
        self: &Arc<Self>,
        update: UpdateGuard,
        id: &str,
        prepared: Option<Prepared>,
    ) -> io::Result<bool> {
        let Some(Prepared {
            saved,
            checksum,
            script,
            changed,
        }) = prepared
        else {
            self.clear_error(id).await;
            return Ok(false);
        };
        let active = (!saved.disabled).then_some(script);
        self.persist_entry(update, id, saved, checksum, active)
            .await?;
        Ok(changed)
    }

    async fn persist_entry(
        self: &Arc<Self>,
        update: UpdateGuard,
        id: &str,
        mut saved: Saved,
        checksum: [u8; 32],
        script: Option<Arc<Script>>,
    ) -> io::Result<()> {
        let path = self.path(id);
        saved.id = (source_id(&saved.origin.url) != id).then(|| id.to_owned());
        let id = id.to_owned();
        self.commit(update, move |sources| {
            persist(&path, &saved)?;
            let entry = Entry {
                name: saved.name,
                script,
                installed: Arc::new(Installed::Subscription(Subscription {
                    origin: saved.origin,
                    checksum,
                    disabled: saved.disabled,
                })),
                error: String::new(),
            };
            let replaced = sources.scripts.blocking_write().insert(id, entry);
            drop(replaced);
            Ok(())
        })
        .await
    }

    async fn commit(
        self: &Arc<Self>,
        update: UpdateGuard,
        apply: impl FnOnce(&Self) -> io::Result<()> + Send + 'static,
    ) -> io::Result<()> {
        let sources = self.clone();
        tokio::task::spawn_blocking(move || {
            let _update = update;
            apply(&sources)
        })
        .await
        .map_err(io::Error::other)?
    }

    async fn clear_error(&self, id: &str) {
        if let Some(entry) = self.scripts.write().await.get_mut(id) {
            entry.error.clear();
        }
    }

    async fn download(&self, url: &Url, previous: Option<&Origin>) -> io::Result<Option<Saved>> {
        let (bytes, etag, modified) = if url.scheme() == "file" {
            let path = url
                .to_file_path()
                .map_err(|()| io::Error::other("invalid LX file URL"))?;
            let imports = fs::canonicalize(self.root.join("imports")).await?;
            let path = fs::canonicalize(path).await?;
            if !path.starts_with(imports) {
                return Err(io::Error::other(
                    "LX file URL must be inside the imports directory",
                ));
            }
            (read(&path, SCRIPT_LIMIT).await?.into_bytes(), None, None)
        } else {
            let mut request = self
                .client
                .get(url.clone())
                .header("user-agent", "MIK-Music/1.0");
            if let Some(etag) = previous.and_then(|saved| saved.etag.as_deref()) {
                request = request.header(IF_NONE_MATCH, etag);
            }
            if let Some(modified) = previous.and_then(|saved| saved.modified.as_deref()) {
                request = request.header(IF_MODIFIED_SINCE, modified);
            }
            let response = http::send(request).await?;
            if response.status() == StatusCode::NOT_MODIFIED && previous.is_some() {
                return Ok(None);
            }
            let response = http::success(response)?;
            let etag = response
                .headers()
                .get(ETAG)
                .and_then(|value| value.to_str().ok())
                .map(str::to_owned);
            let modified = response
                .headers()
                .get(LAST_MODIFIED)
                .and_then(|value| value.to_str().ok())
                .map(str::to_owned);
            (http::body(response, SCRIPT_LIMIT).await?, etag, modified)
        };
        let script = String::from_utf8(bytes).map_err(io::Error::other)?;
        Ok(Some(Saved {
            id: None,
            disabled: false,
            name: String::new(),
            script,
            origin: Origin {
                url: url.as_str().into(),
                etag,
                modified,
            },
        }))
    }

    fn path(&self, id: &str) -> PathBuf {
        self.root.join("subscriptions").join(format!("{id}.json"))
    }
}

fn unknown_subscription() -> io::Error {
    io::Error::new(io::ErrorKind::NotFound, "unknown LX subscription ID")
}

pub(super) fn unknown_source() -> io::Error {
    io::Error::new(io::ErrorKind::NotFound, "unknown LX source ID")
}

fn same_installation(expected: Option<&Entry>, current: Option<&Entry>) -> bool {
    match (expected, current) {
        (None, None) => true,
        (Some(expected), Some(current)) => Arc::ptr_eq(&expected.installed, &current.installed),
        _ => false,
    }
}

fn subscription<'a>(scripts: &'a BTreeMap<String, Entry>, id: &str) -> io::Result<&'a Entry> {
    scripts
        .get(id)
        .filter(|entry| entry.subscription().is_some())
        .ok_or_else(unknown_subscription)
}

fn source_at<'a>(scripts: &'a BTreeMap<String, Entry>, url: &str) -> Option<&'a str> {
    scripts.iter().find_map(|(id, entry)| {
        entry
            .subscription()
            .is_some_and(|saved| saved.origin.url == url)
            .then_some(id.as_str())
    })
}

fn check_origin(scripts: &BTreeMap<String, Entry>, id: &str, url: &str) -> io::Result<()> {
    if let Some(existing) = source_at(scripts, url).filter(|existing| *existing != id) {
        return Err(io::Error::new(
            io::ErrorKind::AlreadyExists,
            format!("URL already belongs to LX source {existing}"),
        ));
    }
    Ok(())
}

fn changed_subscription() -> io::Error {
    io::Error::new(
        io::ErrorKind::WouldBlock,
        "LX source changed; inspect it before retrying",
    )
}

fn check_installation(
    scripts: &BTreeMap<String, Entry>,
    id: &str,
    previous: Option<&Entry>,
) -> io::Result<()> {
    if same_installation(previous, scripts.get(id)) {
        Ok(())
    } else {
        Err(changed_subscription())
    }
}

fn check_capacity(scripts: &BTreeMap<String, Entry>) -> io::Result<()> {
    if scripts.len() >= SOURCE_LIMIT {
        Err(io::Error::other("too many LX sources"))
    } else {
        Ok(())
    }
}

fn display_origin(address: &str) -> Option<String> {
    let mut url = Url::parse(address).ok()?;
    if !url.username().is_empty() || url.password().is_some() {
        url.set_username("").ok()?;
        url.set_password(None).ok()?;
    }
    url.set_query(None);
    url.set_fragment(None);
    Some(url.into())
}

fn entry(name: String, script: io::Result<Option<Script>>, installed: Installed) -> Entry {
    let installed = Arc::new(installed);
    match script {
        Ok(script) => Entry {
            name: script.as_ref().map_or(name, |script| script.name.clone()),
            script: script.map(Arc::new),
            installed,
            error: String::new(),
        },
        Err(error) => Entry {
            name,
            script: None,
            installed,
            error: error.to_string(),
        },
    }
}

fn subscription_url(address: &str) -> io::Result<Url> {
    let address = address.trim();
    if address.len() > 4192 {
        return Err(io::Error::other("LX subscription URL too long"));
    }
    let mut url = if address.starts_with("file:") {
        Url::parse(address).map_err(io::Error::other)?
    } else {
        http::address(address)?
    };
    url.set_fragment(None);
    Ok(url)
}

fn source_id(url: &str) -> String {
    let digest = Sha256::digest(url);
    format!("remote-{}", hex::encode(&digest[..16]))
}

fn local_state_path(root: &Path, id: &str) -> PathBuf {
    let digest = Sha256::digest(id.as_bytes());
    root.join("local-state")
        .join(format!("{}.json", hex::encode(digest)))
}

async fn local_state(root: &Path, id: &str) -> io::Result<Option<LocalState>> {
    let text = match read(&local_state_path(root, id), 64 * 1024).await {
        Ok(text) => text,
        Err(error) if error.kind() == io::ErrorKind::NotFound => return Ok(None),
        Err(error) => return Err(error),
    };
    let setting: LocalSetting = serde_json::from_str(&text).map_err(io::Error::other)?;
    if setting.id != id {
        return Err(io::Error::other("LX local source state has the wrong ID"));
    }
    Ok(Some(setting.state))
}

fn persist_local_state(root: &Path, id: &str, state: LocalState) -> io::Result<()> {
    persist(
        &local_state_path(root, id),
        &LocalSetting {
            id: id.into(),
            state,
        },
    )
}

async fn load_local(
    id: &str,
    path: &Path,
    client: &Client,
    runtimes: &RuntimeSettings,
) -> io::Result<Script> {
    let name = id.strip_prefix("local:").unwrap_or(id);
    let text = read(path, SCRIPT_LIMIT).await?;
    Script::load(name, &text, client.clone(), runtimes.budget_for(id)).await
}

async fn files(root: &Path, extension: &str, max_depth: usize) -> io::Result<Vec<PathBuf>> {
    let root = root.to_owned();
    let extension = extension.to_owned();
    tokio::task::spawn_blocking(move || {
        walkdir::WalkDir::new(root)
            .max_depth(max_depth)
            .sort_by_file_name()
            .into_iter()
            .filter_map(Result::ok)
            .filter(|entry| {
                entry.file_type().is_file()
                    && entry
                        .path()
                        .extension()
                        .is_some_and(|ext| ext == extension.as_str())
            })
            .map(walkdir::DirEntry::into_path)
            .collect::<Vec<_>>()
    })
    .await
    .map_err(io::Error::other)
}

async fn read(path: &Path, limit: usize) -> io::Result<String> {
    if !fs::metadata(path).await?.is_file() {
        return Err(io::Error::other("LX input must be a regular file"));
    }
    let mut bytes = Vec::new();
    fs::File::open(path)
        .await?
        .take((limit + 1) as u64)
        .read_to_end(&mut bytes)
        .await?;
    if bytes.len() > limit {
        return Err(io::Error::other("LX input exceeds size limit"));
    }
    String::from_utf8(bytes).map_err(io::Error::other)
}

async fn load_saved(path: &Path) -> io::Result<Saved> {
    let saved: Saved = serde_json::from_str(&read(path, SCRIPT_LIMIT * 6 + 16_384).await?)
        .map_err(io::Error::other)?;
    if saved.script.len() > SCRIPT_LIMIT {
        return Err(io::Error::other("LX script exceeds size limit"));
    }
    Ok(saved)
}

fn persist(path: &Path, saved: &impl Serialize) -> io::Result<()> {
    let mut file = tempfile::NamedTempFile::new_in(
        path.parent()
            .ok_or_else(|| io::Error::other("invalid LX state path"))?,
    )?;
    {
        let mut writer = BufWriter::new(file.as_file_mut());
        serde_json::to_writer(&mut writer, saved).map_err(io::Error::other)?;
        writer.flush()?;
    }
    file.as_file().sync_all()?;
    file.persist(path).map_err(io::Error::other)?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::time::Duration;
    use tokio::time::{sleep, timeout};

    const DEMO: &str = r"
        const { on, send, EVENT_NAMES } = lx;
        on(EVENT_NAMES.request, () => 'https://example.com/audio');
        send(EVENT_NAMES.inited, {
            status: true,
            sources: { demo: { type: 'music', actions: ['musicUrl'], qualitys: ['128k'] } },
        });
    ";

    #[tokio::test]
    async fn only_exact_ids_in_the_source_allowlist_get_a_parallel_runtime_budget() {
        let directory = tempfile::tempdir().unwrap();
        let root = directory.path();
        assert!(
            RuntimeSettings::load(root)
                .await
                .budget_for("local:demo.js")
                .is_none()
        );
        fs::write(root.join("parallel-sources.txt"), "\n local:demo.js\n")
            .await
            .unwrap();
        let settings = RuntimeSettings::load(root).await;
        assert_eq!(
            settings
                .budget_for("local:demo.js")
                .unwrap()
                .available_permits(),
            1
        );
        assert!(settings.budget_for("local:other.js").is_none());
    }

    #[tokio::test]
    async fn prepared_refresh_cannot_overwrite_an_update_or_reinstall_a_deleted_source() {
        let directory = tempfile::tempdir().unwrap();
        let sources = Arc::new(Sources::open(directory.path().to_owned()).await.unwrap());
        let script = directory.path().join("imports/demo.js");
        fs::write(&script, DEMO).await.unwrap();
        let url = Url::from_file_path(&script).unwrap();
        let id = sources.import(url.as_str()).await.unwrap().id;
        let first = sources.scripts.read().await.get(&id).unwrap().clone();
        fs::write(&script, DEMO.replace("/audio", "/stale"))
            .await
            .unwrap();
        let prepared = sources.prepare(&id, &url, Some(&first)).await.unwrap();
        assert!(prepared.is_some());
        fs::write(&script, DEMO.replace("/audio", "/new"))
            .await
            .unwrap();
        assert!(sources.import(url.as_str()).await.unwrap().changed);
        assert!(
            !sources
                .finish_refresh(&id, &first, Ok(prepared))
                .await
                .unwrap()
        );
        assert!(
            load_saved(&sources.path(&id))
                .await
                .unwrap()
                .script
                .contains("/new")
        );

        let current = sources.scripts.read().await.get(&id).unwrap().clone();
        fs::write(&script, DEMO).await.unwrap();
        let prepared = sources.prepare(&id, &url, Some(&current)).await.unwrap();
        assert!(prepared.is_some());
        assert!(sources.remove(&id).await.unwrap());
        assert!(
            !sources
                .finish_refresh(&id, &current, Ok(prepared))
                .await
                .unwrap()
        );
        assert!(!fs::try_exists(sources.path(&id)).await.unwrap());
        assert!(sources.snapshot().await.is_empty());
    }

    #[tokio::test]
    async fn local_source_disable_and_remove_survive_restart_without_changing_the_script() {
        let directory = tempfile::tempdir().unwrap();
        let root = directory.path();
        let folder = root.join("local/nested");
        let path = folder.join("demo.js");
        fs::create_dir_all(folder).await.unwrap();
        let script = format!("/*\n * @name Local demo\n */\n{DEMO}");
        fs::write(&path, &script).await.unwrap();
        let id = "local:nested/demo.js";
        let sources = Arc::new(Sources::open(root.to_owned()).await.unwrap());
        assert_eq!(sources.active().await[0].0, id);

        sources.set_enabled(id, false).await.unwrap();
        sources.set_enabled(id, false).await.unwrap();
        assert!(sources.active().await.is_empty());
        assert_eq!(fs::read_to_string(&path).await.unwrap(), script);
        drop(sources);

        fs::write(&path, "throw new Error('broken source');")
            .await
            .unwrap();
        let sources = Arc::new(Sources::open(root.to_owned()).await.unwrap());
        let states = sources.snapshot().await;
        assert_eq!(states[0].name, "Local demo");
        assert!(!states[0].enabled);
        assert!(!states[0].subscription);
        assert!(states[0].last_error.is_empty());
        assert!(sources.set_enabled(id, true).await.is_err());
        assert!(!sources.snapshot().await[0].enabled);
        fs::write(&path, &script).await.unwrap();
        sources.set_enabled(id, true).await.unwrap();
        assert_eq!(sources.active().await[0].0, id);

        assert!(sources.remove(id).await.unwrap());
        assert!(!sources.remove(id).await.unwrap());
        assert!(sources.snapshot().await.is_empty());
        drop(sources);
        let sources = Sources::open(root.to_owned()).await.unwrap();
        assert!(sources.snapshot().await.is_empty());
        assert!(sources.active().await.is_empty());
        assert_eq!(fs::read_to_string(path).await.unwrap(), script);
    }

    #[tokio::test]
    async fn subscription_can_change_url_and_stay_disabled_across_restarts() {
        let directory = tempfile::tempdir().unwrap();
        let root = directory.path();
        let sources = Arc::new(Sources::open(root.to_owned()).await.unwrap());
        let original = root.join("imports/original.js");
        let replacement = root.join("imports/replacement.js");
        let broken = root.join("imports/broken.js");
        fs::write(&original, DEMO).await.unwrap();
        fs::write(&replacement, DEMO.replace("/audio", "/new"))
            .await
            .unwrap();
        fs::write(&broken, "throw new Error('broken source');")
            .await
            .unwrap();
        let original_url = Url::from_file_path(&original).unwrap();
        let replacement_url = Url::from_file_path(&replacement).unwrap();
        let broken_url = Url::from_file_path(broken).unwrap();
        let id = sources.import(original_url.as_str()).await.unwrap().id;
        assert_eq!(sources.active().await.len(), 1);

        assert!(sources.update_url(&id, broken_url.as_str()).await.is_err());
        assert_eq!(
            sources.snapshot().await[0].origin.as_deref(),
            Some(original_url.as_str())
        );
        assert_eq!(sources.active().await.len(), 1);

        sources.set_enabled(&id, false).await.unwrap();
        sources
            .update_url(&id, replacement_url.as_str())
            .await
            .unwrap();
        assert!(sources.active().await.is_empty());
        assert_eq!(sources.refresh(None).await.unwrap().total, 0);
        assert_eq!(sources.refresh(Some(&id)).await.unwrap().total, 1);
        assert!(sources.active().await.is_empty());
        assert_eq!(
            sources.import(replacement_url.as_str()).await.unwrap().id,
            id
        );

        let other = sources.import(original_url.as_str()).await.unwrap();
        assert!(other.added);
        assert_ne!(other.id, id);
        let error = sources
            .update_url(&id, original_url.as_str())
            .await
            .unwrap_err();
        assert_eq!(error.kind(), io::ErrorKind::AlreadyExists);
        drop(sources);
        let sources = Arc::new(Sources::open(root.to_owned()).await.unwrap());
        let snapshot = sources.snapshot().await;
        let edited = snapshot.iter().find(|source| source.id == id).unwrap();
        assert!(!edited.enabled);
        assert!(!edited.available);
        assert_eq!(edited.origin.as_deref(), Some(replacement_url.as_str()));
        assert_eq!(sources.active().await[0].0, other.id);

        fs::write(
            &replacement,
            "throw new Error('the installed copy should be used');",
        )
        .await
        .unwrap();
        sources.set_enabled(&id, true).await.unwrap();
        assert_eq!(sources.active().await.len(), 2);
        assert!(sources.remove(&id).await.unwrap());
        assert!(replacement.exists());
        assert_eq!(sources.snapshot().await.len(), 1);
    }

    #[tokio::test]
    async fn in_flight_subscription_commit_survives_cancellation() {
        let directory = tempfile::tempdir().unwrap();
        let sources = Arc::new(Sources::open(directory.path().to_owned()).await.unwrap());
        let script = directory.path().join("imports/demo.js");
        fs::write(&script, DEMO).await.unwrap();
        let url = Url::from_file_path(script).unwrap();
        let id = source_id(url.as_str());
        let path = sources.path(&id);
        let reading = sources.scripts.read().await;
        let importing = tokio::spawn({
            let sources = sources.clone();
            async move { sources.import(url.as_str()).await }
        });
        timeout(Duration::from_secs(10), async {
            while !fs::try_exists(&path).await.unwrap() {
                sleep(Duration::from_millis(5)).await;
            }
        })
        .await
        .expect("subscription file was not committed");
        importing.abort();
        assert!(importing.await.unwrap_err().is_cancelled());
        assert!(sources.updates.try_lock().is_err());
        drop(reading);

        sources.drain().await;
        assert_eq!(sources.snapshot().await[0].id, id);
        assert!(sources.remove(&id).await.unwrap());
        assert!(!path.exists());
        assert!(sources.snapshot().await.is_empty());
    }
}
