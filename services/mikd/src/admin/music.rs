use super::usage;
use crate::{domains::music::CatalogQuery, transport::admin::Command};
use std::{io, path};

pub(super) fn command(args: &[&str]) -> io::Result<Command> {
    let command = match args {
        ["catalog", "list", args @ ..] => {
            let (query, args) = match args {
                ["--query", query, args @ ..] => (*query, args),
                _ => ("", args),
            };
            Command::QueryCatalog {
                query: CatalogQuery::Stored(query.into()),
                after: after(args)?,
            }
        }
        [
            "catalog",
            kind @ ("search" | "collection"),
            value,
            args @ ..,
        ] => {
            let query = if *kind == "search" {
                CatalogQuery::Search((*value).into())
            } else {
                CatalogQuery::Collection((*value).into())
            };
            Command::QueryCatalog {
                query,
                after: after(args)?,
            }
        }
        ["library", "list", args @ ..] => Command::ListLibrary(after(args)?),
        ["library", "import", file] => Command::ImportLocal(path::absolute(file)?),
        ["library", "replace", id, file] => Command::ReplaceLocal {
            id: (*id).into(),
            path: path::absolute(file)?,
        },
        ["track", "inspect", id] => Command::InspectTrack((*id).into()),
        ["track", "versions", id, args @ ..] => Command::TrackVersions {
            id: (*id).into(),
            after: after(args)?,
        },
        ["track", "prepare", id] => Command::PrepareTrack((*id).into()),
        ["track", "retire", id] => Command::RetireTrack((*id).into()),
        ["track", "restore", id] => Command::RestoreTrack((*id).into()),
        ["cache", "list", args @ ..] => Command::ListCache(after(args)?),
        ["cache", "inspect", version] => Command::InspectCache((*version).into()),
        ["cache", "evict", version] => Command::EvictCache((*version).into()),
        ["cache", "clear-idle"] => Command::ClearIdleCache,
        ["lyrics", "inspect", version] => Command::InspectLyrics((*version).into()),
        ["lyrics", "set", version, file] => Command::SetLyrics {
            version: (*version).into(),
            path: path::absolute(file)?,
        },
        ["lyrics", "clear", version] => Command::ClearLyrics((*version).into()),
        ["sources", "list"] => Command::ListSources,
        ["sources", "inspect", id] => Command::InspectSource((*id).into()),
        ["sources", "tracks", id, args @ ..] => Command::QueryCatalog {
            query: CatalogQuery::RegisteredBySource((*id).into()),
            after: after(args)?,
        },
        ["sources", "cache", id, args @ ..] => Command::ListSourceCache {
            id: (*id).into(),
            after: after(args)?,
        },
        ["sources", "import", url] => Command::ImportSource((*url).into()),
        ["sources", "edit", id, url] => Command::UpdateSource {
            id: (*id).into(),
            url: (*url).into(),
        },
        ["sources", action @ ("enable" | "disable"), id] => Command::SetSourceEnabled {
            id: (*id).into(),
            enabled: *action == "enable",
        },
        ["sources", "refresh"] => Command::RefreshSources,
        ["sources", "refresh", id] => Command::RefreshSource((*id).into()),
        ["sources", "remove", id] => Command::RemoveSource((*id).into()),
        ["sources", "test", source_id, track_id] => Command::TestSourcePlayback {
            source_id: (*source_id).into(),
            track_id: (*track_id).into(),
        },
        _ => return Err(usage()),
    };
    Ok(command)
}

fn after(args: &[&str]) -> io::Result<String> {
    match args {
        [] => Ok(String::new()),
        ["--after", cursor] => Ok((*cursor).into()),
        _ => Err(usage()),
    }
}
