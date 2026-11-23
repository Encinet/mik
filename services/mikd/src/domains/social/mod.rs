mod avatar;
mod chat;
mod commands;
mod config;
mod followups;
mod locale;
mod membership;
mod platform;
mod runtime;

pub use config::Config;
pub(crate) use membership::Directory;
pub(crate) use platform::qq::QqModeration;
pub(crate) use runtime::Runtime;

use futures_util::FutureExt;
use std::{fs::OpenOptions, io, os::unix::fs::OpenOptionsExt, sync::Arc};

struct EvidenceOnExit(Arc<Directory>);

impl Drop for EvidenceOnExit {
    fn drop(&mut self) {
        self.0.set_qq_ready(false);
        self.0.clear_matrix();
    }
}

pub(crate) fn qq_moderation(config: Option<&Config>) -> io::Result<Option<QqModeration>> {
    config
        .and_then(|config| config.qq.as_ref())
        .map(|_| QqModeration::from_env())
        .transpose()
}

pub(crate) async fn run(
    config: Config,
    runtime: Runtime,
    qq_moderation: Option<QqModeration>,
) -> io::Result<()> {
    let state = config.state.clone();
    let _lock = tokio::task::spawn_blocking(move || {
        std::fs::create_dir_all(&state)?;
        let lock = OpenOptions::new()
            .write(true)
            .create(true)
            .truncate(false)
            .mode(0o600)
            .open(state.join("owner.lock"))?;
        lock.try_lock().map_err(io::Error::other)?;
        Ok::<_, io::Error>(lock)
    })
    .await
    .map_err(io::Error::other)??;
    let _evidence = EvidenceOnExit(runtime.directory.clone());
    let runtime = Arc::new(runtime);
    let matrix = async {
        if let Some(matrix) = config.matrix {
            report_exit(
                "Matrix",
                platform::matrix::run(matrix, &config.state, runtime.clone()).await,
            );
        }
    };
    let qq = async {
        if let Some(qq) = config.qq {
            report_exit(
                "QQ",
                platform::qq::run(qq, runtime.clone(), qq_moderation).await,
            );
        }
    };
    // Keep the connector futures out of the parent supervisor task layout.
    tokio::join!(matrix.boxed(), qq.boxed());
    Ok(())
}

fn report_exit(platform: &str, result: io::Result<()>) {
    match result {
        Ok(()) => eprintln!("mikd {platform} connector stopped"),
        Err(error) => eprintln!("mikd {platform} connector stopped: {error}"),
    }
}
