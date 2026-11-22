use super::super::super::config::{CommandPrefix, Conversation};
use serde::Deserialize;
use std::collections::BTreeMap;

#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
pub(in crate::domains::social) struct Qq {
    pub(in crate::domains::social) app_id: String,
    pub(in crate::domains::social) groups: BTreeMap<String, Conversation>,
    #[serde(default)]
    pub(in crate::domains::social) command_prefix: CommandPrefix,
    #[serde(default)]
    pub(super) approval_secret_env: BTreeMap<String, String>,
}
