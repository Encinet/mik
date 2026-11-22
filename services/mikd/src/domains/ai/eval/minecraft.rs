use super::{Eval, INVENTORY, Ipv4Addr, Json, OWNER, Router, TcpListener, VISITOR, iron_blocks};
use axum::{
    http::{StatusCode, Uri},
    response::IntoResponse as _,
    routing::get,
};
use serde_json::{Value, json};
use std::{
    collections::BTreeMap,
    sync::{
        Arc,
        atomic::{AtomicUsize, Ordering},
    },
};
use tokio::task::JoinHandle;

const EN_LINK: &str = "https://minecraft.wiki/w/Redstone_Repeater#Crafting";
const ZH_LINK: &str =
    "https://zh.minecraft.wiki/w/%E7%BA%A2%E7%9F%B3%E4%B8%AD%E7%BB%A7%E5%99%A8#%E5%90%88%E6%88%90";
const ZH_REDIRECT: &str = "https://zh.minecraft.wiki/wiki/紅石中繼器#合成";
const MIK_LINK: &str = "https://mcmik.top/zh-CN/wiki?section=building#redstone-practice";
const FIND: &str = "search_minecraft_wiki";
const READ: &str = "read_minecraft_wiki";
const JAPANESE_TITLE: &str = "レッドストーンリピーター";
const JAPANESE_SECTION: &str = "クラフト";

#[tokio::test]
async fn ordinary_agent_reads_public_wikis_without_sharing_private_queries() {
    let (origin, calls, server) = wiki_fixture().await;
    let mut eval = Eval::open_with(None, Some(&origin)).await;
    eval.assistant.refresh_wiki().await;
    let catalog_calls = calls.load(Ordering::Relaxed);
    assert!(catalog_calls >= 2);

    let question =
        "Private project PRV-112-80-334: what goes in a repeater and what am I carrying?";
    let en_turn = eval.ask(OWNER, question);
    let model = eval.exchange(question).await;
    assert!(model.tools().contains(&INVENTORY));
    let forged = model.call(READ, json!({"article_id": "w39659"}));
    let model = eval.exchange(question).await;
    assert!(model.observation(&forged)["error"].is_string());
    let find = model.call(
        FIND,
        json!({"query": "Redstone Repeater PRV-112-80-334", "language": "en"}),
    );
    let model = eval.exchange(question).await;
    let hits = model.observation(&find);
    assert_eq!(hits["results"][0]["title"], "Redstone Repeater");
    assert!(
        hits["results"][0]["reference_id"].is_null(),
        "titles are not evidence"
    );
    assert_eq!(
        calls.load(Ordering::Relaxed),
        catalog_calls,
        "keywords and forged IDs must not leave mikd"
    );
    let read = model.call(
        READ,
        json!({"article_id": hits["results"][0]["article_id"], "section": "Crafting"}),
    );
    let model = eval.exchange(question).await;
    assert_read(&model.observation(&read), EN_LINK, "Redstone Torch", "r1");
    let inventory = model.call(INVENTORY, json!({}));
    let job = eval.game_call(&en_turn, &inventory).await;
    eval.game_reply(&en_turn, job, iron_blocks(8)).await;
    let model = eval.exchange(question).await;
    assert_eq!(
        model.observation(&inventory)["data"]["items"][0]["amount"],
        8
    );
    model.say("Repeaters need redstone torches; you are carrying 8 iron blocks. <ref:r1><ref:r99>");
    assert_eq!(citations(&eval.take_reply(&en_turn).await), [EN_LINK]);

    let question = format!("请阅读 {ZH_REDIRECT}，原版怎么合成红石中继器？本服在哪里练习？");
    let zh_turn = eval.ask_localized(VISITOR, &question, "zh-CN");
    let model = eval.exchange(&question).await;
    assert_eq!(model.questions(), [question.as_str()]);
    let find = model.call(FIND, json!({"query": ZH_REDIRECT}));
    let model = eval.exchange(&question).await;
    let hit = &model.observation(&find)["results"][0];
    assert_eq!(hit["edition"], "zh");
    let read = model.call(READ, json!({"article_id": hit["article_id"]}));
    let model = eval.exchange(&question).await;
    assert_read(&model.observation(&read), ZH_LINK, "紅石火把", "r1");
    let official = model.call("search_knowledge", json!({"query": "红石练习"}));
    let model = eval.exchange(&question).await;
    assert_read(&model.observation(&official), MIK_LINK, "红石练习区", "r2");
    let answer = "原版中继器需要红石火把、红石粉和石头；本服可在红石练习区练习。";
    model.say(&format!("{answer}<ref:r1> · <ref:r2>"));
    assert_eq!(
        citations(&eval.take_reply(&zh_turn).await),
        [ZH_LINK, MIK_LINK]
    );
    assert_eq!(calls.load(Ordering::Relaxed), catalog_calls + 6);

    eval.close().await;
    server.abort();
}

#[tokio::test]
async fn the_agent_prefers_the_player_edition_without_replacing_an_explicit_one() {
    let (origin, calls, server) = wiki_fixture().await;
    let mut eval = Eval::open_with(None, Some(&origin)).await;
    eval.assistant.refresh_wiki().await;
    let catalog_calls = calls.load(Ordering::Relaxed);
    assert_eq!(
        catalog_calls,
        super::super::knowledge::WikiEdition::codes().len()
    );

    let question = "タイ語版と日本語版では、レッドストーンリピーターの素材をどう説明していますか？";
    let turn = eval.ask_localized(OWNER, question, "ja-JP");
    let unavailable = eval.exchange(question).await.call(
        FIND,
        json!({"query": "Redstone Repeater", "language": "th"}),
    );
    let model = eval.exchange(question).await;
    let miss = model.observation(&unavailable);
    assert_eq!(miss["status"], "unavailable");
    assert_eq!(miss["unavailable_editions"], json!(["th"]));
    assert_eq!(miss["results"], json!([]));
    let external = model.call(
        FIND,
        json!({"query": "https://meta.minecraft.wiki/w/Redstone"}),
    );
    let model = eval.exchange(question).await;
    assert!(model.observation(&external)["error"].is_string());
    let find = model.call(FIND, json!({"query": JAPANESE_TITLE}));
    let model = eval.exchange(question).await;
    let hits = model.observation(&find);
    assert_eq!(hits["available_editions"], json!(["ja"]));
    assert_eq!(hits["results"][0]["title"], JAPANESE_TITLE);
    assert_eq!(calls.load(Ordering::Relaxed), catalog_calls);
    let read = model.call(
        READ,
        json!({"article_id": hits["results"][0]["article_id"], "section": JAPANESE_SECTION}),
    );
    let model = eval.exchange(question).await;
    let link = "https://ja.minecraft.wiki/w/%E3%83%AC%E3%83%83%E3%83%89%E3%82%B9%E3%83%88%E3%83%BC%E3%83%B3%E3%83%AA%E3%83%94%E3%83%BC%E3%82%BF%E3%83%BC#%E3%82%AF%E3%83%A9%E3%83%95%E3%83%88";
    assert_read(
        &model.observation(&read),
        link,
        "レッドストーントーチ",
        "r1",
    );
    model.say(
        "日本語版ではレッドストーントーチが必要です。タイ語版は確認できませんでした。<ref:r1>",
    );
    assert_eq!(citations(&eval.take_reply(&turn).await), [link]);
    assert_eq!(calls.load(Ordering::Relaxed), catalog_calls + 3);
    eval.close().await;
    server.abort();
}

fn citations(reply: &mik_protocol::wire::AssistantReply) -> Vec<&str> {
    use mik_protocol::wire::{assistant_block::Content, assistant_part::Target};
    reply
        .blocks
        .iter()
        .filter_map(|block| {
            let Content::Paragraph(inline) = block.content.as_ref()? else {
                return None;
            };
            Some(&inline.parts)
        })
        .flatten()
        .filter_map(|part| {
            let Target::Url(url) = part.target.as_ref()? else {
                return None;
            };
            Some(url.as_str())
        })
        .collect()
}

async fn wiki_fixture() -> (String, Arc<AtomicUsize>, JoinHandle<()>) {
    let calls = Arc::new(AtomicUsize::new(0));
    let listener = TcpListener::bind((Ipv4Addr::LOCALHOST, 0)).await.unwrap();
    let address = listener.local_addr().unwrap();
    let app = Router::new()
        .route(
            "/api/wiki/search-index/{locale}",
            get(|| async {
                Json(json!([{
                    "sectionId": "building", "heading": "红石练习", "slug": "redstone-practice",
                    "directText": "可以在红石练习区练习。", "level": 1,
                }]))
            }),
        )
        .route(
            "/{locale}/api.php",
            get({
                let calls = calls.clone();
                move |uri: Uri| {
                    let calls = calls.clone();
                    async move {
                        let query =
                            url::form_urlencoded::parse(uri.query().unwrap_or_default().as_bytes())
                                .into_owned()
                                .collect::<BTreeMap<_, _>>();
                        calls.fetch_add(1, Ordering::Relaxed);
                        match uri.path() {
                            "/en/api.php" | "/zh/api.php" | "/ja/api.php" => {
                                Json(wiki_response(uri.path(), &query)).into_response()
                            }
                            _ => {
                                assert_eq!(query["list"], "allpages");
                                StatusCode::FORBIDDEN.into_response()
                            }
                        }
                    }
                }
            }),
        );
    let server = tokio::spawn(async move { axum::serve(listener, app).await.unwrap() });
    (format!("http://{address}/"), calls, server)
}

fn assert_read(observation: &Value, link: &str, phrase: &str, reference_id: &str) {
    let excerpt = &observation["excerpts"][0];
    assert_eq!(excerpt["wiki_url"], link, "{observation}");
    assert_eq!(excerpt["reference_id"], reference_id);
    let text = excerpt["text"].as_str().unwrap();
    assert!(
        text.contains(phrase) && !text.contains("HIDDEN_PAYLOAD"),
        "{text}"
    );
}

fn wiki_response(path: &str, query: &BTreeMap<String, String>) -> Value {
    let chinese = path == "/zh/api.php";
    assert_eq!(query["format"], "json");
    assert_eq!(query["formatversion"], "2");
    let (id, alias, canonical, revision, section, ingredient) = match path {
        "/zh/api.php" => (6723, "紅石中繼器", "红石中继器", 412, "合成", "紅石火把"),
        "/ja/api.php" => (
            39659,
            "リピーター",
            JAPANESE_TITLE,
            812,
            JAPANESE_SECTION,
            "レッドストーントーチ",
        ),
        _ => (
            39659,
            "Repeater",
            "Redstone Repeater",
            908,
            "Crafting",
            "Redstone Torch",
        ),
    };
    match (
        query["action"].as_str(),
        query.get("list").map(String::as_str),
    ) {
        ("query", Some("allpages")) => {
            assert_eq!(query["apnamespace"], "0");
            assert!(query.keys().all(|key| matches!(
                key.as_str(),
                "action"
                    | "list"
                    | "apnamespace"
                    | "aplimit"
                    | "apfilterredir"
                    | "format"
                    | "formatversion"
                    | "maxlag"
                    | "continue"
                    | "apcontinue"
            )));
            json!({"query": {"allpages": [
                {"ns": 0, "pageid": id, "title": canonical},
                {"ns": 0, "pageid": id + 1, "title": alias},
            ]}})
        }
        ("query", None) => {
            assert_eq!(query.len(), 6);
            assert_eq!(query["prop"], "info");
            assert_eq!(query["redirects"], "1");
            assert_eq!(
                query["pageids"],
                (if chinese { id + 1 } else { id }).to_string()
            );
            json!({"query": {"pages": [{"ns": 0, "pageid": id, "title": canonical, "lastrevid": revision}]}})
        }
        ("parse", None) => {
            assert_eq!(query["oldid"], revision.to_string());
            let mut page = json!({"pageid": id, "revid": revision, "title": canonical});
            if let Some(index) = query.get("section") {
                assert_eq!(query.len(), 8);
                assert_eq!(query["prop"], "text|revid");
                let (section, ingredient) = match index.as_str() {
                    "4" => (section, ingredient),
                    "5" if path == "/en/api.php" => ("Usage", "A repeater adds signal delay."),
                    _ => panic!("unexpected Wiki section: {index}"),
                };
                page["text"] = json!(format!(
                    "<h3>{section}</h3><p>{ingredient}</p><script>HIDDEN_PAYLOAD</script>"
                ));
            } else {
                assert_eq!(query.len(), 7);
                assert_eq!(query["prop"], "tocdata|revid");
                let mut sections = vec![json!({"index": "4", "line": section, "anchor": section})];
                if path == "/en/api.php" {
                    sections.push(json!({"index": "5", "line": "Usage", "anchor": "Usage"}));
                }
                page["tocdata"] = json!({"sections": sections});
            }
            json!({"parse": page})
        }
        _ => panic!("unexpected Minecraft Wiki request: {query:?}"),
    }
}
