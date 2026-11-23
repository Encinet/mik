use super::super::track::{TrackRecord, TrackRef};
use super::LyricsText;
use serde_json::{Map, Value, json};
use std::collections::BTreeSet;

const INFO_BYTES: usize = 24 * 1024;

pub(super) fn quality(value: &str) -> String {
    match value.trim().to_ascii_lowercase() {
        value if value == "hires" => "flac24bit".into(),
        value => value,
    }
}

pub(super) fn qualities(info: &Value) -> BTreeSet<String> {
    let mut allowed = BTreeSet::new();
    for value in [info, &info["meta"]]
        .into_iter()
        .filter_map(Value::as_object)
    {
        collect_qualities(&mut allowed, value);
    }
    allowed
}

pub(super) fn select_quality<'a>(
    allowed: &BTreeSet<String>,
    supported: &'a [String],
) -> Option<&'a str> {
    ["flac24bit", "flac", "ape", "wav", "320k", "192k", "128k"]
        .into_iter()
        .filter_map(|preferred| supported.iter().find(|value| value.as_str() == preferred))
        .chain(supported.iter())
        .find(|value| allowed.is_empty() || allowed.contains(*value))
        .map(String::as_str)
}

pub(super) fn from_lx(value: Value, source: &str, provider: &str) -> Option<TrackRecord> {
    let name = first(&value, &["name", "title"])?;
    let artist = first(&value, &["singer", "artist"]).unwrap_or_default();
    let album = text(&value["meta"]["albumName"])
        .or_else(|| first(&value, &["albumName", "album"]))
        .unwrap_or_default();
    let canonical =
        text(&value["meta"]["songId"]).or_else(|| first(&value, &["songmid", "songId", "id"]))?;
    let hash = text(&value["meta"]["hash"]).or_else(|| first(&value, &["hash"]));
    let song_id = if source == "kg" {
        hash.as_ref().unwrap_or(&canonical)
    } else {
        &canonical
    };
    let key = TrackRef::new(source, song_id.clone())?;
    let lx_id = first(&value, &["id"]).unwrap_or_else(|| format!("{source}_{canonical}"));
    let duration_ms = first(&value, &["interval", "duration"])
        .and_then(|value| duration(&value))
        .and_then(|seconds| seconds.checked_mul(1000));
    let Value::Object(mut info) = value else {
        return None;
    };
    let mut meta = match info.remove("meta") {
        Some(Value::Object(meta)) => meta,
        _ => Map::new(),
    };
    info.insert("id".into(), lx_id.into());
    info.insert("source".into(), source.into());
    info.insert("songmid".into(), canonical.clone().into());
    info.entry("singer")
        .or_insert_with(|| artist.clone().into());
    info.entry("typeUrl").or_insert_with(|| json!({}));
    meta.insert("songId".into(), canonical.into());
    if let Some(hash) = hash {
        meta.entry("hash").or_insert(hash.into());
    }
    for (legacy, field) in [
        ("albumName", "albumName"),
        ("albumId", "albumId"),
        ("img", "picUrl"),
        ("types", "qualitys"),
        ("_types", "_qualitys"),
        ("strMediaMid", "strMediaMid"),
        ("albumMid", "albumMid"),
        ("copyrightId", "copyrightId"),
        ("lrcUrl", "lrcUrl"),
        ("mrcUrl", "mrcUrl"),
        ("trcUrl", "trcUrl"),
    ] {
        mirror(&mut info, legacy, &mut meta, field);
    }
    if source == "tx" {
        mirror(&mut info, "songId", &mut meta, "id");
    }
    if source == "kg" {
        mirror(&mut info, "hash", &mut meta, "hash");
    }
    for (key, fallback) in [("qualitys", json!([])), ("_qualitys", json!({}))] {
        if let Some(value) = info.get(key) {
            meta.entry(key).or_insert_with(|| value.clone());
        }
        meta.entry(key).or_insert(fallback);
    }
    info.insert("meta".into(), meta.into());
    let info_json = Value::Object(info).to_string();
    if info_json.len() > INFO_BYTES {
        return None;
    }
    Some(TrackRecord {
        key,
        provider: provider.into(),
        info_json,
        title: name,
        artist,
        album,
        duration_ms,
    })
}

fn mirror(info: &mut Map<String, Value>, legacy: &str, meta: &mut Map<String, Value>, field: &str) {
    if let Some(value) = meta.get(field) {
        info.insert(legacy.into(), value.clone());
    } else if let Some(value) = info.get(legacy) {
        meta.insert(field.into(), value.clone());
    }
}

fn collect_qualities(qualities: &mut BTreeSet<String>, value: &Map<String, Value>) {
    for key in ["types", "qualitys"] {
        if let Some(list) = value.get(key).and_then(Value::as_array) {
            qualities.extend(
                list.iter()
                    .filter_map(|value| value.as_str().or_else(|| value["type"].as_str()))
                    .filter(|value| !value.is_empty() && value.len() <= 32)
                    .map(quality),
            );
        }
    }
    for key in ["_types", "_qualitys"] {
        if let Some(object) = value.get(key).and_then(Value::as_object) {
            qualities.extend(
                object
                    .keys()
                    .filter(|key| !key.is_empty() && key.len() <= 32)
                    .map(|key| quality(key)),
            );
        }
    }
}

fn duration(text: &str) -> Option<i64> {
    text.split(':')
        .try_fold(0_i64, |time, field| {
            time.checked_mul(60)?
                .checked_add(field.parse::<i64>().ok()?)
        })
        .filter(|seconds| *seconds > 0)
}

pub(super) fn text(value: &Value) -> Option<String> {
    match value {
        Value::String(text) if !text.trim().is_empty() && text.len() <= 4096 => {
            Some(text.trim().to_owned())
        }
        Value::Number(number) => Some(number.to_string()),
        _ => None,
    }
}

pub(super) fn first(value: &Value, keys: &[&str]) -> Option<String> {
    keys.iter().filter_map(|key| value.get(key)).find_map(text)
}

pub(super) fn number(value: &Value) -> Option<u64> {
    value.as_u64().or_else(|| value.as_str()?.parse().ok())
}

pub(super) fn total(value: &Value, fallback: usize) -> i32 {
    i32::try_from(number(value).unwrap_or(fallback as u64)).unwrap_or(i32::MAX)
}

pub(super) fn lyrics(value: &Value) -> LyricsText {
    if let Some(original) = value.as_str() {
        return LyricsText {
            original: original.into(),
            ..Default::default()
        };
    }
    let field = |keys: &[&str]| {
        keys.iter()
            .find_map(|key| {
                value
                    .get(key)?
                    .as_str()
                    .filter(|text| !text.trim().is_empty())
            })
            .unwrap_or_default()
            .to_owned()
    };
    let direct = LyricsText {
        original: field(&["lyric", "lrc", "lxlyric", "original"]),
        translation: field(&["tlyric", "tlrc", "translation", "trans"]),
        romanization: field(&["rlyric", "rlrc", "romanization", "roma"]),
    };
    if direct != LyricsText::default() {
        return direct;
    }
    ["data", "result", "body"]
        .iter()
        .filter_map(|key| value.get(key))
        .map(lyrics)
        .find(|lyrics| *lyrics != LyricsText::default())
        .unwrap_or_default()
}
