use super::{
    HistoryError, links, mentions,
    model::{MAX_BODY_PARTS, link_reference, person_mention, player_principal},
};
use mik_protocol::wire::{
    ChatBody, ChatPart, ChatPlayer, ChatTextRun, ChatTextStyle, chat_part::Content,
};
use std::ops::Range;

/// An ingress decides which text still needs interpretation. Resolved parts are never scanned.
pub(crate) enum BodyInput {
    Text(ChatTextRun),
    Resolved(ChatPart),
    OfflineMention {
        name: String,
        runs: Vec<ChatTextRun>,
    },
}

pub(super) fn assemble(
    input: impl IntoIterator<Item = BodyInput>,
    lookup: Option<mentions::Lookup<'_>>,
) -> Result<ChatBody, HistoryError> {
    let mut builder = BodyBuilder {
        parts: Vec::new(),
        remaining: mentions::RESOLUTION_LIMIT,
        lookup,
    };
    let mut text = String::new();
    let mut spans = Vec::new();
    for part in input {
        if let BodyInput::Text(run) = part {
            let start = text.len();
            text.push_str(&run.text);
            spans.push((start..text.len(), run.style));
            continue;
        }
        builder.parse_text(&text, &spans);
        text.clear();
        spans.clear();
        match part {
            BodyInput::Resolved(part) => builder.parts.push(part),
            BodyInput::OfflineMention { name, runs } => builder.offline_mention(&name, runs),
            BodyInput::Text(_) => {}
        }
        builder.check_size()?;
    }
    builder.parse_text(&text, &spans);
    builder.check_size()?;
    Ok(ChatBody {
        parts: builder.parts,
    })
}

type StyledRange = (Range<usize>, Option<ChatTextStyle>);

struct BodyBuilder<'a> {
    parts: Vec<ChatPart>,
    remaining: usize,
    lookup: Option<mentions::Lookup<'a>>,
}

impl BodyBuilder<'_> {
    fn check_size(&self) -> Result<(), HistoryError> {
        if self.parts.len() > MAX_BODY_PARTS {
            return Err(HistoryError::Invalid("invalid chat body size"));
        }
        Ok(())
    }

    fn push(&mut self, content: Content) {
        self.parts.push(ChatPart {
            content: Some(content),
        });
    }

    fn plain(&mut self, runs: impl IntoIterator<Item = ChatTextRun>) {
        self.parts.extend(runs.into_iter().map(|run| ChatPart {
            content: Some(Content::Text(run)),
        }));
    }

    fn player(
        &mut self,
        find: impl FnOnce(&mut mentions::Lookup<'_>) -> Option<ChatPlayer>,
    ) -> Option<ChatPlayer> {
        if self.remaining == 0 {
            return None;
        }
        let player = find(self.lookup.as_mut()?)?;
        self.remaining -= 1;
        Some(player)
    }

    fn offline_mention(&mut self, name: &str, runs: Vec<ChatTextRun>) {
        if let Some(player) = self.player(|lookup| lookup.resolve_offline(name)) {
            self.push(person_mention(player_principal(player), runs));
        } else {
            self.plain(runs);
        }
    }

    fn parse_text(&mut self, text: &str, spans: &[StyledRange]) {
        if text.is_empty() {
            return;
        }
        let mut head = 0;
        for link in links::find(text) {
            self.unlinked(text, spans, head..link.range.start);
            let runs = if link.label == text[link.range.clone()] {
                styled_runs(text, spans, link.range.clone()).collect()
            } else {
                let style = spans
                    .iter()
                    .find(|(range, _)| range.contains(&link.range.start))
                    .and_then(|(_, style)| *style);
                vec![ChatTextRun {
                    text: link.label,
                    style,
                }]
            };
            self.push(Content::Reference(link_reference(link.uri, runs, true)));
            head = link.range.end;
        }
        self.unlinked(text, spans, head..text.len());
    }

    fn unlinked(&mut self, text: &str, spans: &[StyledRange], range: Range<usize>) {
        let mut cursor = range.start;
        for (span, name) in mentions::candidates(text, range.clone()) {
            if links::inside_url(text, span.start) {
                continue;
            }
            let Some(player) = self.player(|lookup| lookup.resolve(name)) else {
                continue;
            };
            self.plain(styled_runs(text, spans, cursor..span.start));
            cursor = span.end;
            self.push(person_mention(
                player_principal(player),
                styled_runs(text, spans, span).collect(),
            ));
        }
        self.plain(styled_runs(text, spans, cursor..range.end));
    }
}

fn styled_runs<'a>(
    text: &'a str,
    spans: &'a [StyledRange],
    range: Range<usize>,
) -> impl Iterator<Item = ChatTextRun> + 'a {
    spans.iter().filter_map(move |(span, style)| {
        let start = range.start.max(span.start);
        let end = range.end.min(span.end);
        (start < end).then(|| ChatTextRun {
            text: text[start..end].into(),
            style: *style,
        })
    })
}
