use super::{
    knowledge::{MikWiki, MinecraftWiki, Tavily},
    model::{self, Models, ProviderSettings},
};
use serde::Deserialize;
use std::{
    fs::File,
    io::{self, Read},
    path::{Path, PathBuf},
};

const MAX_CONFIG_BYTES: u64 = 64 * 1024;

#[derive(Deserialize)]
#[serde(deny_unknown_fields, rename_all = "kebab-case")]
struct Settings {
    provider: ProviderSettings,
    concurrent_completions: Option<usize>,
    web_search: Option<WebSearchSettings>,
}

#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
struct WebSearchSettings {
    #[serde(default)]
    enabled: bool,
}

pub struct AiConfig {
    pub(super) models: Models,
    pub(super) knowledge_directory: PathBuf,
    pub(super) memory_database: PathBuf,
    pub(super) mik_wiki: MikWiki,
    pub(super) minecraft: MinecraftWiki,
    pub(super) web_search: Option<Tavily>,
}

impl AiConfig {
    /// # Errors
    /// Rejects invalid daemon AI configuration without building the knowledge index.
    pub fn load(
        path: &Path,
        state: &Path,
        wiki_cache: Option<PathBuf>,
        api_key: Option<&str>,
        tavily_api_key: Option<&str>,
    ) -> io::Result<Self> {
        if !path.is_absolute() {
            return Err(invalid("AI configuration path must be absolute"));
        }
        let mut raw = Vec::new();
        File::open(path)?
            .take(MAX_CONFIG_BYTES + 1)
            .read_to_end(&mut raw)?;
        if raw.len() as u64 > MAX_CONFIG_BYTES {
            return Err(invalid("AI configuration exceeds its size limit"));
        }
        let settings: Settings = serde_yaml_ng::from_slice(&raw)
            .map_err(|_| invalid("invalid mikd AI configuration"))?;
        let concurrency = settings.concurrent_completions.unwrap_or(4);
        if !(1..=8).contains(&concurrency) {
            return Err(invalid("concurrent-completions must be between 1 and 8"));
        }
        let models = model::open(settings.provider, concurrency, api_key)?;
        let web_search = if settings.web_search.is_some_and(|search| search.enabled) {
            let key =
                tavily_api_key.ok_or_else(|| invalid("web-search requires MIKD_TAVILY_API_KEY"))?;
            Some(Tavily::new(key)?)
        } else {
            None
        };
        Ok(Self {
            models,
            knowledge_directory: state.join("knowledge"),
            memory_database: state.join("ai-player-memory.sqlite"),
            mik_wiki: MikWiki::new()?,
            minecraft: MinecraftWiki::new(wiki_cache)?,
            web_search,
        })
    }
}

fn invalid(message: &'static str) -> io::Error {
    io::Error::new(io::ErrorKind::InvalidInput, message)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::fs;

    #[test]
    fn web_search_requires_an_explicit_switch_and_a_separate_key() {
        let directory = tempfile::tempdir().unwrap();
        let path = directory.path().join("ai.yml");
        let provider =
            "provider:\n  endpoint: https://model.example.org/v1/chat/completions\n  model: test\n";
        let load = |tavily_key| {
            AiConfig::load(&path, directory.path(), None, Some("model-key"), tavily_key)
        };

        fs::write(&path, provider).unwrap();
        assert!(load(Some("tvly-test-key")).unwrap().web_search.is_none());

        fs::write(&path, format!("{provider}web-search:\n  enabled: true\n")).unwrap();
        assert_eq!(
            load(None).err().unwrap().kind(),
            io::ErrorKind::InvalidInput
        );
        assert_eq!(
            load(Some("bad key")).err().unwrap().kind(),
            io::ErrorKind::InvalidInput
        );
        assert!(load(Some("tvly-test-key")).unwrap().web_search.is_some());
    }
}
