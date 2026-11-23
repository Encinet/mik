use super::super::render::{markdown_text, markdown_url};
use crate::domains::social::chat::escape_qq;
use mik_protocol::wire::{
    AssistantBlock, AssistantCodeBlock, AssistantInline, AssistantList, AssistantReply,
    AssistantRow, AssistantSpan, AssistantTable, assistant_block::Content, assistant_part::Target,
    assistant_span::Style, assistant_table::Alignment,
};

pub(super) fn answer(reply: &AssistantReply) -> (String, bool) {
    let mut writer = Document::default();
    writer.blocks(&reply.blocks);
    (writer.markdown, writer.rich)
}

#[derive(Default)]
struct Document {
    markdown: String,
    rich: bool,
}

impl Document {
    fn blocks(&mut self, blocks: &[AssistantBlock]) {
        for (index, content) in blocks
            .iter()
            .filter_map(|block| block.content.as_ref())
            .enumerate()
        {
            if index > 0 {
                self.markdown.push_str("\n\n");
            }
            self.rich |= !matches!(content, Content::Paragraph(_));
            match content {
                Content::Paragraph(body) => self.inline(body, false),
                Content::Heading(heading) => {
                    if let Some(body) = &heading.body {
                        self.markdown
                            .extend(std::iter::repeat_n('#', heading.level.clamp(1, 6) as usize));
                        self.markdown.push(' ');
                        self.inline(body, false);
                    }
                }
                Content::BlockQuote(quote) => {
                    let mut nested = Self::default();
                    nested.blocks(&quote.blocks);
                    self.markdown.push_str("> ");
                    lines(&mut self.markdown, &nested.markdown, "> ");
                }
                Content::List(list) => self.list(list),
                Content::Code(code) => self.code(code),
                Content::Table(table) => self.table(table),
                Content::Divider(_) => self.markdown.push_str("---"),
            }
        }
    }

    fn inline(&mut self, body: &AssistantInline, in_table: bool) {
        let mut writer = Inline::new(&mut self.markdown, in_table);
        for part in &body.parts {
            self.rich |= part.spans.iter().any(|span| !span.styles.is_empty());
            if let Some(Target::Url(url)) = &part.target {
                self.rich = true;
                writer.link(&part.spans, url);
            } else {
                for span in &part.spans {
                    writer.span(span, 0);
                }
            }
        }
        writer.finish();
    }

    fn list(&mut self, list: &AssistantList) {
        for (index, item) in list.children.iter().enumerate() {
            if index != 0 {
                self.markdown.push('\n');
            }
            let marker = list.first_number.map_or_else(
                || "- ".into(),
                |number| format!("{}. ", u64::from(number) + index as u64),
            );
            self.markdown.push_str(&marker);
            match item.checked {
                Some(true) => self.markdown.push_str("[x] "),
                Some(false) => self.markdown.push_str("[ ] "),
                None => (),
            }
            let mut nested = Self::default();
            nested.blocks(&item.blocks);
            lines(
                &mut self.markdown,
                &nested.markdown,
                &" ".repeat(marker.len()),
            );
        }
    }

    fn code(&mut self, code: &AssistantCodeBlock) {
        let fence = "`".repeat((backtick_run(&code.text) + 1).max(3));
        self.markdown.push_str(&fence);
        self.markdown.push_str(&code.language);
        self.markdown.push('\n');
        self.markdown.push_str(&escape_qq(&code.text));
        self.markdown.push('\n');
        self.markdown.push_str(&fence);
    }

    fn table(&mut self, table: &AssistantTable) {
        let Some(header) = &table.header else { return };
        self.row(header, table.columns.len());
        self.markdown.push_str("\n|");
        for &column in &table.columns {
            self.markdown
                .push_str(match Alignment::try_from(column).unwrap_or_default() {
                    Alignment::Unspecified => " --- |",
                    Alignment::Left => " :--- |",
                    Alignment::Center => " :---: |",
                    Alignment::Right => " ---: |",
                });
        }
        for row in &table.rows {
            self.markdown.push('\n');
            self.row(row, table.columns.len());
        }
    }

    fn row(&mut self, row: &AssistantRow, columns: usize) {
        self.markdown.push('|');
        for column in 0..columns {
            self.markdown.push(' ');
            if let Some(cell) = row.cells.get(column) {
                self.inline(cell, true);
            }
            self.markdown.push_str(" |");
        }
    }
}

struct Inline<'a> {
    markdown: &'a mut String,
    styles: Vec<i32>,
    space: String,
    in_table: bool,
}

impl<'a> Inline<'a> {
    fn new(markdown: &'a mut String, in_table: bool) -> Self {
        Self {
            markdown,
            styles: Vec::new(),
            space: String::new(),
            in_table,
        }
    }

    fn span(&mut self, span: &AssistantSpan, skip: usize) {
        let styles = &span.styles[skip..];
        if let Some(outer) = styles.strip_suffix(&[Style::Code as i32]) {
            self.transition(outer);
            let text = escape_qq(&span.text);
            if self.in_table {
                code_span(self.markdown, &text.replace('|', r"\|"));
            } else {
                code_span(self.markdown, &text);
            }
            return;
        }
        for line in span.text.split_inclusive('\n') {
            let (body, line_break) = line
                .strip_suffix('\n')
                .map_or((line, false), |body| (body, true));
            let trimmed = body.trim();
            if trimmed.is_empty() {
                self.space.push_str(body);
            } else {
                let start = body.len() - body.trim_start().len();
                self.space.push_str(&body[..start]);
                self.transition(styles);
                markdown_text(self.markdown, trimmed);
                self.space.push_str(&body[start + trimmed.len()..]);
            }
            if line_break {
                self.transition(&[]);
                if self.in_table {
                    self.markdown.push(' ');
                } else {
                    self.markdown.push_str("  \n");
                }
            }
        }
    }

    fn link(&mut self, spans: &[AssistantSpan], url: &str) {
        let Some(first) = spans.first() else { return };
        let common = spans.iter().fold(first.styles.as_slice(), |shared, span| {
            &shared[..shared_prefix(shared, &span.styles)]
        });
        let outer = common.strip_suffix(&[Style::Code as i32]).unwrap_or(common);
        self.transition(outer);
        self.markdown.push('[');
        let mut label = Inline::new(self.markdown, self.in_table);
        for span in spans {
            label.span(span, outer.len());
        }
        label.finish();
        self.markdown.push_str("](");
        markdown_url(self.markdown, url);
        self.markdown.push(')');
    }

    fn transition(&mut self, desired: &[i32]) {
        let shared = shared_prefix(&self.styles, desired);
        for &style in self.styles[shared..].iter().rev() {
            self.markdown.push_str(delimiter(style));
        }
        markdown_text(self.markdown, &self.space);
        self.space.clear();
        self.styles.truncate(shared);
        for &style in &desired[shared..] {
            self.markdown.push_str(delimiter(style));
            self.styles.push(style);
        }
    }

    fn finish(mut self) {
        self.transition(&[]);
    }
}

fn shared_prefix(left: &[i32], right: &[i32]) -> usize {
    left.iter()
        .zip(right)
        .take_while(|(left, right)| left == right)
        .count()
}

fn delimiter(style: i32) -> &'static str {
    match Style::try_from(style).unwrap_or_default() {
        Style::Bold => "**",
        Style::Italic => "*",
        Style::Strikethrough => "~~",
        Style::Code | Style::Unspecified => "",
    }
}

fn backtick_run(text: &str) -> usize {
    let mut longest = 0;
    let mut run = 0;
    for character in text.chars() {
        run = if character == '`' { run + 1 } else { 0 };
        longest = longest.max(run);
    }
    longest
}

fn code_span(markdown: &mut String, text: &str) {
    let ticks = "`".repeat(backtick_run(text) + 1);
    let pad = text.starts_with(['`', ' ']) || text.ends_with(['`', ' ']);
    markdown.push_str(&ticks);
    if pad && !text.chars().all(|character| character == ' ') {
        markdown.push(' ');
        markdown.push_str(text);
        markdown.push(' ');
    } else {
        markdown.push_str(text);
    }
    markdown.push_str(&ticks);
}

fn lines(markdown: &mut String, text: &str, indent: &str) {
    let mut lines = text.split('\n');
    markdown.push_str(lines.next().unwrap_or_default());
    for line in lines {
        markdown.push('\n');
        if !line.is_empty() {
            markdown.push_str(indent);
            markdown.push_str(line);
        } else if indent.starts_with('>') {
            markdown.push_str(indent.trim_end());
        }
    }
}
