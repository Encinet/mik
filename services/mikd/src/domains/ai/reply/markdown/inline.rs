use super::{
    Markdown,
    text::{OMITTED_ADDRESS, Text},
};
use mik_protocol::wire::{
    AssistantInline, AssistantPart, assistant_part::Target, assistant_span::Style,
};
use pulldown_cmark::{Event, Tag, TagEnd};

impl Markdown<'_> {
    pub(super) fn wrapped_inline(&mut self) -> Option<AssistantInline> {
        let _start = self.events.next();
        let body = self.inline();
        let _end = self.events.next();
        body
    }

    pub(super) fn inline(&mut self) -> Option<AssistantInline> {
        let mut parts = Vec::new();
        let mut draft = Text::default();
        let mut styles = Vec::new();
        while let Some(event) = self.events.next_if(inline_event) {
            match event {
                Event::Start(Tag::Link { dest_url, .. }) => {
                    let label = link_label(&mut self.events, styles.clone());
                    self.link(&mut parts, &mut draft, &styles, &dest_url, label);
                }
                Event::Start(Tag::Image { .. }) => {
                    draft.append(link_label(&mut self.events, styles.clone()));
                }
                event => write_inline(event, &mut styles, &mut draft),
            }
        }
        self.flush(&mut parts, &mut draft);
        readable(parts)
    }

    pub(super) fn flatten_container(&mut self) -> Option<AssistantInline> {
        let mut depth = 0_usize;
        let mut text = Text::default();
        for event in self.events.by_ref() {
            match event {
                Event::Start(_) => depth += 1,
                Event::End(_) if depth == 0 => break,
                Event::End(tag) => {
                    depth -= 1;
                    if matches!(
                        tag,
                        TagEnd::Paragraph
                            | TagEnd::Item
                            | TagEnd::CodeBlock
                            | TagEnd::TableRow
                            | TagEnd::TableHead
                    ) {
                        if !text.as_str().ends_with('\n') {
                            text.push("\n", &[]);
                        }
                    } else if tag == TagEnd::TableCell {
                        text.push(" │ ", &[]);
                    }
                }
                Event::Text(value) | Event::Html(value) | Event::InlineHtml(value) => {
                    text.push(&value, &[]);
                }
                Event::Code(value) => text.push(&value, &[Style::Code]),
                Event::HardBreak => text.push("\n", &[]),
                Event::SoftBreak => text.push(" ", &[]),
                _ => {}
            }
        }
        let mut parts = Vec::new();
        self.flush(&mut parts, &mut text);
        readable(parts)
    }

    fn link(
        &mut self,
        parts: &mut Vec<AssistantPart>,
        draft: &mut Text,
        styles: &[Style],
        target: &str,
        mut label: Text,
    ) {
        let Some(annotation) = self.annotations.resolve(target, label.as_str()) else {
            if label.as_str() != target {
                draft.append(label);
            } else if !target.is_empty() && !target.starts_with("ref:") {
                draft.push(OMITTED_ADDRESS, styles);
            }
            return;
        };
        if let Some(replacement) = annotation.label {
            label = Text::default();
            label.push(&replacement, styles);
        }
        if annotation.target.is_none() && annotation.item_id.is_empty() {
            draft.append(label);
            return;
        }
        self.flush(parts, draft);
        let spans = self
            .annotations
            .segments(&label)
            .into_iter()
            .flat_map(|part| part.spans)
            .collect();
        self.budget.push(
            parts,
            AssistantPart {
                spans,
                target: annotation.target,
                item_id: annotation.item_id,
            },
        );
    }

    fn flush(&mut self, parts: &mut Vec<AssistantPart>, draft: &mut Text) {
        for segment in self.annotations.segments(&std::mem::take(draft)) {
            self.budget.push(
                parts,
                AssistantPart {
                    spans: segment.spans,
                    target: segment.url.map(Target::Url),
                    item_id: String::new(),
                },
            );
        }
    }
}

fn readable(parts: Vec<AssistantPart>) -> Option<AssistantInline> {
    parts
        .iter()
        .flat_map(|part| &part.spans)
        .any(|span| !span.text.trim().is_empty())
        .then_some(AssistantInline { parts })
}

pub(super) fn inline_event(event: &Event<'_>) -> bool {
    matches!(
        event,
        Event::Start(
            Tag::Emphasis | Tag::Strong | Tag::Strikethrough | Tag::Link { .. } | Tag::Image { .. }
        ) | Event::End(TagEnd::Emphasis | TagEnd::Strong | TagEnd::Strikethrough)
            | Event::Text(_)
            | Event::Code(_)
            | Event::Html(_)
            | Event::InlineHtml(_)
            | Event::SoftBreak
            | Event::HardBreak
    )
}

fn write_inline(event: Event<'_>, styles: &mut Vec<Style>, draft: &mut Text) {
    match event {
        Event::Start(Tag::Emphasis) => styles.push(Style::Italic),
        Event::Start(Tag::Strong) => styles.push(Style::Bold),
        Event::Start(Tag::Strikethrough) => styles.push(Style::Strikethrough),
        Event::End(TagEnd::Emphasis | TagEnd::Strong | TagEnd::Strikethrough) => {
            let _style = styles.pop();
        }
        Event::Text(text) | Event::Html(text) | Event::InlineHtml(text) => {
            draft.push(&text, styles);
        }
        Event::Code(text) => {
            styles.push(Style::Code);
            draft.push(&text, styles);
            let _style = styles.pop();
        }
        Event::SoftBreak => draft.push(" ", styles),
        Event::HardBreak => draft.push("\n", styles),
        _ => {}
    }
}

fn link_label<'a>(events: &mut impl Iterator<Item = Event<'a>>, mut styles: Vec<Style>) -> Text {
    let mut label = Text::default();
    let mut depth = 0_usize;
    for event in events {
        match event {
            Event::Start(_) => depth += 1,
            Event::End(_) if depth == 0 => break,
            Event::End(_) => depth -= 1,
            _ => {}
        }
        write_inline(event, &mut styles, &mut label);
    }
    label
}
