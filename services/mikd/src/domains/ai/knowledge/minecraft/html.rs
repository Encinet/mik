//! Turns a `MediaWiki` section fragment into text for the Minecraft Wiki tool.

use htmd::{
    Element, HtmlToMarkdown,
    element_handler::{Context, HandlerResult, Handlers},
    options::{BulletListMarker, CodeBlockStyle, Options},
};
use html5ever::{
    Attribute, LocalName, ParseOpts, QualName, local_name, ns, parse_document, tendril::TendrilSink,
};
use markup5ever_rcdom::{Node, NodeData, RcDom};
use std::{cell::RefCell, io, rc::Rc};

const MAX_HTML_BYTES: usize = 512 * 1024;
const MAX_DEPTH: usize = 96;
const MAX_EXCERPT_BYTES: usize = 6 * 1024;
const MAX_LABEL_HTML_BYTES: usize = 4 * 1024;
const MAX_LABEL_BYTES: usize = 240;
const MAX_TABLE_ROWS: usize = 24;
const MAX_TABLE_COLUMNS: usize = 16;
const MAX_TABLE_CONTENT: usize = 4096;

/// Returns the Markdown and whether the input or output exceeded its byte budget.
pub(super) fn excerpt(markup: &str) -> io::Result<(String, bool)> {
    let end = markup.floor_char_boundary(MAX_HTML_BYTES);
    let document = parse(&markup[..end]);
    clean(&document, 0)?;
    let mut markdown = HtmlToMarkdown::builder()
        .options(Options {
            bullet_list_marker: BulletListMarker::Dash,
            code_block_style: CodeBlockStyle::Indented,
            ..Options::default()
        })
        .add_handler(vec!["a"], |handlers: &dyn Handlers, element: &Element| {
            Some(handlers.walk_children(element.node, element.context))
        })
        .add_handler(vec!["span"], crafting_span)
        .build()
        .convert(document)?;
    markdown.retain(|ch| !ch.is_control() || matches!(ch, '\n' | '\t'));
    let (markdown, clipped) = limit_excerpt(markdown.trim_end());
    Ok((markdown.to_owned(), clipped || end < markup.len()))
}

pub(super) fn heading(markup: &str) -> String {
    let document = parse(&markup[..markup.floor_char_boundary(MAX_LABEL_HTML_BYTES)]);
    let mut pending = vec![Rc::clone(&document)];
    let mut text = String::new();
    while let Some(node) = pending.pop() {
        if !visible(&node) {
            continue;
        }
        if let NodeData::Text { contents } = &node.data {
            text.push_str(&contents.borrow());
        } else if tag(&node) == "br" {
            text.push(' ');
        }
        pending.extend(node.children.borrow().iter().rev().cloned());
    }
    label(&text)
}

fn parse(markup: &str) -> Rc<Node> {
    parse_document(RcDom::default(), ParseOpts::default())
        .one(markup)
        .document
}

fn clean(node: &Rc<Node>, depth: usize) -> io::Result<()> {
    if depth > MAX_DEPTH {
        return Err(io::Error::new(
            io::ErrorKind::InvalidData,
            "Minecraft Wiki markup nests too deeply",
        ));
    }
    let mut children = node.children.borrow_mut();
    children.retain(|child| visible(child));
    for child in children.iter_mut() {
        if tag(child) == "img" {
            // Let htmd escape the label as ordinary text; never emit an image destination.
            let text = image_label(child, node);
            *child = Node::new(NodeData::Text {
                contents: RefCell::new(text.as_str().into()),
            });
        } else {
            clean(child, depth + 1)?;
            if tag(child) != "table" || prepare_table(child).is_some() {
                continue;
            }
            // Markdown cannot faithfully represent merged cells. Keep their contents
            // in order, also avoiding htmd's column padding on oversized tables.
            *child = retag(child, local_name!("div"));
        }
        child.parent.set(Some(Rc::downgrade(node)));
    }
    Ok(())
}

fn visible(node: &Node) -> bool {
    let NodeData::Element { name, attrs, .. } = &node.data else {
        return !matches!(node.data, NodeData::Comment { .. });
    };
    if matches!(
        &*name.local,
        "head"
            | "script"
            | "style"
            | "noscript"
            | "template"
            | "svg"
            | "canvas"
            | "iframe"
            | "object"
            | "embed"
            | "frame"
            | "frameset"
            | "portal"
            | "audio"
            | "video"
            | "source"
            | "nav"
            | "footer"
            | "aside"
            | "form"
            | "dialog"
            | "button"
            | "select"
            | "textarea"
    ) {
        return false;
    }
    let attrs = attrs.borrow();
    attr(&attrs, "hidden").is_none()
        && !attr(&attrs, "aria-hidden")
            .is_some_and(|value| value.trim().eq_ignore_ascii_case("true"))
        && !attr(&attrs, "role").is_some_and(|value| {
            value
                .split_ascii_whitespace()
                .any(|role| matches!(role, "navigation" | "banner" | "complementary" | "dialog"))
        })
        && !attr(&attrs, "class").is_some_and(|value| {
            value.split_ascii_whitespace().any(|class| {
                matches!(
                    class,
                    "mw-editsection"
                        | "toc"
                        | "navbox"
                        | "vertical-navbox"
                        | "searchaux"
                        | "noexcerpt"
                        | "navigation-not-searchable"
                        | "infobox-imagearea"
                        | "infobox-extratext"
                )
            })
        })
        && !attr(&attrs, "style").is_some_and(hidden_style)
}

fn hidden_style(style: &str) -> bool {
    style
        .split(';')
        .filter_map(|property| property.split_once(':'))
        .any(|(name, value)| {
            let name = name.trim();
            let value = value.split('!').next().unwrap_or_default().trim();
            (name.eq_ignore_ascii_case("display") && value.eq_ignore_ascii_case("none"))
                || (name.eq_ignore_ascii_case("visibility") && value.eq_ignore_ascii_case("hidden"))
        })
}

fn image_label(image: &Node, parent: &Node) -> String {
    let NodeData::Element { attrs, .. } = &image.data else {
        return String::new();
    };
    let attrs = attrs.borrow();
    let alt = attr(&attrs, "alt").or_else(|| attr(&attrs, "title"));
    // Inventory sprites have a lengthy generated alt description. Use the localized
    // item name on their wrapping link/span when the wiki supplies one.
    if alt.is_none_or(|alt| alt.starts_with("Invicon "))
        && let NodeData::Element { attrs, .. } = &parent.data
        && let Some(title) = attr(&attrs.borrow(), "title").map(str::trim)
        && !title.is_empty()
        && !title.starts_with("File:")
        && !title.starts_with("文件:")
    {
        return label(title);
    }
    label(alt.unwrap_or_default())
}

fn crafting_span(handlers: &dyn Handlers, element: &Element) -> Option<HandlerResult> {
    if let Some(classes) = element.node.attr("class") {
        if has_class(&classes, "mcui-arrow") {
            return Some(" → ".into());
        }
        if has_class(&classes, "mcui-row") {
            let slots: Vec<_> = element
                .node
                .children()
                .iter()
                .filter(|node| {
                    node.attr("class")
                        .is_some_and(|value| has_class(&value, "invslot"))
                })
                .map(|slot| {
                    let item = handlers.walk_children_content(&slot, Context::INLINE);
                    let item = item.trim();
                    if item.is_empty() {
                        "·".to_owned()
                    } else {
                        item.to_owned()
                    }
                })
                .collect();
            if !slots.is_empty() {
                return Some(format!(" [{}] ", slots.join(" / ")).into());
            }
        }
    }
    handlers.fallback(element)
}

fn has_class(classes: &str, name: &str) -> bool {
    classes.split_ascii_whitespace().any(|class| class == name)
}

fn prepare_table(table: &Rc<Node>) -> Option<()> {
    let mut children = table.children.borrow_mut();
    let mut rows = Vec::new();
    let mut budget = MAX_TABLE_CONTENT;
    for section in children.iter().filter(|node| table_section(node)) {
        for row in section
            .children
            .borrow()
            .iter()
            .filter(|node| tag(node) == "tr")
        {
            let cells = row.children.borrow();
            let count = cells.iter().filter(|node| table_cell(node)).count();
            if count == 0 {
                continue;
            }
            if rows.len() == MAX_TABLE_ROWS
                || count > MAX_TABLE_COLUMNS
                || table_cost(row, &mut budget).is_none()
            {
                return None;
            }
            rows.push((Rc::clone(row), tag(section) == "thead"));
        }
    }
    let (first, in_head) = rows.first()?;
    let header = *in_head
        || first
            .children
            .borrow()
            .iter()
            .filter(|cell| table_cell(cell))
            .all(|cell| tag(cell) == "th");

    // htmd expects one header row, and drops <th> cells in body rows. Normalize
    // the shape so infobox row labels and values both survive, even without a header.
    for section in children.iter().filter(|node| table_section(node)) {
        section.children.borrow_mut().clear();
    }
    children.retain(|node| tag(node) == "caption");
    if !header {
        let row = empty_element(local_name!("tr"));
        let cell = empty_element(local_name!("th"));
        cell.parent.set(Some(Rc::downgrade(&row)));
        row.children.borrow_mut().push(cell);
        row.parent.set(Some(Rc::downgrade(table)));
        children.push(row);
    }
    for (index, (row, _)) in rows.into_iter().enumerate() {
        let name = if index == 0 && header {
            local_name!("th")
        } else {
            local_name!("td")
        };
        for cell in row
            .children
            .borrow_mut()
            .iter_mut()
            .filter(|node| table_cell(node))
        {
            if tag(cell) != &*name {
                *cell = retag(cell, name.clone());
                cell.parent.set(Some(Rc::downgrade(&row)));
            }
        }
        row.parent.set(Some(Rc::downgrade(table)));
        children.push(row);
    }
    Some(())
}

fn table_section(node: &Node) -> bool {
    matches!(tag(node), "thead" | "tbody" | "tfoot")
}

fn table_cell(node: &Node) -> bool {
    matches!(tag(node), "th" | "td")
}

fn table_cost(node: &Node, budget: &mut usize) -> Option<()> {
    let cost = match &node.data {
        NodeData::Text { contents } => contents.borrow().len(),
        NodeData::Element { name, attrs, .. } => {
            if &*name.local == "table"
                || matches!(&*name.local, "th" | "td") && {
                    let attrs = attrs.borrow();
                    ["rowspan", "colspan"]
                        .iter()
                        .any(|key| attr(&attrs, key).is_some_and(|value| value.trim() != "1"))
                }
            {
                return None;
            }
            // Allow for Markdown added by the converter, even for empty elements.
            4
        }
        _ => 0,
    };
    *budget = budget.checked_sub(cost)?;
    for child in node.children.borrow().iter() {
        table_cost(child, budget)?;
    }
    Some(())
}

fn empty_element(name: LocalName) -> Rc<Node> {
    Node::new(NodeData::Element {
        name: QualName::new(None, ns!(html), name),
        attrs: RefCell::default(),
        template_contents: RefCell::default(),
        mathml_annotation_xml_integration_point: false,
    })
}

fn retag(old: &Node, name: LocalName) -> Rc<Node> {
    let replacement = empty_element(name);
    let children = old.children.take();
    for child in &children {
        child.parent.set(Some(Rc::downgrade(&replacement)));
    }
    *replacement.children.borrow_mut() = children;
    replacement
}

fn tag(node: &Node) -> &str {
    if let NodeData::Element { name, .. } = &node.data {
        &name.local
    } else {
        ""
    }
}

fn attr<'a>(attrs: &'a [Attribute], key: &str) -> Option<&'a str> {
    attrs
        .iter()
        .find(|attr| &*attr.name.local == key)
        .map(|attr| &*attr.value)
}

fn label(text: &str) -> String {
    let mut result = String::new();
    let mut space = false;
    for ch in text.chars() {
        if ch.is_whitespace() {
            space = !result.is_empty();
        } else if !ch.is_control() {
            if result.len() + usize::from(space) + ch.len_utf8() > MAX_LABEL_BYTES {
                break;
            }
            if space {
                result.push(' ');
                space = false;
            }
            result.push(ch);
        }
    }
    result
}

fn limit_excerpt(markdown: &str) -> (&str, bool) {
    // Account for the surrounding JSON quotes and escaping in the tool response.
    let mut remaining = MAX_EXCERPT_BYTES - 2;
    for (offset, ch) in markdown.char_indices() {
        let width = match ch {
            '\\' | '"' | '\n' | '\t' => 2,
            _ => ch.len_utf8(),
        };
        let Some(rest) = remaining.checked_sub(width) else {
            let prefix = &markdown[..offset];
            // Keep a complete Markdown line (notably a table row) when possible.
            let prefix = if ch == '\n' {
                prefix
            } else {
                prefix
                    .rsplit_once('\n')
                    .filter(|(lines, _)| lines.len() >= offset / 2)
                    .map_or(prefix, |(lines, _)| lines)
            };
            return (prefix.trim_end(), true);
        };
        remaining = rest;
    }
    (markdown, false)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn crafting_keeps_the_recipe_and_discards_wiki_chrome() -> io::Result<()> {
        let (text, truncated) = excerpt(
            r#"
            <h3>合成<span class="mw-editsection">edit</span></h3>
            <table><tr><th>材料</th><th>配方</th></tr><tr><td><a href="/w/Stone">石头</a></td><td>
            <span class="mcui-row"><span class="invslot"></span><span class="searchaux">[[|]]</span><span class="invslot">
            <a title="红石火把" href="/w/Redstone_Torch"><img src="/images/torch.png" alt="Invicon Redstone Torch.png：描述：红石火把"></a>
            </span><span class="invslot"><img src="/images/stone.png" alt="石头"></span></span>
            <span class="mcui-arrow"><br></span><span title="红石中继器"><img alt="Invicon Repeater.png" src="/images/repeater.png"></span>
            </td></tr></table>
            <table><tr><th>堆叠</th><td>64</td></tr></table>
            <span style="Display: None !important">hidden</span><script>script</script>
        "#,
        )?;
        assert!(!truncated);
        assert!(text.contains("### 合成") && text.contains("| 材料"));
        assert!(text.contains("[· / 红石火把 / 石头]") && text.contains("→ 红石中继器"));
        assert!(
            text.lines()
                .any(|row| row.starts_with("| 堆叠") && row.contains("64"))
        );
        for unwanted in [
            "/w/", "/images/", "Invicon", "hidden", "script", "edit", "[[|]]",
        ] {
            assert!(!text.contains(unwanted), "unexpected {unwanted}: {text}");
        }
        Ok(())
    }

    #[test]
    fn labels_and_json_excerpts_stay_bounded() -> io::Result<()> {
        assert_eq!(
            heading("红<span>石</span><br> &amp; <i>合成</i><span hidden>edit</span>"),
            "红石 & 合成"
        );
        assert_eq!(heading(&"红石".repeat(1000)).len(), MAX_LABEL_BYTES);
        let (text, truncated) = excerpt(&format!("<p>{}</p>", r#"石"\"#.repeat(2048)))?;
        assert!(truncated && text.starts_with('石'));
        assert!(serde_json::to_string(&text)?.len() <= MAX_EXCERPT_BYTES);
        Ok(())
    }
}
