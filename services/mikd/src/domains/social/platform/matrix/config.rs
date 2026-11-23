use super::super::super::config::{CommandPrefix, Conversation};
use matrix_sdk::ruma::{OwnedRoomId, OwnedUserId};
use serde::Deserialize;
use std::{collections::BTreeMap, io};
use url::Url;

#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
pub(in crate::domains::social) struct Matrix {
    pub(super) homeserver: Url,
    pub(super) user: OwnedUserId,
    pub(in crate::domains::social) rooms: BTreeMap<OwnedRoomId, Conversation>,
    #[serde(default)]
    pub(in crate::domains::social) command_prefix: CommandPrefix,
    #[serde(default)]
    pub(super) recipient_devices: MatrixRecipients,
    #[serde(default)]
    pub(super) trusted_users: BTreeMap<OwnedUserId, String>,
}

impl Matrix {
    pub(in crate::domains::social) fn validate(&self) -> io::Result<()> {
        if self.homeserver.scheme() != "https"
            || !self.homeserver.username().is_empty()
            || self.homeserver.password().is_some()
        {
            return Err(io::Error::other(
                "Matrix homeserver must be an HTTPS URL without credentials",
            ));
        }
        Ok(())
    }
}

#[derive(Clone, Copy, Default, Deserialize)]
#[serde(rename_all = "snake_case")]
pub(super) enum MatrixRecipients {
    #[default]
    Verified,
    CrossSigned,
}
