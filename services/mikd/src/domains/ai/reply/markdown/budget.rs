use mik_protocol::wire::{AssistantPart, assistant_part::Target};

pub(super) const TEXT_LIMIT: usize = 6000;
const NODE_LIMIT: usize = 768;
const LINK_BYTES: usize = 16 * 1024;

pub(super) struct Budget {
    remaining: usize,
    nodes: usize,
    link_bytes: usize,
    truncated: bool,
    omitted: bool,
}

impl Default for Budget {
    fn default() -> Self {
        Self {
            remaining: TEXT_LIMIT,
            nodes: NODE_LIMIT,
            link_bytes: LINK_BYTES,
            truncated: false,
            omitted: false,
        }
    }
}

impl Budget {
    pub(super) const fn truncated(&self) -> bool {
        self.truncated
    }

    pub(super) const fn omitted(&self) -> bool {
        self.omitted
    }

    pub(super) const fn nodes_left(&self) -> usize {
        self.nodes
    }

    pub(super) fn node(&mut self) -> bool {
        if self.truncated || self.nodes == 0 || self.remaining == 0 {
            self.truncated = true;
            return false;
        }
        self.nodes -= 1;
        true
    }

    pub(super) fn text(&mut self, text: &mut String) -> bool {
        if let Some((end, _)) = text.char_indices().nth(self.remaining) {
            text.truncate(end);
            self.remaining = 0;
            self.truncated = true;
            false
        } else {
            self.remaining -= text.chars().count();
            true
        }
    }

    pub(super) fn push(&mut self, parts: &mut Vec<AssistantPart>, mut part: AssistantPart) {
        if part.spans.is_empty() || !self.node() {
            return;
        }
        let bare_url = matches!(&part.target, Some(Target::Url(url)) if
            part.spans.iter().flat_map(|span| span.text.bytes()).eq(url.bytes()));
        let mut kept = 0;
        let mut complete = true;
        for span in &mut part.spans {
            if !self.node() {
                complete = false;
                break;
            }
            kept += 1;
            if !self.text(&mut span.text) {
                complete = false;
                break;
            }
        }
        if !complete && bare_url {
            return;
        }
        part.spans.truncate(kept);
        if !complete || part.spans.iter().all(|span| span.text.trim().is_empty()) {
            part.target = None;
            part.item_id.clear();
        }
        if let Some(Target::Url(url)) = &part.target {
            if url.len() <= self.link_bytes {
                self.link_bytes -= url.len();
            } else {
                part.target = None;
                self.omitted = true;
            }
        }
        if !part.spans.is_empty() {
            parts.push(part);
        }
    }
}
