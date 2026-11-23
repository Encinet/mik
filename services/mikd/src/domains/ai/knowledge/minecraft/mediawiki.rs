//! `MediaWiki` transport. Turn-time calls carry only public article/revision and section numbers.
use super::{WikiEdition, edition};
use crate::http_egress::direct_client;
use reqwest::{Client, StatusCode, Url, header};
use serde::{Deserialize, de::DeserializeOwned};
use std::{collections::HashMap, io, time::Duration};

const METADATA_LIMIT: usize = 256 * 1024;
const SECTION_LIMIT: usize = 768 * 1024;

pub(super) struct Api {
    client: Client,
    endpoints: HashMap<WikiEdition, Url>,
}

impl Api {
    pub(super) fn new() -> io::Result<Self> {
        let endpoints = edition::registered()?
            .iter()
            .map(|site| {
                let url = Url::parse(&format!("https://{}{}", site.canonical.host, site.api_path))
                    .map_err(io::Error::other)?;
                Ok((site.edition, url))
            })
            .collect::<io::Result<_>>()?;
        Ok(Self {
            client: client(true)?,
            endpoints,
        })
    }

    #[cfg(test)]
    pub(super) fn set_test_origin(&mut self, origin: &str) -> io::Result<()> {
        let origin = Url::parse(origin).map_err(io::Error::other)?;
        self.client = client(false)?;
        for site in edition::registered()? {
            let url = origin
                .join(&format!("{}{}", site.edition.code(), site.api_path))
                .map_err(io::Error::other)?;
            self.endpoints.insert(site.edition, url);
        }
        Ok(())
    }

    pub(super) async fn get<T: DeserializeOwned>(
        &self,
        edition: WikiEdition,
        query: &[(&str, &str)],
        byte_limit: usize,
    ) -> io::Result<T> {
        let mut url = self
            .endpoints
            .get(&edition)
            .ok_or_else(|| invalid("Minecraft Wiki edition has no verified API endpoint"))?
            .clone();
        url.query_pairs_mut()
            .append_pair("format", "json")
            .append_pair("formatversion", "2")
            .extend_pairs(query.iter().copied());
        let mut response = self
            .client
            .get(url)
            .header(header::ACCEPT, "application/json")
            .header(header::ACCEPT_ENCODING, "identity")
            .header(
                header::USER_AGENT,
                "MIK-AI/1.0 (https://github.com/Encinet/mik)",
            )
            .send()
            .await
            .map_err(|error| network_error(&error))?;
        if response.status() != StatusCode::OK {
            let status = response.status();
            let kind = match status {
                StatusCode::UNAUTHORIZED | StatusCode::FORBIDDEN => io::ErrorKind::PermissionDenied,
                StatusCode::TOO_MANY_REQUESTS | StatusCode::REQUEST_TIMEOUT => {
                    io::ErrorKind::WouldBlock
                }
                _ if status.is_server_error() => io::ErrorKind::WouldBlock,
                _ => io::ErrorKind::Other,
            };
            return Err(io::Error::new(
                kind,
                format!("Minecraft Wiki API returned HTTP {status}"),
            ));
        }
        if !response
            .headers()
            .get(header::CONTENT_TYPE)
            .and_then(|value| value.to_str().ok())
            .and_then(|value| value.split(';').next())
            .is_some_and(|value| value.trim().eq_ignore_ascii_case("application/json"))
        {
            return Err(invalid("unexpected Minecraft Wiki content type"));
        }
        let too_large = || invalid("Minecraft Wiki response exceeds its byte limit");
        if response
            .content_length()
            .is_some_and(|size| size > byte_limit as u64)
        {
            return Err(too_large());
        }
        let mut bytes = Vec::new();
        while let Some(chunk) = response
            .chunk()
            .await
            .map_err(|error| network_error(&error))?
        {
            if chunk.len() > byte_limit - bytes.len() {
                return Err(too_large());
            }
            bytes.extend_from_slice(&chunk);
        }
        match serde_json::from_slice(&bytes)
            .map_err(|_| invalid("invalid Minecraft Wiki API response"))?
        {
            ApiResponse::Data(data) => Ok(data),
            ApiResponse::Error { error } => Err(error.into_io()),
        }
    }

    pub(super) async fn resolve(&self, edition: WikiEdition, id: u64) -> io::Result<Page> {
        let response: QueryResponse = self
            .get(
                edition,
                &[
                    ("action", "query"),
                    ("prop", "info"),
                    ("pageids", &id.to_string()),
                    ("redirects", "1"),
                ],
                METADATA_LIMIT,
            )
            .await?;
        let [page]: [PageInfo; 1] = response
            .query
            .pages
            .try_into()
            .map_err(|_| invalid("expected one resolved Minecraft Wiki page"))?;
        if page.missing {
            return Err(io::Error::new(
                io::ErrorKind::NotFound,
                "Minecraft Wiki article was not found",
            ));
        }
        if page.ns != 0 {
            return Err(io::Error::new(
                io::ErrorKind::PermissionDenied,
                "Minecraft Wiki article is outside the public article namespace",
            ));
        }
        if page.pageid == 0 || page.lastrevid == 0 {
            return Err(invalid("missing Minecraft Wiki article or revision ID"));
        }
        Ok(Page {
            id: page.pageid,
            title: page.title,
            revision: page.lastrevid,
            redirect_section: response
                .query
                .redirects
                .into_iter()
                .rev()
                .find_map(|redirect| redirect.tofragment),
        })
    }

    pub(super) async fn outline(
        &self,
        edition: WikiEdition,
        page: &Page,
    ) -> io::Result<Vec<Section>> {
        let result = self.parse(edition, page, None).await?;
        Ok(result.tocdata.map_or_else(Vec::new, |toc| toc.sections))
    }

    pub(super) async fn section(
        &self,
        edition: WikiEdition,
        page: &Page,
        index: u32,
    ) -> io::Result<String> {
        self.parse(edition, page, Some(index))
            .await?
            .text
            .ok_or_else(|| invalid("missing Minecraft Wiki section text"))
    }

    async fn parse(
        &self,
        edition: WikiEdition,
        page: &Page,
        section: Option<u32>,
    ) -> io::Result<ParsedPage> {
        let revision = page.revision.to_string();
        let mut query = vec![
            ("action", "parse"),
            ("oldid", revision.as_str()),
            ("disableeditsection", "1"),
            ("disablelimitreport", "1"),
        ];
        let section_index;
        let limit = if let Some(section) = section {
            section_index = section.to_string();
            query.extend([("prop", "text|revid"), ("section", section_index.as_str())]);
            SECTION_LIMIT
        } else {
            query.push(("prop", "tocdata|revid"));
            METADATA_LIMIT
        };
        let result: ParseResponse = self.get(edition, &query, limit).await?;
        if result.parse.pageid != page.id
            || result.parse.revid != page.revision
            || result.parse.title != page.title
        {
            return Err(invalid(
                "Minecraft Wiki returned a different article revision",
            ));
        }
        Ok(result.parse)
    }
}

fn client(https_only: bool) -> io::Result<Client> {
    // Only the fixed HTTPS authority is contacted, even under split DNS. Never follow HTTP redirects.
    direct_client(true)
        .https_only(https_only)
        .connect_timeout(Duration::from_secs(3))
        .timeout(Duration::from_secs(5))
        .build()
        .map_err(io::Error::other)
}

fn invalid(message: &'static str) -> io::Error {
    io::Error::new(io::ErrorKind::InvalidData, message)
}

fn network_error(error: &reqwest::Error) -> io::Error {
    if error.is_timeout() {
        io::Error::new(io::ErrorKind::TimedOut, "Minecraft Wiki request timed out")
    } else if error.is_connect() {
        io::Error::new(
            io::ErrorKind::ConnectionAborted,
            "could not connect to Minecraft Wiki",
        )
    } else {
        io::Error::other("Minecraft Wiki response transfer failed")
    }
}

#[derive(Deserialize)]
#[serde(untagged)]
enum ApiResponse<T> {
    Error { error: ApiError },
    Data(T),
}

#[derive(Deserialize)]
struct ApiError {
    code: String,
}

impl ApiError {
    fn into_io(self) -> io::Error {
        if self.code == "maxlag" {
            return io::Error::new(
                io::ErrorKind::WouldBlock,
                "Minecraft Wiki API is busy (maxlag)",
            );
        }
        let kind = match self.code.as_str() {
            "missingtitle" | "nosuchpageid" | "nosuchsection" => io::ErrorKind::NotFound,
            "readapidenied" | "permissiondenied" => io::ErrorKind::PermissionDenied,
            _ => io::ErrorKind::Other,
        };
        io::Error::new(kind, "Minecraft Wiki API rejected the lookup")
    }
}

pub(super) struct Page {
    pub(super) id: u64,
    pub(super) title: String,
    pub(super) revision: u64,
    pub(super) redirect_section: Option<String>,
}

#[derive(Deserialize)]
struct QueryResponse {
    query: PageQuery,
}

#[derive(Deserialize)]
struct PageQuery {
    pages: Vec<PageInfo>,
    #[serde(default)]
    redirects: Vec<PageRedirect>,
}

#[derive(Deserialize)]
struct PageInfo {
    #[serde(default)]
    pageid: u64,
    ns: i32,
    title: String,
    #[serde(default)]
    lastrevid: u64,
    #[serde(default)]
    missing: bool,
}

#[derive(Deserialize)]
struct PageRedirect {
    tofragment: Option<String>,
}

#[derive(Deserialize)]
struct ParseResponse {
    parse: ParsedPage,
}

#[derive(Deserialize)]
struct ParsedPage {
    pageid: u64,
    revid: u64,
    title: String,
    tocdata: Option<Outline>,
    text: Option<String>,
}

#[derive(Deserialize)]
struct Outline {
    sections: Vec<Section>,
}

#[derive(Deserialize)]
pub(super) struct Section {
    pub(super) index: String,
    pub(super) line: String,
    pub(super) anchor: String,
}
