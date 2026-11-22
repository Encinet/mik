use mik_protocol::wire::{AssistantSpan, assistant_span::Style};
use std::ops::Range;

const ITEM_PATH_LIMIT: usize = 96;
pub(super) const OMITTED_ADDRESS: &str = "[…]";

struct Run {
    end: usize,
    styles: Vec<i32>,
}

#[derive(Default)]
pub(super) struct Text {
    raw: String,
    runs: Vec<Run>,
}

pub(super) struct Segment {
    pub(super) spans: Vec<AssistantSpan>,
    pub(super) url: Option<String>,
}

impl Text {
    pub(super) fn as_str(&self) -> &str {
        &self.raw
    }

    pub(super) fn append(&mut self, other: Self) {
        let offset = self.raw.len();
        self.raw.push_str(&other.raw);
        for run in other.runs {
            self.finish_run(offset + run.end, run.styles);
        }
    }

    pub(super) fn push(&mut self, value: &str, styles: &[Style]) {
        let start = self.raw.len();
        self.raw.extend(value.chars().filter(|&character| {
            (!character.is_control() || matches!(character, '\n' | '\t'))
                && !matches!(character, '§' | '\u{feff}')
        }));
        if start != self.raw.len() {
            let mut wire = Vec::new();
            for style in styles {
                if !wire.contains(&(*style as i32)) {
                    wire.push(*style as i32);
                }
            }
            self.finish_run(self.raw.len(), wire);
        }
    }

    fn finish_run(&mut self, end: usize, styles: Vec<i32>) {
        if let Some(last) = self.runs.last_mut().filter(|last| last.styles == styles) {
            last.end = end;
        } else {
            self.runs.push(Run { end, styles });
        }
    }

    pub(super) fn segments(
        &self,
        verified: impl Fn(&str) -> Option<String>,
        omitted: &mut bool,
    ) -> Vec<Segment> {
        let finder = linkify::LinkFinder::new();
        let mut result = Vec::new();
        let mut pending = Vec::new();
        let mut start = 0;
        for link in finder.links(&self.raw) {
            pending.extend(self.spans(start..link.start()));
            if let Some(url) = verified(link.as_str()) {
                if !pending.is_empty() {
                    result.push(Segment {
                        spans: std::mem::take(&mut pending),
                        url: None,
                    });
                }
                let mut spans = self.spans(link.start()..link.end());
                let literal = spans
                    .iter()
                    .any(|span| span.styles.contains(&(Style::Code as i32)));
                if !literal
                    && url != link.as_str()
                    && let Some(first) = spans.first_mut()
                {
                    first.text.clone_from(&url);
                    spans.truncate(1);
                }
                result.push(Segment {
                    spans,
                    url: (!literal).then_some(url),
                });
            } else {
                *omitted = true;
                let styles = self
                    .runs
                    .get(self.runs.partition_point(|run| run.end <= link.start()))
                    .map_or_else(Vec::new, |run| run.styles.clone());
                pending.push(AssistantSpan {
                    text: OMITTED_ADDRESS.into(),
                    styles,
                });
            }
            start = link.end();
        }
        pending.extend(self.spans(start..self.raw.len()));
        if !pending.is_empty() {
            result.push(Segment {
                spans: pending,
                url: None,
            });
        }
        result
    }

    fn spans(&self, range: Range<usize>) -> Vec<AssistantSpan> {
        let mut start = range.start;
        let first = self.runs.partition_point(|run| run.end <= start);
        let mut spans = Vec::new();
        for run in &self.runs[first..] {
            let end = run.end.min(range.end);
            if end <= start {
                break;
            }
            spans.push(AssistantSpan {
                text: self.raw[start..end].into(),
                styles: run.styles.clone(),
            });
            start = end;
        }
        spans
    }
}

pub(super) fn valid_item_id(id: &str) -> bool {
    id.strip_prefix("minecraft:").is_some_and(|path| {
        (1..=ITEM_PATH_LIMIT).contains(&path.len())
            && path.bytes().all(|byte| {
                byte.is_ascii_lowercase()
                    || byte.is_ascii_digit()
                    || matches!(byte, b'_' | b'-' | b'.' | b'/')
            })
    })
}
