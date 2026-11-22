//! HTTP discovery fallback for channels without a usable script result.
use super::{
    http,
    metadata::{first, number, text, total},
};
use futures_util::{StreamExt, TryStreamExt, stream};
use md5::{Digest, Md5};
use reqwest::{Client, RequestBuilder};
use serde_json::{Value, json};
use std::{
    io,
    sync::LazyLock,
    time::{SystemTime, UNIX_EPOCH},
};

const MAX_BODY: usize = 16 * 1024 * 1024;
const PLAYLIST_LIMIT: usize = 1_000;
const AGENT: &str = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/124 Safari/537.36";
const MIGU_DEVICE: &str = "963B7AA0D21511ED807EE5846EC87D20";

pub(super) struct DiscoveryClient(pub Client);

pub(super) struct Listing {
    pub name: String,
    pub tracks: Vec<Value>,
    pub total: i32,
}

impl Listing {
    fn read(
        data: &Value,
        rows: &Value,
        total_field: &str,
        name: &str,
        limit: usize,
        convert: impl Fn(&Value) -> Option<Value>,
    ) -> Self {
        let mut tracks = Vec::new();
        let mut pending = vec![rows];
        while let Some(row) = pending.pop() {
            if let Some(list) = row.as_array() {
                pending.extend(list.iter().rev());
            } else if let Some(track) = convert(row) {
                tracks.push(track);
                if tracks.len() == limit {
                    break;
                }
            }
        }
        Self {
            total: total(&data[total_field], tracks.len()),
            name: text(&data[name]).map(decoded).unwrap_or_default(),
            tracks,
        }
    }
}

impl DiscoveryClient {
    pub fn supports(source: &str) -> bool {
        matches!(source, "kw" | "kg" | "tx" | "wy" | "mg")
    }

    pub async fn search(
        &self,
        source: &str,
        keyword: &str,
        page: i32,
        limit: i32,
    ) -> io::Result<Listing> {
        let keyword_encoded: String =
            url::form_urlencoded::byte_serialize(keyword.as_bytes()).collect();
        let count = usize::try_from(limit).map_err(io::Error::other)?;
        Ok(match source {
            "kw" => {
                let url = format!(
                    "https://search.kuwo.cn/r.s?client=kt&all={keyword_encoded}&pn={}&rn={limit}&uid=794762570&ver=kwplayer_ar_9.2.2.1&vipver=1&show_copyright_off=1&newver=1&ft=music&cluster=0&strategy=2012&encoding=utf8&rformat=json&vermerge=1&mobi=1&issubtitle=1",
                    page - 1
                );
                let data = json_response(self.get(&url)).await?;
                Listing::read(&data, &data["abslist"], "TOTAL", "", count, |song| {
                    kuwo(song, false)
                })
            }
            "kg" => {
                let url = format!(
                    "https://songsearch.kugou.com/song_search_v2?keyword={keyword_encoded}&page={page}&pagesize={limit}&userid=0&clientver=&platform=WebFilter&filter=2&iscorrection=1&privilege_filter=0&area_code=1"
                );
                let response = json_response(self.get(&url)).await?;
                check(&response, "error_code", "0", "Kugou search")?;
                let data = &response["data"];
                Listing::read(data, &data["lists"], "total", "", count, |song| {
                    kugou(song, false)
                })
            }
            "tx" => {
                let url = format!(
                    "https://c.y.qq.com/soso/fcgi-bin/client_search_cp?format=json&p={page}&n={limit}&w={keyword_encoded}&cr=1&new_json=1"
                );
                let response =
                    json_response(self.get(&url).header("referer", "https://y.qq.com/")).await?;
                check(&response, "code", "0", "QQ Music search")?;
                let data = &response["data"]["song"];
                Listing::read(data, &data["list"], "totalnum", "", count, |song| {
                    tencent(song, false)
                })
            }
            "wy" => {
                let body = format!(
                    "s={keyword_encoded}&type=1&limit={limit}&offset={}",
                    i64::from(page - 1) * i64::from(limit)
                );
                let response = json_response(
                    self.0
                        .post("https://music.163.com/api/search/get/web")
                        .header("user-agent", AGENT)
                        .header("referer", "https://music.163.com/")
                        .header("content-type", "application/x-www-form-urlencoded")
                        .body(body),
                )
                .await?;
                check(&response, "code", "200", "NetEase search")?;
                let data = &response["result"];
                Listing::read(data, &data["songs"], "songCount", "", count, netease)
            }
            "mg" => {
                let timestamp = SystemTime::now()
                    .duration_since(UNIX_EPOCH)
                    .map_err(io::Error::other)?
                    .as_millis();
                let sign = format!(
                    "{keyword}6cdc72a439cef99a3418d2a78aa28c73yyapp2d16148780a1dcc7408e06336b98cfd50{MIGU_DEVICE}{timestamp}"
                );
                let url = format!(
                    "https://jadeite.migu.cn/music_search/v3/search/searchAll?isCorrect=0&isCopyright=1&searchSwitch=%7B%22song%22%3A1%2C%22album%22%3A0%2C%22singer%22%3A0%2C%22tagSong%22%3A1%2C%22mvSong%22%3A0%2C%22bestShow%22%3A1%2C%22songlist%22%3A0%2C%22lyricSong%22%3A0%7D&pageSize={limit}&text={keyword_encoded}&pageNo={page}&sort=0&sid=USS"
                );
                let response = json_response(
                    self.get(&url)
                        .header("uiVersion", "A_music_3.6.1")
                        .header("deviceId", MIGU_DEVICE)
                        .header("timestamp", timestamp.to_string())
                        .header("sign", hex::encode(Md5::digest(sign)))
                        .header("channel", "0146921"),
                )
                .await?;
                check(&response, "code", "000000", "Migu search")?;
                let data = &response["songResultData"];
                Listing::read(data, &data["resultList"], "totalCount", "", count, migu)
            }
            _ => return Err(io::Error::other("unsupported music catalog")),
        })
    }

    pub async fn playlist(&self, source: &str, id: &str) -> io::Result<Listing> {
        Ok(match source {
            "kw" => {
                let url = format!(
                    "https://nplserver.kuwo.cn/pl.svc?op=getlistinfo&pid={id}&pn=0&rn={PLAYLIST_LIMIT}&encode=utf8&keyset=pl2012&identity=kuwo&pcmp4=1&vipver=MUSIC_9.0.5.0_W1&newver=1"
                );
                let data = json_response(self.get(&url)).await?;
                check(&data, "result", "ok", "Kuwo playlist")?;
                Listing::read(
                    &data,
                    &data["musiclist"],
                    "total",
                    "title",
                    PLAYLIST_LIMIT,
                    |song| kuwo(song, true),
                )
            }
            "kg" => {
                let bytes = response(
                    self.get(&format!(
                        "https://www.kugou.com/yy/special/single/{id}.html"
                    ))
                    .header("referer", "https://www.kugou.com/"),
                )
                .await?;
                let html = String::from_utf8_lossy(&bytes);
                let tracks = embedded(&html, "var data")
                    .or_else(|| embedded(&html, "global.data"))
                    .filter(Value::is_array)
                    .ok_or_else(|| io::Error::other("Kugou playlist contains no track list"))?;
                let info = embedded(&html, "var specialInfo").unwrap_or_default();
                Listing::read(&info, &tracks, "", "name", PLAYLIST_LIMIT, |song| {
                    kugou(song, true)
                })
            }
            "tx" => {
                let url = format!(
                    "https://c.y.qq.com/qzone/fcg-bin/fcg_ucc_getcdinfo_byids_cp.fcg?type=1&json=1&utf8=1&onlysong=0&disstid={id}&format=json"
                );
                let data = json_response(
                    self.get(&url)
                        .header("origin", "https://y.qq.com")
                        .header("referer", format!("https://y.qq.com/n/ryqq/playlist/{id}")),
                )
                .await?;
                check(&data, "code", "0", "QQ Music playlist")?;
                check(&data, "subcode", "0", "QQ Music playlist")?;
                let data = &data["cdlist"][0];
                Listing::read(
                    data,
                    &data["songlist"],
                    "songnum",
                    "dissname",
                    PLAYLIST_LIMIT,
                    |song| tencent(song, true),
                )
            }
            "wy" => {
                let data = json_response(
                    self.get(&format!(
                        "https://music.163.com/api/playlist/detail?id={id}&n={PLAYLIST_LIMIT}&s=8"
                    ))
                    .header("referer", "https://music.163.com/"),
                )
                .await?;
                check(&data, "code", "200", "NetEase playlist")?;
                let data = data.get("result").unwrap_or(&data["playlist"]);
                Listing::read(
                    data,
                    &data["tracks"],
                    "trackCount",
                    "name",
                    PLAYLIST_LIMIT,
                    netease,
                )
            }
            "mg" => self.migu_playlist(id).await?,
            _ => return Err(io::Error::other("unsupported playlist catalog")),
        })
    }

    async fn migu_playlist(&self, id: &str) -> io::Result<Listing> {
        let get_page = |page| async move {
            let url = format!(
                "https://app.c.nf.migu.cn/MIGUM3.0/resource/playlist/song/v2.0?pageNo={page}&pageSize=100&playlistId={id}"
            );
            let data =
                json_response(self.get(&url).header("referer", "https://m.music.migu.cn/")).await?;
            check(&data, "code", "000000", "Migu playlist")?;
            let data = &data["data"];
            Ok::<_, io::Error>(Listing::read(
                data,
                &data["songList"],
                "totalCount",
                "title",
                PLAYLIST_LIMIT,
                migu,
            ))
        };
        let mut result = get_page(1).await?;
        let pages = (result.total.min(1_000) + 99) / 100;
        let mut rest = stream::iter(2..=pages).map(get_page).buffered(4);
        while let Some(page) = rest.try_next().await? {
            result.tracks.extend(page.tracks);
        }
        result.tracks.truncate(PLAYLIST_LIMIT);
        Ok(result)
    }

    fn get(&self, url: &str) -> RequestBuilder {
        self.0.get(url).header("user-agent", AGENT)
    }
}

pub(super) fn playlist_id(source: &str, reference: &str) -> io::Result<String> {
    #[expect(
        clippy::expect_used,
        reason = "The playlist ID pattern is a source literal"
    )]
    static ID: LazyLock<regex::Regex> = LazyLock::new(|| {
        regex::Regex::new(r"(?i)(?:/(?:playlist(?:_detail)?|playsquare|special/single|plist/list)/|[?&#](?:playlistId|specialid|id)=)([0-9]{1,32})(?:[?&#/.]|$)").expect("playlist ID regex")
    });
    let reference = reference.trim();
    let domain = match source {
        "kw" => "kuwo.cn",
        "kg" => "kugou.com",
        "tx" => "qq.com",
        "wy" => "music.163.com",
        "mg" => "migu.cn",
        _ => "",
    };
    let url = http::address(reference)?;
    if reference.len() > 4096
        || !url.domain().is_some_and(|host| {
            host == domain
                || host
                    .strip_suffix(domain)
                    .is_some_and(|prefix| prefix.ends_with('.'))
        })
    {
        return Err(io::Error::other(
            "playlist URL does not belong to its catalog",
        ));
    }
    ID.captures(reference)
        .map(|captures| captures[1].to_owned())
        .ok_or_else(|| io::Error::other("playlist ID not found"))
}

async fn response(request: RequestBuilder) -> io::Result<Vec<u8>> {
    http::body(http::success(http::send(request).await?)?, MAX_BODY).await
}

async fn json_response(request: RequestBuilder) -> io::Result<Value> {
    serde_json::from_slice(&response(request).await?).map_err(io::Error::other)
}

fn check(data: &Value, key: &str, expected: &str, operation: &str) -> io::Result<()> {
    if text(&data[key]).is_some_and(|code| !code.eq_ignore_ascii_case(expected)) {
        return Err(io::Error::other(format!("{operation} failed")));
    }
    Ok(())
}

fn embedded(html: &str, name: &str) -> Option<Value> {
    let (_, assignment) = html.split_once(name)?;
    let json = assignment.trim_start().strip_prefix('=')?.trim_start();
    serde_json::Deserializer::from_str(json)
        .into_iter()
        .next()?
        .ok()
}

fn decoded(value: String) -> String {
    htmlescape::decode_html(&value).unwrap_or(value)
}

fn names(value: &Value) -> String {
    value
        .as_array()
        .into_iter()
        .flatten()
        .filter_map(|artist| first(artist, &["name", "singerName", "artistName"]))
        .collect::<Vec<_>>()
        .join(" / ")
}

fn base(
    source: &str,
    id: &str,
    name: String,
    singer: Option<String>,
    album: Option<String>,
    seconds: Option<u64>,
) -> Value {
    let interval = seconds.map(|seconds| format!("{}:{:02}", seconds / 60, seconds % 60));
    json!({"source":source,"id":format!("{source}_{id}"),"songmid":id,"name":decoded(name),
        "singer":singer.map(decoded),"albumName":album.map(decoded),"interval":interval,"types":[],"_types":{}})
}

fn add_quality(song: &mut Value, quality: &str, size: Value, hash: Option<String>) {
    if size.is_null() && hash.is_none() {
        return;
    }
    let mut info = Value::Object(serde_json::Map::from_iter([("size".into(), size)]));
    if let Some(hash) = hash {
        info["hash"] = hash.into();
    }
    song["_types"][quality] = info.clone();
    info["type"] = quality.into();
    if let Some(types) = song["types"].as_array_mut() {
        types.push(info);
    }
}

fn copy(song: &mut Value, raw: &Value, fields: &[(&str, &str)]) {
    for (from, to) in fields {
        if let Some(value) = text(&raw[from]) {
            song[to] = value.into();
        }
    }
}

fn size(raw: &Value, field: &str) -> Value {
    number(&raw[field])
        .filter(|size| *size != 0)
        .map_or(Value::Null, Value::from)
}

fn kuwo(raw: &Value, playlist: bool) -> Option<Value> {
    #[expect(
        clippy::expect_used,
        reason = "The Kuwo quality pattern is a source literal"
    )]
    static QUALITY: LazyLock<regex::Regex> = LazyLock::new(|| {
        regex::Regex::new(r"(?i)bitrate:(\d+)[^;]*?size:([^;,]+)").expect("Kuwo quality regex")
    });
    let (id, name, singer, album, duration, album_id) = if playlist {
        ("id", "name", "artist", "album", "duration", "albumid")
    } else {
        (
            "MUSICRID", "SONGNAME", "ARTIST", "ALBUM", "DURATION", "ALBUMID",
        )
    };
    let id = text(&raw[id])?;
    let mut song = base(
        "kw",
        id.trim_start_matches("MUSIC_"),
        first(raw, &[name, "NAME"])?,
        text(&raw[singer]),
        text(&raw[album]),
        number(&raw[duration]),
    );
    copy(&mut song, raw, &[(album_id, "albumId")]);
    if let Some(raw) = first(raw, &["N_MINFO", "MINFO"]) {
        for entry in QUALITY.captures_iter(&raw) {
            let quality = match &entry[1] {
                "4000" => "flac24bit",
                "2000" => "flac",
                "320" => "320k",
                "128" => "128k",
                _ => continue,
            };
            add_quality(&mut song, quality, entry[2].into(), None);
        }
    }
    Some(song)
}

fn kugou(raw: &Value, playlist: bool) -> Option<Value> {
    let (id, hash, name, singer, album, duration, album_field, fields) = if playlist {
        (
            first(raw, &["audio_id", "album_audio_id"])?,
            first(raw, &["hash", "hash_128", "HASH"])?,
            first(raw, &["songname", "audio_name"])?,
            first(raw, &["singername", "author_name"]),
            text(&raw["album_name"]),
            number(&raw["duration"]).map(|value| value / 1000),
            "album_id",
            [
                ("filesize", "hash"),
                ("filesize_320", "hash_320"),
                ("filesize_flac", "hash_flac"),
                ("filesize_high", "hash_high"),
            ],
        )
    } else {
        (
            first(raw, &["Audioid", "MixSongID", "ID"])?,
            text(&raw["FileHash"])?,
            text(&raw["SongName"])?,
            first(raw, &["SingerName", "FileName"]),
            text(&raw["AlbumName"]),
            number(&raw["Duration"]),
            "AlbumID",
            [
                ("FileSize", "FileHash"),
                ("HQFileSize", "HQFileHash"),
                ("SQFileSize", "SQFileHash"),
                ("ResFileSize", "ResFileHash"),
            ],
        )
    };
    let mut song = base("kg", &id, name, singer, album, duration);
    song["hash"] = hash.into();
    copy(
        &mut song,
        raw,
        &[(album_field, "albumId"), ("album_audio_id", "albumAudioId")],
    );
    for (quality, (size_field, hash_field)) in QUALITIES.into_iter().zip(fields) {
        if let Some(hash) = text(&raw[hash_field]) {
            add_quality(&mut song, quality, size(raw, size_field), Some(hash));
        }
    }
    Some(song)
}

const QUALITIES: [&str; 4] = ["128k", "320k", "flac", "flac24bit"];

fn tencent(raw: &Value, playlist: bool) -> Option<Value> {
    let id = first(raw, &["songmid", "mid"])?;
    let album = first(raw, &["albumname", "albumName"]).or_else(|| text(&raw["album"]["name"]));
    let mid =
        first(raw, &["strMediaMid", "media_mid"]).or_else(|| text(&raw["file"]["media_mid"]))?;
    let mut song = base(
        "tx",
        &id,
        first(raw, &["songname", "title", "name"])?,
        Some(names(&raw["singer"])),
        album,
        number(&raw["interval"]),
    );
    song["strMediaMid"] = mid.into();
    if playlist {
        copy(
            &mut song,
            raw,
            &[
                ("songid", "songId"),
                ("albummid", "albumId"),
                ("albummid", "albumMid"),
            ],
        );
    } else {
        copy(&mut song, raw, &[("id", "songId")]);
        copy(
            &mut song,
            &raw["album"],
            &[("mid", "albumId"), ("mid", "albumMid")],
        );
    }
    let (file, fields) = if playlist {
        (raw, ["size128", "size320", "sizeflac", "size_hires"])
    } else {
        (
            &raw["file"],
            ["size_128mp3", "size_320mp3", "size_flac", "size_hires"],
        )
    };
    for (quality, field) in QUALITIES.into_iter().zip(fields) {
        add_quality(&mut song, quality, size(file, field), None);
    }
    Some(song)
}

fn netease(raw: &Value) -> Option<Value> {
    let album = raw.get("album").unwrap_or(&raw["al"]);
    let mut song = base(
        "wy",
        &text(&raw["id"])?,
        text(&raw["name"])?,
        Some(names(raw.get("artists").unwrap_or(&raw["ar"]))),
        text(&album["name"]),
        number(raw.get("duration").unwrap_or(&raw["dt"])).map(|ms| ms / 1000),
    );
    copy(&mut song, album, &[("id", "albumId")]);
    for (quality, (long, short)) in QUALITIES.into_iter().zip([
        ("lMusic", "l"),
        ("hMusic", "h"),
        ("sqMusic", "sq"),
        ("hrMusic", "hr"),
    ]) {
        add_quality(
            &mut song,
            quality,
            size(raw.get(long).unwrap_or(&raw[short]), "size"),
            None,
        );
    }
    Some(song)
}

fn migu(raw: &Value) -> Option<Value> {
    let copyright = text(&raw["copyrightId"])?;
    let mut song = base(
        "mg",
        &text(&raw["songId"])?,
        first(raw, &["name", "songName"])?,
        Some(names(&raw["singerList"])),
        text(&raw["album"]),
        number(&raw["duration"]),
    );
    song["copyrightId"] = copyright.into();
    copy(
        &mut song,
        raw,
        &[
            ("albumId", "albumId"),
            ("lrcUrl", "lrcUrl"),
            ("mrcurl", "mrcUrl"),
            ("trcUrl", "trcUrl"),
        ],
    );
    for format in raw["audioFormats"].as_array().into_iter().flatten() {
        let quality = match format["formatType"].as_str() {
            Some("PQ") => "128k",
            Some("HQ") => "320k",
            Some("SQ") => "flac",
            Some("ZQ24") => "flac24bit",
            _ => continue,
        };
        let size = number(&format["asize"])
            .filter(|size| *size != 0)
            .or_else(|| number(&format["isize"]));
        add_quality(
            &mut song,
            quality,
            size.map_or(Value::Null, Value::from),
            None,
        );
    }
    Some(song)
}
