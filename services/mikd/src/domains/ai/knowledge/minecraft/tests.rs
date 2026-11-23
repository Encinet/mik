use super::{CATALOG_DOWNLOAD_CONCURRENCY, MinecraftWiki, WikiEdition, address::Address};
use crate::domains::ai::knowledge::is_wiki_citation;
use axum::{
    Json, Router,
    extract::{Path, State},
    http::{StatusCode, Uri, header::LOCATION},
    response::{IntoResponse, Response},
    routing::get,
};
use serde_json::{Value, json};
use std::{
    collections::HashMap,
    net::Ipv4Addr,
    sync::{
        Arc, Mutex,
        atomic::{AtomicUsize, Ordering},
    },
    time::Duration,
};
use tokio::net::TcpListener;

#[test]
fn locale_selection_and_tool_codes_are_distinct() {
    for code in WikiEdition::codes() {
        let wire = json!(code);
        let edition = serde_json::from_value::<WikiEdition>(wire.clone()).unwrap();
        assert_eq!(edition.code(), code);
        assert_eq!(json!(edition), wire);
    }
    for (locale, code) in [
        ("pt_BR", "pt"),
        ("zh-Hant-TW", "zh"),
        ("lzh", "lzh"),
        ("JA-JP", "ja"),
        ("iw_IL", "he"),
        ("nl-NL", "nl"),
        ("cs-CZ", "en"),
        ("tr-TR", "en"),
    ] {
        assert_eq!(WikiEdition::from_locale(locale).code(), code);
    }
    for invalid in ["zh-tw", "pt_BR", "cs", "tr", "pl", "id"] {
        assert!(serde_json::from_value::<WikiEdition>(json!(invalid)).is_err());
    }
}

#[test]
fn trusted_aliases_are_lookup_only_and_citations_are_canonical() {
    for (input, canonical) in [
        (
            "https://nl.minecraft.wiki/wiki/Diamant#Gebruik",
            "https://nl.minecraft.wiki/w/Diamant#Gebruik",
        ),
        (
            "https://ja.minecraft.wiki/wiki/ダイヤモンド#入手",
            "https://ja.minecraft.wiki/w/%E3%83%80%E3%82%A4%E3%83%A4%E3%83%A2%E3%83%B3%E3%83%89#%E5%85%A5%E6%89%8B",
        ),
        (
            "https://zh.minecraft.wiki/w/钻石?variant=zh-tw#获取",
            "https://zh.minecraft.wiki/w/%E9%92%BB%E7%9F%B3#%E8%8E%B7%E5%8F%96",
        ),
        (
            "https://en.minecraft.wiki/wiki/Diamond",
            "https://minecraft.wiki/w/Diamond",
        ),
        (
            "https://www.minecraft.wiki/w/Diamond",
            "https://minecraft.wiki/w/Diamond",
        ),
    ] {
        let address = Address::parse(input).unwrap();
        let url =
            super::address::article(address.edition, &address.title, address.section.as_deref())
                .unwrap();
        assert_eq!(url.as_str(), canonical);
        assert!(is_wiki_citation(canonical));
        assert!(!is_wiki_citation(input));
    }
    for invalid in [
        "https://tr.minecraft.wiki/w/Elmas",
        "https://minecraftviki.net/wiki/Elmas",
        "https://pl.minecraft.wiki/w/Diament",
        "https://hypixelskyblock.minecraft.wiki/w/Diamond",
        "https://nl.minecraft.wiki.evil.test/w/Diamant",
        "https://nl.minecraft.wiki@evil.test/w/Diamant",
        "https://nl.minecraft.wiki:443/w/Diamant",
        "http://nl.minecraft.wiki/w/Diamant",
        "https://nl.minecraft.wiki/w/Diamant?redirect=no",
        "https://nl.minecraft.wiki/w/Diamant?variant=zh-tw",
        "https://zh.minecraft.wiki/zh-tw/%E9%91%BD%E7%9F%B3",
        "https://zh.minecraft.wiki/w/%E9%92%BB%E7%9F%B3?variant=zh-sg",
        "https://zh.minecraft.wiki/w/%E9%92%BB%E7%9F%B3?variant=zh-tw&redirect=no",
        "https://nl.minecraft.wiki/w/%2e%2e/Diamant",
        "https://nl.minecraft.wiki/w/%252e%252e/Diamant",
        "https://nl.minecraft.wiki/w/api.php",
        "https://nl.minecraft.wiki/w/Speciaal:Doorverwijzen",
        "https://ja.minecraft.wiki/w/%E7%89%B9%E5%88%A5:Random",
        "https://fr.minecraft.wiki/w/Spe%CC%81cial:Random",
        "https://nl.minecraft.wiki/w/Spe%E2%80%8Fciaal:Random",
    ] {
        assert!(Address::parse(invalid).is_none(), "{invalid}");
        assert!(!is_wiki_citation(invalid), "{invalid}");
    }
    assert!(is_wiki_citation(
        "https://minecraft.wiki/w/Java_Edition:Info"
    ));
}

#[tokio::test]
async fn catalogs_and_reads_are_isolated_and_fallback_stays_local() {
    let requests = Arc::new(Requests::default());
    let listener = TcpListener::bind((Ipv4Addr::LOCALHOST, 0)).await.unwrap();
    let origin = format!("http://{}/", listener.local_addr().unwrap());
    let router = Router::new()
        .route("/{edition}/api.php", get(mediawiki))
        .with_state(requests.clone());
    let server = tokio::spawn(async move { axum::serve(listener, router).await.unwrap() });
    let mut wiki = MinecraftWiki::new(None).unwrap();
    wiki.set_test_origin(&origin).unwrap();

    tokio::join!(wiki.refresh_due(), wiki.refresh_due());
    // Failed editions observe their own retry interval; no request follows the French redirect.
    wiki.refresh_due().await;
    let catalog_requests = requests.paths.lock().unwrap().len();
    assert_eq!(catalog_requests, WikiEdition::codes().len());
    assert!((2..=CATALOG_DOWNLOAD_CONCURRENCY).contains(&requests.peak.load(Ordering::Relaxed)));

    let nl = WikiEdition::from_locale("nl_NL");
    let de = WikiEdition::from_locale("de-DE");
    let fr = WikiEdition::from_locale("fr-FR");
    let lzh = WikiEdition::from_locale("lzh");
    let en = WikiEdition::ENGLISH;
    let zh = WikiEdition::CHINESE;

    let preferred = wiki.find(nl, None, "Diamant private words").unwrap();
    assert_eq!(preferred.available_editions, [nl]);
    assert_eq!(preferred.matches[0].edition, nl);
    let requested = wiki.find(nl, Some(de), "Diamant").unwrap();
    assert_eq!(requested.available_editions, [de]);
    let fallback = wiki.find(fr, None, "Diamond").unwrap();
    assert_eq!(fallback.available_editions, [en]);
    assert_eq!(fallback.matches[0].edition, en);
    let unavailable = wiki.find(en, Some(fr), "Diamond").unwrap();
    assert!(unavailable.available_editions.is_empty() && unavailable.matches.is_empty());
    let unavailable_url = wiki
        .find(en, None, "https://fr.minecraft.wiki/w/Diamond")
        .unwrap();
    assert!(unavailable_url.available_editions.is_empty() && unavailable_url.matches.is_empty());
    let classical = wiki.find(lzh, None, "礦藝").unwrap();
    assert_eq!(classical.available_editions, [lzh]);
    assert_eq!(classical.matches[0].edition, lzh);
    let classical_fallback = wiki.find(lzh, None, "钻石").unwrap();
    assert_eq!(classical_fallback.available_editions, [lzh, zh]);
    assert_eq!(classical_fallback.matches[0].edition, zh);
    let explicit_classical = wiki.find(zh, Some(lzh), "钻石").unwrap();
    assert_eq!(explicit_classical.available_editions, [lzh]);
    assert!(explicit_classical.matches.is_empty());
    let classical_to_english = wiki.find(lzh, None, "Diamond").unwrap();
    assert_eq!(classical_to_english.available_editions, [lzh, zh, en]);
    assert_eq!(classical_to_english.matches[0].edition, en);

    let link = "https://nl.minecraft.wiki/wiki/Diamant#Gebruik";
    assert_eq!(
        wiki.find(en, Some(de), link).err().unwrap().kind(),
        std::io::ErrorKind::InvalidInput
    );
    let candidate = wiki.find(en, Some(nl), link).unwrap().matches.remove(0);
    assert_eq!(candidate.url, "https://nl.minecraft.wiki/w/Diamant#Gebruik");
    assert_eq!(requests.paths.lock().unwrap().len(), catalog_requests);

    let (first, second, third) = tokio::join!(
        wiki.read(candidate.reference.clone(), None),
        wiki.read(candidate.reference.clone(), None),
        wiki.read(candidate.reference.clone(), None),
    );
    let article = first.unwrap();
    assert!(Arc::ptr_eq(&article, &second.unwrap()));
    assert!(Arc::ptr_eq(&article, &third.unwrap()));
    let excerpt = &article.excerpts[0];
    assert_eq!(article.edition, nl);
    assert_eq!(excerpt.wiki_url, candidate.url);
    assert!(is_wiki_citation(&excerpt.wiki_url));
    assert!(excerpt.source.contains("Nederlands"));
    assert!(excerpt.text.contains("Nederlandse diamant"));

    // The same numeric page ID and section on another wiki must not reuse a Dutch article.
    let german = wiki.find(de, None, "Diamant").unwrap().matches.remove(0);
    let article_de = wiki.read(german.reference, Some("Gebruik")).await.unwrap();
    assert_eq!(article_de.edition, de);
    assert!(article_de.excerpts[0].text.contains("Deutscher Diamant"));
    assert_eq!(
        article_de.excerpts[0].wiki_url,
        "https://de.minecraft.wiki/w/Diamant#Gebruik"
    );
    let cached = wiki.read(candidate.reference, None).await.unwrap();
    assert!(Arc::ptr_eq(&article, &cached));
    assert_eq!(
        &requests.paths.lock().unwrap()[catalog_requests..],
        ["nl", "nl", "nl", "de", "de", "de"]
    );
    server.abort();
}

#[derive(Default)]
struct Requests {
    paths: Mutex<Vec<String>>,
    active: AtomicUsize,
    peak: AtomicUsize,
}

async fn mediawiki(
    State(requests): State<Arc<Requests>>,
    Path(edition): Path<String>,
    uri: Uri,
) -> Response {
    requests.paths.lock().unwrap().push(edition.clone());
    let active = requests.active.fetch_add(1, Ordering::Relaxed) + 1;
    requests.peak.fetch_max(active, Ordering::Relaxed);
    tokio::time::sleep(Duration::from_millis(10)).await;
    requests.active.fetch_sub(1, Ordering::Relaxed);

    let title = match edition.as_str() {
        "de" | "nl" => "Diamant",
        "en" => "Diamond",
        "lzh" => "礦藝",
        "zh" => "钻石",
        "fr" => return (StatusCode::FOUND, [(LOCATION, "/en/api.php")]).into_response(),
        _ => "Minecraft",
    };
    let query: HashMap<_, _> =
        url::form_urlencoded::parse(uri.query().unwrap_or_default().as_bytes()).collect();
    let get = |key| query.get(key).map(std::borrow::Cow::as_ref);
    let body: Value = match (get("action"), get("list"), get("prop")) {
        (Some("query"), Some("allpages"), None) => {
            json!({"query":{"allpages":[{"ns":0,"title":title,"pageid":17}]}})
        }
        (Some("query"), None, Some("info")) if get("pageids") == Some("17") => {
            json!({"query":{"pages":[{"ns":0,"title":title,"pageid":17,"lastrevid":117}]}})
        }
        (Some("parse"), None, Some("tocdata|revid")) if get("oldid") == Some("117") => {
            json!({"parse":{"title":title,"pageid":17,"revid":117,"tocdata":{"sections":[
                {"index":"1","line":"Gebruik","anchor":"Gebruik"}
            ]}}})
        }
        (Some("parse"), None, Some("text|revid"))
            if get("oldid") == Some("117") && get("section") == Some("1") =>
        {
            let text = if edition == "nl" {
                "<p>Nederlandse diamant</p>"
            } else {
                "<p>Deutscher Diamant</p>"
            };
            json!({"parse":{"title":title,"pageid":17,"revid":117,"text":text}})
        }
        _ => return StatusCode::BAD_REQUEST.into_response(),
    };
    Json(body).into_response()
}
