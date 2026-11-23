use super::{questions, visible::VisibleReply};
use mik_protocol::wire::{
    AssistantBlock, AssistantInline, AssistantList, AssistantReply, AssistantTable,
    assistant_block::Content, assistant_part::Target,
};

#[derive(Clone)]
pub(crate) struct Layout {
    pub(crate) visible: VisibleReply,
    follow_ups: Vec<(String, usize)>,
}

impl Layout {
    pub(crate) fn new(reply: &AssistantReply, display: fn(&str) -> String) -> Self {
        let mut formatter = Formatter {
            layout: Self {
                visible: VisibleReply::default(),
                follow_ups: Vec::new(),
            },
            display,
        };
        formatter.blocks(&reply.blocks, "");
        formatter.layout
    }

    pub(crate) fn clip(&mut self, limit: usize) {
        if let Some(end) = self.visible.clip(limit) {
            self.follow_ups.retain(|(_, offset)| *offset <= end);
        }
    }

    pub(crate) fn follow_ups(&self) -> Vec<String> {
        self.follow_ups
            .iter()
            .map(|(question, _)| question.clone())
            .collect()
    }
}

pub(in crate::domains::ai) fn paper_history(reply: &AssistantReply) -> VisibleReply {
    Layout::new(reply, str::to_owned).visible
}

struct Formatter {
    layout: Layout,
    display: fn(&str) -> String,
}

impl Formatter {
    fn blocks(&mut self, blocks: &[AssistantBlock], prefix: &str) {
        for (index, content) in blocks
            .iter()
            .filter_map(|block| block.content.as_ref())
            .enumerate()
        {
            if index > 0 {
                self.layout
                    .visible
                    .push(&format!("\n{}\n{prefix}", prefix.trim_end()));
            }
            match content {
                Content::Paragraph(inline) => self.inline(inline, prefix),
                Content::Heading(heading) => {
                    if let Some(inline) = &heading.body {
                        self.inline(inline, prefix);
                    }
                }
                Content::BlockQuote(quote) => {
                    self.layout.visible.push("│ ");
                    self.blocks(&quote.blocks, &format!("{prefix}│ "));
                }
                Content::List(list) => self.list(list, prefix),
                Content::Code(code) => {
                    self.layout
                        .visible
                        .push(&with_prefix(&(self.display)(&code.text), prefix));
                }
                Content::Divider(_) => self.layout.visible.push("────"),
                Content::Table(table) => self.table(table, prefix),
            }
        }
    }

    fn inline(&mut self, inline: &AssistantInline, prefix: &str) {
        for part in &inline.parts {
            let label: String = part.spans.iter().map(|span| span.text.as_str()).collect();
            let shown = with_prefix(&(self.display)(&label), prefix);
            if let Some(Target::Url(url)) = &part.target {
                self.layout.visible.push_link(&shown, url);
            } else {
                self.layout.visible.push(&shown);
                if matches!(part.target, Some(Target::AskLabel(_))) && questions::valid(&label) {
                    self.layout
                        .follow_ups
                        .push((label, self.layout.visible.as_str().len()));
                }
            }
        }
    }

    fn list(&mut self, list: &AssistantList, prefix: &str) {
        for (index, item) in list.children.iter().enumerate() {
            if index > 0 {
                self.layout.visible.push(&format!("\n{prefix}"));
            }
            let mut marker = list.first_number.map_or_else(
                || "• ".to_owned(),
                |number| format!("{}. ", u64::from(number) + index as u64),
            );
            match item.checked {
                Some(true) => marker.push_str("☑ "),
                Some(false) => marker.push_str("☐ "),
                None => (),
            }
            self.layout.visible.push(&marker);
            self.blocks(
                &item.blocks,
                &format!("{prefix}{}", " ".repeat(marker.chars().count())),
            );
        }
    }

    fn table(&mut self, table: &AssistantTable, prefix: &str) {
        for (index, row) in table.header.iter().chain(&table.rows).enumerate() {
            if index > 0 {
                self.layout.visible.push(&format!("\n{prefix}"));
            }
            for (column, cell) in row.cells.iter().enumerate() {
                if column > 0 {
                    self.layout.visible.push(" │ ");
                }
                self.inline(cell, prefix);
            }
        }
    }
}

fn with_prefix(text: &str, prefix: &str) -> String {
    if prefix.is_empty() {
        text.into()
    } else {
        text.replace('\n', &format!("\n{prefix}"))
    }
}
