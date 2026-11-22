use super::{Body, Cue, MAX_ENTRIES, MAX_LINE, MAX_TEXT};
use regex::Regex;
use std::{collections::BTreeMap, sync::LazyLock};

#[expect(clippy::expect_used, reason = "LRC patterns are source literals")]
static TAG: LazyLock<Regex> = LazyLock::new(|| {
    Regex::new(r"^\[(?:([0-9]{1,3}):)?([0-9]{1,3}):([0-9]{1,2})(?:\.([0-9]{1,3}))?\]")
        .expect("LRC timestamp")
});
#[expect(clippy::expect_used, reason = "LRC patterns are source literals")]
static OFFSET: LazyLock<Regex> =
    LazyLock::new(|| Regex::new(r"(?i)^\[offset:([+-]?[0-9]+)\]$").expect("LRC offset"));
#[expect(clippy::expect_used, reason = "LRC patterns are source literals")]
static WORD: LazyLock<Regex> = LazyLock::new(|| {
    Regex::new(r"<[0-9]{1,3}:[0-9]{1,2}(?:\.[0-9]{1,3})?>").expect("LRC word timestamp")
});
#[expect(clippy::expect_used, reason = "LRC patterns are source literals")]
static META: LazyLock<Regex> =
    LazyLock::new(|| Regex::new(r"^\[[a-zA-Z]+:[^\]]*\]$").expect("LRC tag"));

pub(super) fn parse(text: &str, aligned: bool, duration: Option<u64>) -> Option<Body> {
    if text.len() > MAX_TEXT {
        return None;
    }
    let trim = |line: &str| line.trim().trim_start_matches('\u{feff}').trim().to_owned();
    let offset = text
        .lines()
        .rev()
        .find_map(|line| OFFSET.captures(&trim(line))?[1].parse::<i64>().ok())
        .unwrap_or_default()
        .clamp(-3_600_000, 3_600_000);
    let mut plain = Vec::new();
    let mut timed = BTreeMap::<u64, String>::new();
    for raw in text.lines().take(MAX_ENTRIES) {
        let line = trim(raw);
        let mut rest = line.as_str();
        let mut tags = Vec::new();
        while let Some(tag) = TAG.captures(rest) {
            let millis = millis(&tag).map(|time| (time + offset).max(0).unsigned_abs());
            if let Some(millis) = millis {
                tags.push(millis);
            }
            rest = rest[tag.get_match().end()..].trim_start();
        }
        if META.is_match(rest) {
            continue;
        }
        let visible = bounded(&WORD.replace_all(rest, "").replace('\0', " "));
        if !visible.is_empty() {
            plain.push(visible.clone());
        }
        if aligned {
            for time in tags {
                if timed.len() >= MAX_ENTRIES && !timed.contains_key(&time) {
                    break;
                }
                if duration.is_some_and(|duration| time >= duration) {
                    continue;
                }
                let existing = timed.entry(time).or_default();
                if existing.is_empty() {
                    existing.clone_from(&visible);
                } else if !visible.is_empty() && !existing.lines().any(|line| line == visible) {
                    *existing = bounded(&format!("{existing}\n{visible}"));
                }
            }
        }
    }
    if !timed.is_empty() {
        let mut iter = timed.into_iter().peekable();
        let mut cues = Vec::new();
        let mut size = 0;
        while let Some((start_ms, text)) = iter.next() {
            let end_ms = iter.peek().map(|(at, _)| *at).or(duration);
            size += text.len();
            if size > MAX_TEXT {
                return None;
            }
            if !text.is_empty() {
                cues.push(Cue {
                    start_ms,
                    end_ms,
                    text,
                });
            }
        }
        return (!cues.is_empty()).then_some(Body::Timed { cues });
    }
    (!plain.is_empty()).then_some(Body::Text { lines: plain })
}

fn bounded(text: &str) -> String {
    text[..text.floor_char_boundary(text.len().min(MAX_LINE))]
        .trim()
        .to_owned()
}

fn millis(tag: &regex::Captures<'_>) -> Option<i64> {
    let hours = tag
        .get(1)
        .map_or(Some(0), |part| part.as_str().parse().ok())?;
    let minutes: i64 = tag[2].parse().ok()?;
    let seconds: i64 = tag[3].parse().ok()?;
    let fraction = tag.get(4).map_or("", |part| part.as_str());
    let ms: i64 = format!("{fraction:0<3}").parse().ok()?;
    (seconds < 60 && (tag.get(1).is_none() || minutes < 60))
        .then_some(((hours * 60 + minutes) * 60 + seconds) * 1000 + ms)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn independent_intervals_preserve_gaps_and_do_not_invent_alignment() {
        let source =
            "[offset:+250]\n[00:01.5][00:02.050]First\n[00:02.05]Again\n[00:04]\n[00:07]Last";
        let Some(Body::Timed { cues }) = parse(source, true, Some(8000)) else {
            panic!("expected aligned cues");
        };
        assert_eq!(cues[0].start_ms, 1750);
        assert_eq!(cues[1].text, "First\nAgain");
        assert_eq!(cues[1].end_ms, Some(4250));
        assert_eq!(cues[2].start_ms, 7250);
        assert_eq!(cues[2].end_ms, Some(8000));
        let Some(Body::Text { lines }) = parse(source, false, None) else {
            panic!("missing unaligned text");
        };
        assert_eq!(lines, ["First", "Again", "Last"]);
    }
}
