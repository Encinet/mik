mod admin_cli;
mod config;
mod runtime;
mod web;

use crate::domains::{announcements::Announcements, social};
use config::{Command, Config};
use std::io;

/// Runs the daemon or validates its startup parameters.
///
/// Validation checks configuration and any configured announcement catalog. It does not probe
/// listeners or providers, open or generate HPKE signing keys, or initialize service stores.
///
/// # Errors
/// Reports invalid parameters, critical startup failures, or an unexpected Paper IPC exit.
pub fn run() -> io::Result<()> {
    let args: Vec<_> = std::env::args_os().skip(1).collect();
    if let [action, args @ ..] = args.as_slice()
        && action == "admin"
    {
        return admin_cli::run(args);
    }
    let (command, config) = Config::load(&args)?;
    let qq_moderation = social::qq_moderation(config.social.as_ref());
    if matches!(command, Command::Validate) {
        Announcements::validate(&config.announcements)?;
        return qq_moderation.map(|_| ());
    }
    let qq_moderation = qq_moderation.unwrap_or_else(|error| {
        eprintln!("mikd QQ moderation unavailable: {error}");
        None
    });
    tokio::runtime::Builder::new_multi_thread()
        .enable_all()
        .build()?
        .block_on(runtime::run(config, qq_moderation))
}
