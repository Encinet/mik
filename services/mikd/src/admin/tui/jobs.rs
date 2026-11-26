use super::catalog::{Collection, PageQuery, SourceDetail};
use crate::{
    admin::{console, systemd},
    console::{CommandOutcome, Target},
    domains::music::{
        CacheEntry, CachePage, CatalogPage, CatalogQuery, LyricsInspection, SourceState,
        TrackInspection, VersionPage,
    },
    transport::admin::{self, CallError, Command, Reply, Status},
};
use std::{io, path::Path, time::Duration};
use tokio::time::timeout;

const READ_TIMEOUT: Duration = Duration::from_secs(5);
const UNEXPECTED_REPLY: &str = "Unexpected reply from the management socket";

pub(super) enum Work {
    Library {
        keyword: String,
        page: PageQuery,
    },
    SourceTracks {
        id: String,
        page: PageQuery,
    },
    Online {
        query: CatalogQuery,
        after: String,
    },
    Track(String),
    Versions {
        id: String,
        title: String,
        page: PageQuery,
    },
    Lyrics(String),
    Sources,
    Source(String),
    FollowSource(String),
    Cache {
        source: Option<String>,
        page: PageQuery,
    },
    CacheEntry(String),
    Change(Change),
    Service(systemd::Action),
    ConsoleCommand {
        target: Target,
        line: String,
    },
}

pub(super) struct Change {
    pub command: Command,
    pub collection: Collection,
    pub label: &'static str,
}

pub(super) enum Finished {
    Library(String, PageQuery, Result<CatalogPage, String>),
    SourceTracks(String, PageQuery, Result<CatalogPage, String>),
    Online(CatalogQuery, String, Result<CatalogPage, String>),
    Track(Result<Box<TrackInspection>, String>),
    Versions(String, String, PageQuery, Result<VersionPage, String>),
    Lyrics(Result<LyricsInspection, String>),
    Sources(Result<Vec<SourceState>, String>),
    Source(Result<SourceDetail, String>),
    Cache(Option<String>, PageQuery, Result<CachePage, String>),
    CacheEntry(Result<Box<CacheEntry>, String>),
    Change(Collection, Result<Report, ChangeError>),
    Service(systemd::Action, io::Result<()>),
    ConsoleCommand(String, Result<CommandOutcome, console::RunError>),
}

pub(super) struct Report {
    pub message: String,
    pub partial: bool,
    pub selection: Option<String>,
    pub effect: Effect,
}

pub(super) enum Effect {
    Changed,
    TrackState { id: String, retired: bool },
    SourceRemoved(String),
    CacheEvicted(String),
    Lyrics { version: String, cleared: bool },
}

pub(super) enum ChangeError {
    Failed(String),
    Unconfirmed(String),
}

impl Work {
    pub fn label(&self) -> &'static str {
        match self {
            Self::Library { .. } => "Load library",
            Self::SourceTracks { .. } => "Load registered tracks",
            Self::Online {
                query: CatalogQuery::Collection(_),
                ..
            } => "Preview external collection",
            Self::Online { .. } => "Search online catalog",
            Self::Track(_) => "Inspect track",
            Self::Versions { .. } => "Load media versions",
            Self::Lyrics(_) => "Inspect lyrics",
            Self::Sources => "Load sources",
            Self::Source(_) | Self::FollowSource(_) => "Inspect source",
            Self::Cache {
                source: Some(_), ..
            } => "Load cached downloads",
            Self::Cache { source: None, .. } => "Load media cache",
            Self::CacheEntry(_) => "Inspect cached media",
            Self::Change(change) => change.label,
            Self::ConsoleCommand { .. } => "Send console command",
            Self::Service(action) => match action {
                systemd::Action::Start => "Start service",
                systemd::Action::Stop => "Stop service",
                systemd::Action::Restart => "Restart service",
            },
        }
    }

    pub fn modifies(&self) -> bool {
        matches!(
            self,
            Self::Change(_) | Self::Service(_) | Self::ConsoleCommand { .. }
        )
    }

    pub async fn run(self, socket: &Path) -> Finished {
        match self {
            Self::Library { keyword, page } => {
                let result = query_catalog(
                    socket,
                    CatalogQuery::Stored(keyword.clone()),
                    page.after.clone(),
                )
                .await;
                Finished::Library(keyword, page, result)
            }
            Self::SourceTracks { id, page } => {
                let result = query_catalog(
                    socket,
                    CatalogQuery::RegisteredBySource(id.clone()),
                    page.after.clone(),
                )
                .await;
                Finished::SourceTracks(id, page, result)
            }
            Self::Online { query, after } => {
                let result = query_catalog(socket, query.clone(), after.clone()).await;
                Finished::Online(query, after, result)
            }
            Self::Track(id) => Finished::Track(
                read(socket, Command::InspectTrack(id), |reply| match reply {
                    Reply::Track(track) => Some(track),
                    _ => None,
                })
                .await,
            ),
            Self::Versions { id, title, page } => {
                let result = read(
                    socket,
                    Command::TrackVersions {
                        id: id.clone(),
                        after: page.after.clone(),
                    },
                    |reply| match reply {
                        Reply::Versions(versions) => Some(versions),
                        _ => None,
                    },
                )
                .await;
                Finished::Versions(id, title, page, result)
            }
            Self::Lyrics(version) => Finished::Lyrics(
                read(
                    socket,
                    Command::InspectLyrics(version),
                    |reply| match reply {
                        Reply::Lyrics(lyrics) => Some(lyrics),
                        _ => None,
                    },
                )
                .await,
            ),
            Self::Sources => Finished::Sources(
                read(socket, Command::ListSources, |reply| match reply {
                    Reply::Sources(sources) => Some(sources),
                    _ => None,
                })
                .await,
            ),
            Self::Source(id) => inspect_source(socket, Command::InspectSource(id)).await,
            Self::FollowSource(id) => inspect_source(socket, Command::FollowSource(id)).await,
            Self::Cache { source, page } => {
                let result = query_cache(socket, source.as_deref(), page.after.clone()).await;
                Finished::Cache(source, page, result)
            }
            Self::CacheEntry(version) => Finished::CacheEntry(
                read(
                    socket,
                    Command::InspectCache(version),
                    |reply| match reply {
                        Reply::CacheEntry(entry) => Some(entry),
                        _ => None,
                    },
                )
                .await,
            ),
            Self::Change(change) => Finished::Change(
                change.collection,
                perform_change(socket, change.command, change.label).await,
            ),
            Self::Service(action) => Finished::Service(action, systemd::control(action).await),
            Self::ConsoleCommand { target, line } => {
                let id = target.id.to_string();
                Finished::ConsoleCommand(id, console::run(socket, target, line).await)
            }
        }
    }
}

async fn inspect_source(socket: &Path, command: Command) -> Finished {
    Finished::Source(
        read(socket, command, |reply| match reply {
            Reply::Source(source) => Some(SourceDetail::Registered(source)),
            Reply::SourceMissing(id) => Some(SourceDetail::Missing(id)),
            _ => None,
        })
        .await,
    )
}

async fn query_cache(
    socket: &Path,
    source: Option<&str>,
    after: String,
) -> Result<CachePage, String> {
    let command = match source {
        Some(id) => Command::ListSourceCache {
            id: id.to_owned(),
            after,
        },
        None => Command::ListCache(after),
    };
    read(socket, command, |reply| match reply {
        Reply::CachePage(page) => Some(page),
        _ => None,
    })
    .await
}

async fn query_catalog(
    socket: &Path,
    query: CatalogQuery,
    after: String,
) -> Result<CatalogPage, String> {
    let stored = matches!(
        query,
        CatalogQuery::Stored(_) | CatalogQuery::RegisteredBySource(_)
    );
    let command = Command::QueryCatalog { query, after };
    let payload = |reply| match reply {
        Reply::Catalog(page) => Some(page),
        _ => None,
    };
    if stored {
        read(socket, command, payload).await
    } else {
        request(socket, command, payload).await
    }
}

pub(super) async fn status(socket: &Path) -> Result<Status, String> {
    read(socket, Command::Status, |reply| match reply {
        Reply::Status(status) => Some(status),
        _ => None,
    })
    .await
}

async fn read<T>(
    socket: &Path,
    command: Command,
    payload: impl FnOnce(Reply) -> Option<T>,
) -> Result<T, String> {
    timeout(READ_TIMEOUT, request(socket, command, payload))
        .await
        .map_err(|_| "Management socket did not reply in time".to_owned())?
}

async fn request<T>(
    socket: &Path,
    command: Command,
    payload: impl FnOnce(Reply) -> Option<T>,
) -> Result<T, String> {
    match admin::call(socket, command).await {
        Ok(Reply::Error(error)) => Err(error),
        Ok(reply) => payload(reply).ok_or_else(|| UNEXPECTED_REPLY.into()),
        Err(error) => Err(error.to_string()),
    }
}

async fn perform_change(
    socket: &Path,
    command: Command,
    label: &str,
) -> Result<Report, ChangeError> {
    let effect = match &command {
        Command::RetireTrack(id) | Command::RestoreTrack(id) => Effect::TrackState {
            id: id.clone(),
            retired: matches!(command, Command::RetireTrack(_)),
        },
        Command::RemoveSource(id) => Effect::SourceRemoved(id.clone()),
        Command::EvictCache(version) => Effect::CacheEvicted(version.clone()),
        Command::SetLyrics { version, .. } | Command::ClearLyrics(version) => Effect::Lyrics {
            version: version.clone(),
            cleared: matches!(command, Command::ClearLyrics(_)),
        },
        _ => Effect::Changed,
    };
    let reply = match admin::call_with_outcome(socket, command).await {
        Ok(Reply::Error(error)) if Reply::is_unconfirmed(&error) => {
            return Err(ChangeError::Unconfirmed(error));
        }
        Ok(Reply::Error(error)) => return Err(ChangeError::Failed(error)),
        Err(CallError::NotSent(error)) => return Err(ChangeError::Failed(error.to_string())),
        Err(CallError::Unconfirmed(error)) => {
            return Err(ChangeError::Unconfirmed(error.to_string()));
        }
        Ok(reply) => reply,
    };
    let mut report = Report {
        message: String::new(),
        partial: false,
        selection: None,
        effect,
    };
    report.message = match reply {
        Reply::LocalTrack(track) => {
            let message = format!("{label} completed · {} [{}]", track.title, track.id);
            report.selection = Some(track.id);
            message
        }
        Reply::Imported(source) => {
            let state = if source.added {
                "added"
            } else if source.changed {
                "updated"
            } else {
                "already up to date"
            };
            let message = format!("Subscription {} {state}", source.id);
            report.selection = Some(source.id);
            message
        }
        Reply::Refreshed(result) => {
            report.partial = result.failed > 0;
            let mut text = format!(
                "{} subscriptions checked · {} updated · {} failed",
                result.total, result.changed, result.failed
            );
            if report.partial {
                text.push_str("\nFailed · ");
                text.push_str(&result.failed_ids.join(", "));
            }
            text
        }
        Reply::Removed { id } | Reply::Restored { id } => format!("{label} completed · {id}"),
        Reply::Prepared(prepared) => {
            let text = format!("Cache ready · {} · {}", prepared.id, prepared.version);
            report.selection = Some(prepared.version);
            text
        }
        Reply::LyricsEdited => format!("{label} completed"),
        Reply::CacheEvicted {
            version,
            released_bytes,
        } => format!("{released_bytes} bytes released · version {version}"),
        Reply::CacheCleared(result) => {
            report.partial = result.skipped > 0 || result.failed > 0;
            format!(
                "{} removed · {} skipped · {} failed · {} bytes released",
                result.removed, result.skipped, result.failed, result.released_bytes
            )
        }
        _ => return Err(ChangeError::Unconfirmed(UNEXPECTED_REPLY.into())),
    };
    Ok(report)
}
