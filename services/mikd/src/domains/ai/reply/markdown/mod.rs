mod annotations;
mod budget;
mod inline;
mod text;

#[cfg(test)]
mod tests;
#[cfg(test)]
pub(in crate::domains) use tests::sample;

use self::{annotations::Annotations, budget::Budget, inline::inline_event, text::Text};
use crate::domains::ai::reference::Reference;
use mik_protocol::wire::{
    AssistantBlock, AssistantBlocks, AssistantCodeBlock, AssistantDivider, AssistantHeading,
    AssistantInline, AssistantList, AssistantListItem, AssistantPart, AssistantReply, AssistantRow,
    AssistantSpan, AssistantTable, assistant_block::Content, assistant_span::Style,
    assistant_table::Alignment,
};
use pulldown_cmark::{CodeBlockKind, Event, Options, Parser, Tag, TagEnd};
use std::iter::Peekable;

const MARKDOWN_BYTES: usize = 32 * 1024;
const DEPTH_LIMIT: usize = 10;

pub(in crate::domains::ai) struct Projection {
    pub(in crate::domains::ai) reply: AssistantReply,
    pub(in crate::domains::ai) omitted: bool,
}

pub(in crate::domains::ai) fn project(
    output: &str,
    question: &str,
    references: &[Reference],
) -> Option<Projection> {
    let end = output.floor_char_boundary(MARKDOWN_BYTES);
    let mut document = Markdown {
        events: Parser::new_ext(
            output[..end].trim_matches('\u{feff}'),
            Options::ENABLE_TABLES | Options::ENABLE_STRIKETHROUGH | Options::ENABLE_TASKLISTS,
        )
        .peekable(),
        annotations: Annotations::new(question, references),
        budget: Budget::default(),
    };
    let mut blocks = document.blocks(0);
    if blocks.is_empty() {
        return None;
    }
    let truncated = document.budget.truncated() || end < output.len();
    if truncated {
        blocks.push(block(Content::Paragraph(AssistantInline {
            parts: vec![AssistantPart {
                spans: vec![AssistantSpan {
                    text: "…".into(),
                    styles: Vec::new(),
                }],
                ..Default::default()
            }],
        })));
    }
    Some(Projection {
        reply: AssistantReply { blocks },
        omitted: document.annotations.omitted() || document.budget.omitted() || truncated,
    })
}

struct Markdown<'a> {
    events: Peekable<Parser<'a>>,
    annotations: Annotations<'a>,
    budget: Budget,
}

impl Markdown<'_> {
    fn blocks(&mut self, depth: usize) -> Vec<AssistantBlock> {
        let mut blocks = Vec::new();
        while let Some(event) = self.events.peek() {
            if matches!(event, Event::End(_)) {
                let _end = self.events.next();
                break;
            }
            if !self.budget.node() {
                skip(&mut self.events);
                break;
            }
            if let Some(content) = self.content(depth) {
                blocks.push(block(content));
            }
        }
        blocks
    }

    fn content(&mut self, depth: usize) -> Option<Content> {
        match self.events.peek()? {
            Event::Start(Tag::List(_) | Tag::BlockQuote(_)) if depth >= DEPTH_LIMIT => {
                let _start = self.events.next();
                let inline = self.flatten_container()?;
                Some(Content::Paragraph(inline))
            }
            Event::Start(Tag::List(number)) => {
                let number = *number;
                let _start = self.events.next();
                self.list(number, depth + 1)
            }
            Event::Start(Tag::BlockQuote(_)) => {
                let _start = self.events.next();
                let blocks = self.blocks(depth + 1);
                (!blocks.is_empty()).then_some(Content::BlockQuote(AssistantBlocks { blocks }))
            }
            Event::Start(Tag::Heading { level, .. }) => {
                let level = *level as u32;
                let body = self.wrapped_inline()?;
                Some(Content::Heading(AssistantHeading {
                    level,
                    body: Some(body),
                }))
            }
            Event::Start(Tag::CodeBlock(_)) => {
                let Some(Event::Start(Tag::CodeBlock(kind))) = self.events.next() else {
                    return None;
                };
                self.code(kind)
            }
            Event::Start(Tag::Table(_)) => {
                let Some(Event::Start(Tag::Table(columns))) = self.events.next() else {
                    return None;
                };
                self.table(columns)
            }
            Event::Start(Tag::Paragraph | Tag::HtmlBlock) => {
                self.wrapped_inline().map(Content::Paragraph)
            }
            Event::Rule => {
                let _rule = self.events.next();
                Some(Content::Divider(AssistantDivider {}))
            }
            event if inline_event(event) => self.inline().map(Content::Paragraph),
            _ => {
                if matches!(self.events.next(), Some(Event::Start(_))) {
                    skip(&mut self.events);
                }
                None
            }
        }
    }

    fn list(&mut self, number: Option<u64>, depth: usize) -> Option<Content> {
        let mut children = Vec::new();
        while let Some(event) = self.events.next() {
            match event {
                Event::Start(Tag::Item) => {
                    if !self.budget.node() {
                        skip(&mut self.events);
                        skip(&mut self.events);
                        break;
                    }
                    let wrapped = self
                        .events
                        .next_if(|event| matches!(event, Event::Start(Tag::Paragraph)))
                        .is_some();
                    let checked = match self
                        .events
                        .next_if(|event| matches!(event, Event::TaskListMarker(_)))
                    {
                        Some(Event::TaskListMarker(checked)) => Some(checked),
                        _ => None,
                    };
                    let mut blocks = Vec::new();
                    if wrapped {
                        if let Some(body) = self.inline() {
                            blocks.push(block(Content::Paragraph(body)));
                        }
                        let _end = self.events.next();
                    }
                    blocks.extend(self.blocks(depth));
                    if !blocks.is_empty() || checked.is_some() {
                        children.push(AssistantListItem { blocks, checked });
                    }
                }
                Event::End(TagEnd::List(_)) => break,
                _ => {}
            }
        }
        (!children.is_empty()).then_some(Content::List(AssistantList {
            first_number: number.and_then(|number| u32::try_from(number).ok()),
            children,
        }))
    }

    fn table(&mut self, columns: Vec<pulldown_cmark::Alignment>) -> Option<Content> {
        if columns.len() > self.budget.nodes_left() {
            return self.flatten_container().map(Content::Paragraph);
        }
        let mut header = None;
        let mut rows = Vec::new();
        while let Some(event) = self.events.next() {
            match event {
                Event::Start(Tag::TableHead) => header = Some(self.row()),
                Event::Start(Tag::TableRow) => {
                    if !self.budget.node() {
                        skip(&mut self.events);
                        skip(&mut self.events);
                        break;
                    }
                    rows.push(self.row());
                }
                Event::End(TagEnd::Table) => break,
                _ => {}
            }
        }
        Some(Content::Table(AssistantTable {
            columns: columns
                .into_iter()
                .map(|align| match align {
                    pulldown_cmark::Alignment::None => Alignment::Unspecified,
                    pulldown_cmark::Alignment::Left => Alignment::Left,
                    pulldown_cmark::Alignment::Center => Alignment::Center,
                    pulldown_cmark::Alignment::Right => Alignment::Right,
                } as i32)
                .collect(),
            header,
            rows,
        }))
    }

    fn row(&mut self) -> AssistantRow {
        let mut cells = Vec::new();
        while let Some(event) = self.events.next() {
            match event {
                Event::Start(Tag::TableCell) => {
                    if !self.budget.node() {
                        skip(&mut self.events);
                        skip(&mut self.events);
                        break;
                    }
                    cells.push(self.inline().unwrap_or_default());
                    let _end = self.events.next();
                }
                Event::End(TagEnd::TableHead | TagEnd::TableRow) => break,
                _ => {}
            }
        }
        AssistantRow { cells }
    }

    fn code(&mut self, kind: CodeBlockKind<'_>) -> Option<Content> {
        let language = match kind {
            CodeBlockKind::Fenced(info) => info
                .split_ascii_whitespace()
                .next()
                .filter(|name| {
                    name.len() <= 32
                        && name.bytes().all(|byte| {
                            byte.is_ascii_alphanumeric()
                                || matches!(byte, b'_' | b'-' | b'+' | b'#')
                        })
                })
                .unwrap_or_default()
                .to_owned(),
            CodeBlockKind::Indented => String::new(),
        };
        let mut body = Text::default();
        for event in self.events.by_ref() {
            match event {
                Event::Text(value) => body.push(&value, &[Style::Code]),
                Event::End(TagEnd::CodeBlock) => break,
                _ => {}
            }
        }
        let mut code: String = self
            .annotations
            .segments(&body)
            .into_iter()
            .flat_map(|piece| piece.spans)
            .map(|mut span| {
                self.budget.text(&mut span.text);
                span.text
            })
            .collect();
        if code.ends_with('\n') {
            code.pop();
        }
        (!code.trim().is_empty()).then_some(Content::Code(AssistantCodeBlock {
            text: code,
            language,
        }))
    }
}

fn block(content: Content) -> AssistantBlock {
    AssistantBlock {
        content: Some(content),
    }
}

fn skip<'a>(events: &mut impl Iterator<Item = Event<'a>>) {
    let mut depth = 0_usize;
    for event in events {
        match event {
            Event::Start(_) => depth += 1,
            Event::End(_) if depth == 0 => break,
            Event::End(_) => depth -= 1,
            _ => {}
        }
    }
}
