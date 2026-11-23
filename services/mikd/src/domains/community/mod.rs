//! Shared community storage for platform bindings and governance.
mod governance;
mod identity;
mod outbox;
mod schema;
mod store;

pub(crate) use identity::{
    Binding, BindingChange, BindingIndex, ExternalKey, IdentityService, Membership,
    MembershipEvidence, Redemption,
};
use std::{fs, os::unix::fs::PermissionsExt, path::Path};
pub(crate) use store::CommunityStore;

#[derive(Debug, thiserror::Error)]
pub enum Error {
    #[error("community database rejected: {0}")]
    Invalid(&'static str),
    #[error("community filesystem operation failed")]
    Io(#[from] std::io::Error),
    #[error("community SQLite operation failed")]
    Database(#[from] tokio_rusqlite::rusqlite::Error),
    #[error("community queue is full or closed")]
    Busy,
    #[error("community state has changed")]
    Stale,
    #[error("operation already committed")]
    Committed,
    #[error("operation is not permitted")]
    Denied,
}

fn java_whitespace(ch: char) -> bool {
    ('\u{001c}'..='\u{001f}').contains(&ch)
        || (ch.is_whitespace() && !matches!(ch, '\u{00a0}' | '\u{2007}' | '\u{202f}' | '\u{0085}'))
}

fn validate_target_path(path: &Path) -> Result<&Path, Error> {
    if !path.is_absolute() || path.file_name().is_none() {
        return Err(Error::Invalid(
            "an absolute community database path is required",
        ));
    }
    let parent = path
        .parent()
        .ok_or(Error::Invalid("community database parent required"))?;
    let info = fs::symlink_metadata(parent)?;
    if !info.is_dir() || info.permissions().mode() & 0o077 != 0 {
        return Err(Error::Invalid("a private, absolute directory is required"));
    }
    Ok(parent)
}

#[cfg(test)]
mod tests;
