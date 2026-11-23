//! Validated, ordered player-facing rich text from one Markdown document.

mod layout;
mod markdown;
mod questions;
mod visible;

pub(super) use layout::paper_history;
pub(super) use markdown::project;
pub(crate) use {layout::Layout, visible::VisibleReply};

#[cfg(test)]
pub(in crate::domains) use markdown::sample;
