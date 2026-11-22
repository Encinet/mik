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
    script: String,
    #[serde(flatten)]
    origin: Origin,
}

#[derive(PartialEq, Serialize, Deserialize)]
struct Origin {
    url: String,
    etag: Option<String>,
    modified: Option<String>,
}

struct Installed {
    origin: Origin,
    checksum: [u8; 32],
}

#[derive(Clone)]
struct Entry {
    name: String,
    script: Option<Arc<Script>>,
    saved: Option<Arc<Installed>>,
    error: String,
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
        for folder in ["local", "imports", "subscriptions"] {
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
            let loaded = match read(&path, SCRIPT_LIMIT).await {
                Ok(text) => {
                    Script::load(&name, &text, client.clone(), runtimes.budget_for(&id)).await
                }
                Err(error) => Err(error),
            };
            scripts.insert(id, entry(name, loaded, None));
        }
        let subscription_paths = files(&root.join("subscriptions"), "json", 1).await?;
        for path in subscription_paths {
            if scripts.len() == SOURCE_LIMIT {
                break;
            }
            let Some(id) = path.file_stem().and_then(|name| name.to_str()) else {
                continue;
            };
            let saved = match load_saved(&path).await {
                Ok(saved) if source_id(&saved.origin.url) == id => saved,
                Ok(_) => {
                    eprintln!("ignoring misnamed LX subscription {id}");
                    continue;
                }
                Err(error) => {
                    eprintln!("cannot read LX subscription {id}: {error}");
                    continue;
                }
            };
            let loaded =
                Script::load(id, &saved.script, client.clone(), runtimes.budget_for(id)).await;
            let installed = Installed {
                checksum: Sha256::digest(&saved.script).into(),
                origin: saved.origin,
            };
            scripts.insert(
                id.into(),
                entry(id.into(), loaded, Some(Arc::new(installed))),
            );
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
                SourceState {
                    id: id.clone(),
                    name: entry.name.clone(),
                    available,
                    last_error,
                    channels: entry
                        .script
                        .as_ref()
                        .map_or_else(Vec::new, |script| script.channels.keys().cloned().collect()),
                    subscription: entry.saved.is_some(),
                }
            })
            .collect()
    }

    pub async fn import(self: &Arc<Self>, address: &str) -> io::Result<SourceImport> {
        let url = subscription_url(address)?;
        let id = source_id(url.as_str());
        let previous = {
            let scripts = self.scripts.read().await;
            let previous = scripts.get(&id).cloned();
            if previous.is_none() && scripts.len() >= SOURCE_LIMIT {
                return Err(io::Error::other("too many LX sources"));
            }
            previous
        };
        let prepared = self.prepare(&id, &url, previous.as_ref()).await?;
        let update = self.begin_update().await;
        {
            let scripts = self.scripts.read().await;
            if !same_installation(previous.as_ref(), scripts.get(&id)) {
                return Err(io::Error::new(
                    io::ErrorKind::WouldBlock,
                    "LX subscription changed during import",
                ));
            }
            if previous.is_none() && scripts.len() >= SOURCE_LIMIT {
                return Err(io::Error::other("too many LX sources"));
            }
        }
        let changed = self.install(update, &id, prepared).await?;
        Ok(SourceImport {
            id,
            added: previous.is_none(),
            changed,
        })
    }

    pub async fn remove(self: &Arc<Self>, id: &str) -> io::Result<bool> {
        let update = self.begin_update().await;
        if self
            .scripts
            .read()
            .await
            .get(id)
            .is_none_or(|entry| entry.saved.is_none())
        {
            return Ok(false);
        }
        let path = self.path(id);
        let id = id.to_owned();
        self.commit(update, move |sources| {
            std::fs::remove_file(path)?;
            let removed = sources.scripts.blocking_write().remove(&id);
            drop(removed);
            Ok(())
        })
        .await?;
        Ok(true)
    }

    pub async fn refresh(self: &Arc<Self>) -> SourceRefresh {
        let _refresh = self.refreshing.lock().await;
        let entries = self
            .scripts
            .read()
            .await
            .iter()
            .filter(|(_, entry)| entry.saved.is_some())
            .map(|(id, entry)| (id.clone(), entry.clone()))
            .collect::<Vec<_>>();
        let mut summary = SourceRefresh {
            total: i32::try_from(entries.len()).unwrap_or(i32::MAX),
            ..Default::default()
        };
        let mut prepared = stream::iter(entries)
            .map(|(id, current)| async move {
                let result = match current
                    .saved
                    .as_ref()
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
                Ok(changed) => summary.changed += i32::from(changed),
                Err(_) => summary.failed += 1,
            }
        }
        summary
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
        let old = previous.and_then(|entry| entry.saved.as_deref());
        let downloaded = self.download(url, old.map(|saved| &saved.origin)).await?;
        let saved = match downloaded {
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
        let path = self.path(id);
        let id = id.to_owned();
        self.commit(update, move |sources| {
            persist(&path, &saved)?;
            let entry = Entry {
                name: script.name.clone(),
                script: Some(script),
                saved: Some(Arc::new(Installed {
                    origin: saved.origin,
                    checksum,
                })),
                error: String::new(),
            };
            let replaced = sources.scripts.blocking_write().insert(id, entry);
            drop(replaced);
            Ok(())
        })
        .await?;
        Ok(changed)
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

fn same_installation(expected: Option<&Entry>, current: Option<&Entry>) -> bool {
    match (expected, current) {
        (None, None) => true,
        (Some(expected), Some(current)) => expected
            .saved
            .as_ref()
            .zip(current.saved.as_ref())
            .is_some_and(|(expected, current)| Arc::ptr_eq(expected, current)),
        _ => false,
    }
}

fn entry(name: String, script: io::Result<Script>, saved: Option<Arc<Installed>>) -> Entry {
    match script {
        Ok(script) => Entry {
            name: script.name.clone(),
            script: Some(Arc::new(script)),
            saved,
            error: String::new(),
        },
        Err(error) => Entry {
            name,
            script: None,
            saved,
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
            .take(SOURCE_LIMIT)
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

fn persist(path: &Path, saved: &Saved) -> io::Result<()> {
    let mut file = tempfile::NamedTempFile::new_in(
        path.parent()
            .ok_or_else(|| io::Error::other("invalid subscription path"))?,
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
