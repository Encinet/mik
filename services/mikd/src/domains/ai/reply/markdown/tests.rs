use super::{Projection, Reference, budget::TEXT_LIMIT, project};
use crate::domains::ai::reply::paper_history;
use mik_protocol::wire::{
    AssistantCodeBlock, AssistantReply, assistant_block::Content, assistant_part::Target,
    assistant_table::Alignment,
};
use std::fmt::Write as _;

pub(in crate::domains) fn sample(markdown: &str) -> AssistantReply {
    fixture(markdown).reply
}

fn fixture(markdown: &str) -> Projection {
    project(
        markdown,
        "What's next?",
        &[Reference::new(
            "Guide".into(),
            Some("https://mcmik.top/wiki"),
        )],
    )
    .expect("readable sample")
}

#[test]
fn rich_blocks_and_verified_links_share_one_ordered_document() {
    let reply = sample(concat!(
        "> 7. [first **steps**](ref:r1)\n>    - [x] ~~done~~\n\n",
        "| Gear | Amount |\n| --- | ---: |\n| [Iron](item:minecraft:iron_block) | 3 |\n\n",
        "```sh\nset +e\n```\n\n",
        "See [the wiki](https://mcmik.top/wiki) or https://mcmik.top/wiki.\n",
        "Soft wrap.  \nHard break. [Bad](https://other.example.com/)"
    ));
    let [quote, table, code, paragraph] = reply.blocks.as_slice() else {
        panic!("{reply:?}")
    };
    let Some(Content::BlockQuote(quote)) = &quote.content else {
        panic!("expected a quote")
    };
    let Some(Content::List(list)) = &quote.blocks[0].content else {
        panic!("expected a list")
    };
    assert_eq!(list.first_number, Some(7));
    let Some(Content::List(tasks)) = &list.children[0].blocks[1].content else {
        panic!("expected tasks")
    };
    assert_eq!(tasks.children[0].checked, Some(true));
    let Some(Content::Table(table)) = &table.content else {
        panic!("expected a table")
    };
    assert_eq!(table.columns[1], Alignment::Right as i32);
    assert_eq!(
        table.rows[0].cells[0].parts[0].item_id,
        "minecraft:iron_block"
    );
    assert_eq!(
        code.content,
        Some(Content::Code(AssistantCodeBlock {
            text: "set +e".into(),
            language: "sh".into()
        }))
    );
    let Some(Content::Paragraph(paragraph)) = &paragraph.content else {
        panic!("expected text")
    };
    assert_eq!(
        paragraph
            .parts
            .iter()
            .filter(|part| matches!(part.target, Some(Target::Url(_))))
            .count(),
        2
    );
    assert!(
        paper_history(&reply)
            .as_str()
            .ends_with("Soft wrap.\nHard break. Bad")
    );
}

#[test]
fn filenames_and_literal_markup_survive_without_unverified_raw_addresses() {
    let original = r"See `plots.md`, [readme.md](ref:r1), \<ref:r1> and `<ref:r1>`. Before **https://unissued**.invalid/file after **关键🔧**.";
    let projection = fixture(original);
    assert!(projection.omitted);
    let history = paper_history(&projection.reply);
    assert_eq!(
        history.as_str(),
        "See plots.md, readme.md, <ref:r1> and <ref:r1>. Before […] after 关键🔧."
    );
    let references = serde_json::to_value(history).unwrap();
    assert_eq!(references["references"].as_array().unwrap().len(), 1);
    assert_eq!(references["references"][0]["label"], "readme.md");
    assert!(project("<ref:unknown>", "", &[]).is_none());
    assert_eq!(paper_history(&sample("<ref:r1>")).as_str(), "Guide");
}

#[test]
fn rejected_addresses_leave_readable_text_in_autolinks_and_code() {
    let projection = fixture(concat!(
        "<https://unissued.invalid/>\n\n",
        "Read [the guide](https://unissued.invalid/), or don't share `https://unissued.invalid/link`.\n\n",
        "```sh\nfetch https://unissued.invalid/link\n```",
    ));
    let history = paper_history(&projection.reply);
    assert!(projection.omitted);
    assert_eq!(
        history.as_str(),
        "[…]\n\nRead the guide, or don't share […].\n\nfetch […]"
    );
    assert!(
        serde_json::to_value(history)
            .unwrap()
            .get("references")
            .is_none()
    );
    assert_eq!(
        paper_history(&sample("https://unissued.invalid/")).as_str(),
        "[…]"
    );
}

#[test]
fn long_lists_keep_items_and_total_clipping_is_visible() {
    let mut items = String::new();
    for number in 1..=30 {
        writeln!(&mut items, "{number}. item {number}").unwrap();
    }
    let projection = fixture(&format!("{items}\nConclusion."));
    assert!(!projection.omitted);
    assert!(
        paper_history(&projection.reply)
            .as_str()
            .ends_with("30. item 30\n\nConclusion.")
    );
    let long = fixture(&format!("**{}**", "🔧".repeat(TEXT_LIMIT + 1)));
    assert!(long.omitted);
    let visible = paper_history(&long.reply);
    assert!(visible.as_str().ends_with("\n\n…"));
    assert_eq!(visible.as_str().chars().count(), TEXT_LIMIT + 3);
}

#[test]
fn only_current_public_https_evidence_can_be_clickable() {
    let spelling = "https://mcmik.top/w/Café?item=[1]#section#part";
    let canonical = "https://mcmik.top/w/Caf%C3%A9?item=%5B1%5D#section%23part";
    let output = format!(
        concat!(
            "[Guide](<{spelling}>) [Another page](https://mcmik.top/wiki) ",
            "[Private](ref:r2) [Unknown](https://mcmik.top@other.example.com/) ",
            "[Command](javascript:alert%281%29) [Port](https://mcmik.top:443/wiki)"
        ),
        spelling = spelling
    );
    let references = [
        Reference::new("Known page".into(), Some(spelling)),
        Reference::new("Private".into(), Some("https://127.0.0.1/")),
    ];
    let projection = project(&output, "", &references).unwrap();
    assert!(projection.omitted);
    let history = paper_history(&projection.reply);
    assert_eq!(
        history.as_str(),
        "Guide Another page Private Unknown Command Port"
    );
    assert_eq!(
        serde_json::to_value(history).unwrap()["references"],
        serde_json::json!([
            {"line": 1, "label": "Guide", "url": canonical},
        ])
    );
}

#[test]
fn budget_never_leaves_click_targets_on_partial_labels_or_bare_addresses() {
    let bare = "https://mcmik.top/wiki";
    let visible = paper_history(&sample(&format!("{} {bare}", "x".repeat(TEXT_LIMIT - 5))));
    assert!(!visible.as_str().contains("http"));
    assert!(visible.as_str().ends_with('…'));
    let label = paper_history(&sample(&format!(
        "{} [Entry guide](ref:r1)",
        "x".repeat(TEXT_LIMIT - 5),
    )));
    assert!(label.as_str().ends_with(" Entr\n\n…"));
    assert!(
        serde_json::to_value(label)
            .unwrap()
            .get("references")
            .is_none()
    );

    let styled = fixture(&"**a** *b* ~~c~~ ".repeat(300));
    assert!(styled.omitted);
    assert!(paper_history(&styled.reply).as_str().ends_with('…'));
}
