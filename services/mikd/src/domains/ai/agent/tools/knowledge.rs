use super::{
    Observations, ToolObservation,
    observation::{ObservedRecord, label},
};
use crate::domains::ai::{
    TurnError,
    knowledge::{Excerpt, LookupStatus, MikWikiTarget, WikiEdition, is_wiki_citation},
    reference::Reference,
};
use rig_agent::tool::DynamicTool;
use serde::Deserialize;
use serde_json::{Value, json};
use std::sync::Arc;

pub(super) fn register(
    run: &Arc<Observations>,
    tools: &mut Vec<DynamicTool>,
) -> Result<(), TurnError> {
    tools.push(run.bind(
        "search_knowledge",
        "Find excerpts in the public MIK Wiki and any server-provided public documents. Use short MIK feature names or keywords in Simplified Chinese or English, or an exact official MIK Wiki section URL.",
        json!({"type": "object", "additionalProperties": false, "required": ["query"], "properties": {
            "query": {"type": "string"}
        }}),
        |run, args| async move { search(&run, &args).await },
    )?);
    tools.push(run.bind(
        "search_minecraft_wiki",
        "Look up vanilla Minecraft Wiki article titles locally. Use a short title or a supported edition's article URL (which may include a section). By default, prefer the player's locale with limited fallback on a miss. A URL selects its own edition. If the player requests a specific edition, select its language code and do not silently use another edition. Read a match before citing it.",
        json!({"type": "object", "additionalProperties": false, "required": ["query"], "properties": {
            "query": {"type": "string"}, "language": {"type": "string", "enum": WikiEdition::codes().collect::<Vec<_>>()}
        }}),
        |run, args| async move { find_minecraft(&run, &args).await },
    )?);
    tools.push(run.bind(
        "read_minecraft_wiki",
        "Read a vanilla Minecraft Wiki article using an article_id returned this turn by search_minecraft_wiki. It defaults to an overview, or the section in the supplied URL. To read a specific part, pass the exact section from the returned outline. Use the reference_id from the excerpt you rely on.",
        json!({"type": "object", "additionalProperties": false, "required": ["article_id"], "properties": {
            "article_id": {"type": "string"}, "section": {"type": "string"}
        }}),
        |run, args| async move { read_minecraft(&run, &args).await },
    )?);
    Ok(())
}

#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
struct Search {
    query: String,
}

#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
struct FindMinecraft {
    query: String,
    language: Option<WikiEdition>,
}

#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
struct ReadMinecraft {
    article_id: String,
    section: Option<String>,
}

async fn search(run: &Observations, args: &Value) -> ToolObservation {
    let Ok(Search { query }) = Search::deserialize(args) else {
        return ToolObservation::invalid("invalid knowledge query");
    };
    let query = query.trim();
    if query.is_empty() || query.chars().any(char::is_control) {
        return ToolObservation::invalid("invalid knowledge query");
    }
    let tools = run.tools.clone();
    let locale = run.mik_wiki_locale;
    if (query.contains("://") || query.len() > 512) && MikWikiTarget::parse(query, locale).is_none()
    {
        return ToolObservation::invalid("use short terms or an official MIK Wiki section URL");
    }
    let Ok(permit) = run.tools.knowledge_slots.clone().try_acquire_owned() else {
        return ToolObservation::unavailable("knowledge search is busy");
    };
    let query = query.to_owned();
    match tokio::task::spawn_blocking(move || {
        let _permit = permit;
        tools.knowledge.search(locale, &query)
    })
    .await
    {
        Ok(Ok(matches)) => {
            let status = if matches.excerpts.is_empty() {
                matches.wiki_status
            } else {
                LookupStatus::Found
            };
            let excerpts = matches.excerpts.into_iter().map(|mut excerpt| {
                excerpt.source = label(&excerpt.source);
                let end = excerpt.text.floor_char_boundary(2300);
                excerpt.text = bounded_text(&excerpt.text[..end], 2100);
                wiki_excerpt(&excerpt, status)
            });
            ToolObservation::new(status, json!({"wiki_status": matches.wiki_status}))
                .with_records("excerpts", excerpts)
        }
        _ => ToolObservation::unavailable("knowledge is unavailable"),
    }
}

async fn find_minecraft(run: &Observations, args: &Value) -> ToolObservation {
    let Ok(FindMinecraft { query, language }) = FindMinecraft::deserialize(args) else {
        return ToolObservation::invalid("invalid Minecraft Wiki search arguments");
    };
    let query = query.trim();
    if query.is_empty() || query.len() > 2048 || query.chars().any(char::is_control) {
        return ToolObservation::invalid(
            "use a short Minecraft article title or its official article URL",
        );
    }
    let Ok(permit) = run.tools.knowledge_slots.clone().try_acquire_owned() else {
        return ToolObservation::unavailable("knowledge search is busy");
    };
    let tools = run.tools.clone();
    let edition = run.minecraft_edition;
    let query = query.to_owned();
    let lookup = tokio::task::spawn_blocking(move || {
        let _permit = permit;
        tools.minecraft.find(edition, language, &query)
    })
    .await;
    let lookup = match lookup {
        Ok(Ok(lookup)) => lookup,
        Ok(Err(error)) if error.kind() == std::io::ErrorKind::InvalidInput => {
            return ToolObservation::invalid(
                "invalid article URL or mismatched Minecraft Wiki edition",
            );
        }
        _ => {
            return ToolObservation::unavailable("Minecraft Wiki title catalog could not be read");
        }
    };
    let status = LookupStatus::search(
        !lookup.matches.is_empty(),
        lookup.unavailable_editions.is_empty(),
    );
    let results = lookup.matches.into_iter().map(|candidate| {
        let fields = json!(&candidate);
        ObservedRecord::article(fields, candidate.reference)
    });
    ToolObservation::new(
        status,
        json!({
            "available_editions": lookup.available_editions,
            "unavailable_editions": lookup.unavailable_editions,
        }),
    )
    .with_records("results", results)
}

async fn read_minecraft(run: &Observations, args: &Value) -> ToolObservation {
    let Ok(ReadMinecraft {
        article_id,
        section,
    }) = ReadMinecraft::deserialize(args)
    else {
        return ToolObservation::invalid("invalid Minecraft Wiki read arguments");
    };
    let Some(reference) = run
        .evidence
        .lock()
        .ok()
        .and_then(|evidence| evidence.article(&article_id))
    else {
        return ToolObservation::invalid(
            "use an article_id from a Minecraft Wiki search in this turn",
        );
    };
    match run
        .tools
        .minecraft
        .read(reference, section.as_deref())
        .await
    {
        Ok(article) => ToolObservation::new(article.status, json!(&*article)).with_records(
            "excerpts",
            article
                .excerpts
                .iter()
                .map(|excerpt| wiki_excerpt(excerpt, article.status)),
        ),
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => ToolObservation::error(
            LookupStatus::NoMatch,
            "no readable article or section matched",
        ),
        Err(error) if error.kind() == std::io::ErrorKind::PermissionDenied => {
            ToolObservation::error(
                LookupStatus::AccessUnavailable,
                "this Minecraft Wiki content is not available for public reading",
            )
        }
        Err(error) if error.kind() == std::io::ErrorKind::InvalidInput => ToolObservation::invalid(
            "the article or section name is invalid; try another search result",
        ),
        Err(_) => {
            ToolObservation::unavailable("could not read the Minecraft Wiki article right now")
        }
    }
}

fn wiki_excerpt(excerpt: &Excerpt, status: LookupStatus) -> ObservedRecord {
    let name = label(&excerpt.source);
    let reference =
        (status == LookupStatus::Found && !name.is_empty() && !excerpt.text.trim().is_empty())
            .then(|| {
                Reference::new(
                    name,
                    Some(excerpt.wiki_url.as_str()).filter(|url| is_wiki_citation(url)),
                )
            });
    ObservedRecord::citable(json!(excerpt), reference)
}

fn bounded_text(text: &str, limit: usize) -> String {
    text.trim()
        .chars()
        .filter(|character| !character.is_control() || *character == '\n' || *character == '\t')
        .take(limit)
        .collect()
}
