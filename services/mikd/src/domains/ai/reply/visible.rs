use serde::Serialize;

#[derive(Clone, Default, Serialize)]
pub(crate) struct VisibleReply {
    reply: String,
    #[serde(skip_serializing_if = "Vec::is_empty")]
    references: Vec<VisibleReference>,
}

#[derive(Clone, Serialize)]
struct VisibleReference {
    line: usize,
    label: String,
    url: String,
    #[serde(skip)]
    end: usize,
}

impl VisibleReply {
    pub(crate) fn as_str(&self) -> &str {
        &self.reply
    }

    pub(crate) fn push(&mut self, text: &str) {
        self.reply.push_str(text);
    }

    pub(crate) fn push_link(&mut self, label: &str, url: &str) {
        let line = 1 + self.reply.bytes().filter(|byte| *byte == b'\n').count();
        self.push(label);
        self.references.push(VisibleReference {
            line,
            label: label.into(),
            url: url.into(),
            end: self.reply.len(),
        });
    }

    pub(crate) fn clip(&mut self, limit: usize) -> Option<usize> {
        let (end, _) = self.reply.char_indices().nth(limit)?;
        if limit == 0 {
            self.reply.clear();
            self.references.clear();
            return Some(0);
        }
        let before_marker = self.reply[..end].char_indices().next_back()?.0;
        let head = &self.reply[..before_marker];
        let boundary = head
            .rfind('\n')
            .filter(|&index| head[index..].chars().count() <= limit / 3)
            .unwrap_or(before_marker);
        let boundary = self
            .references
            .iter()
            .filter(|reference| reference.label == reference.url)
            .map(|reference| (reference.end - reference.label.len(), reference.end))
            .find_map(|(start, end)| (start < boundary && boundary < end).then_some(start))
            .unwrap_or(boundary);
        let body_end = self.reply[..boundary].trim_end().len();
        let separate = body_end < boundary || boundary < before_marker;
        self.reply.truncate(body_end);
        self.references
            .retain(|reference| reference.end <= body_end);
        if separate && body_end > 0 && self.reply.chars().count() + 1 < limit {
            self.reply.push('\n');
        }
        self.reply.push('…');
        Some(body_end)
    }

    pub(crate) fn plain_within(&self, limit: usize) -> (String, Self) {
        let mut remaining = limit.saturating_sub(self.reply.chars().count());
        let mut plain = String::with_capacity(self.reply.len());
        let mut references = Vec::new();
        let mut offset = 0;
        for reference in &self.references {
            if reference.label == reference.url {
                references.push(reference.clone());
                continue;
            }
            let length = reference.url.chars().count() + 3;
            if length <= remaining {
                plain.push_str(&self.reply[offset..reference.end]);
                plain.push_str(" (");
                plain.push_str(&reference.url);
                plain.push(')');
                references.push(reference.clone());
                offset = reference.end;
                remaining -= length;
            }
        }
        plain.push_str(&self.reply[offset..]);
        (
            plain,
            Self {
                reply: self.reply.clone(),
                references,
            },
        )
    }

    pub(in crate::domains::ai) fn limit(mut self, chars: usize) -> Self {
        if let Some((end, _)) = self.reply.char_indices().nth(chars) {
            self.reply.truncate(end);
            self.references.retain(|reference| reference.end <= end);
        }
        self
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    #[test]
    fn transcript_retains_only_whole_references_within_its_unicode_limit() {
        let mut visible = VisibleReply::default();
        for (index, id) in ["first", "second"].into_iter().enumerate() {
            if index != 0 {
                visible.push("\n");
            }
            visible.push_link("展馆", &format!("https://mcmik.top/buildings/{id}"));
        }
        assert_eq!(
            serde_json::to_value(visible.limit(4)).unwrap(),
            json!({
                "reply": "展馆\n展",
                "references": [{"line": 1, "label": "展馆", "url": "https://mcmik.top/buildings/first"}],
            })
        );
    }

    #[test]
    fn fallback_copies_only_addresses_that_fit_and_records_the_same_links() {
        let mut visible = VisibleReply::default();
        let destination = "https://mcmik.top/wiki";
        visible.push_link("指南", destination);
        visible.push("\n");
        visible.push_link("地图", "https://mcmik.top/map");
        let (plain, history) = visible.plain_within(5 + destination.len() + 3);
        assert_eq!(plain, format!("指南 ({destination})\n地图"));
        assert_eq!(
            serde_json::to_value(history).unwrap(),
            json!({
                "reply": "指南\n地图", "references": [{"line": 1, "label": "指南", "url": destination}],
            })
        );
    }

    #[test]
    fn clipping_keeps_whole_links_and_never_shows_a_partial_bare_url() {
        let mut visible = VisibleReply::default();
        let first = "https://mcmik.top/wiki";
        let second = "https://mcmik.top/buildings";
        visible.push_link("Guide", first);
        visible.push("\n");
        visible.push_link(second, second);
        let _ = visible.clip(15);
        let (plain, history) = visible.plain_within(50);
        assert_eq!(plain, format!("Guide ({first})\n…"));
        assert_eq!(
            serde_json::to_value(history).unwrap()["references"]
                .as_array()
                .unwrap()
                .len(),
            1
        );
    }
}
