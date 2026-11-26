mod config;
mod runtime;
mod web;

use crate::{
    admin,
    domains::{announcements::Announcements, social},
};
use config::{Command, Config};
use std::io;

/// Runs the daemon, validates startup parameters or opens local administration.
///
/// Validation checks configuration and any configured announcement catalog. It does not probe
/// listeners or providers, load TLS credentials, or initialize service stores.
///
/// # Errors
/// Reports invalid parameters, critical startup failures, an unexpected Paper IPC exit
/// or an administration error.
pub fn run() -> io::Result<()> {
    let args: Vec<_> = std::env::args_os().skip(1).collect();
    if let [action, args @ ..] = args.as_slice()
        && action == "admin"
    {
        return admin::run(args, &config::default_control_socket());
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
