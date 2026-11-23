use reqwest::{Client, RequestBuilder, Response, Url};
use std::{io, time::Duration};

pub(super) const TIMEOUT: Duration = Duration::from_secs(20);

pub(super) fn address(value: &str) -> io::Result<Url> {
    Url::parse(value)
        .ok()
        .filter(|url| {
            matches!(url.scheme(), "http" | "https")
                && url.has_host()
                && url.username().is_empty()
                && url.password().is_none()
        })
        .ok_or_else(|| io::Error::other("invalid HTTP(S) URL"))
}

pub(super) fn client() -> io::Result<Client> {
    crate::http_egress::client_builder()
        .no_proxy()
        .timeout(TIMEOUT)
        .redirect(reqwest::redirect::Policy::none())
        .build()
        .map_err(io::Error::other)
}

pub(super) async fn send(request: RequestBuilder) -> io::Result<Response> {
    request
        .send()
        .await
        .map_err(|error| io::Error::other(error.without_url()))
}

pub(super) async fn body(mut response: Response, limit: usize) -> io::Result<Vec<u8>> {
    if response
        .content_length()
        .is_some_and(|size| size > limit as u64)
    {
        return Err(io::Error::other("music response exceeds size limit"));
    }
    let mut bytes = Vec::new();
    while let Some(chunk) = response
        .chunk()
        .await
        .map_err(|error| io::Error::other(error.without_url()))?
    {
        if chunk.len() > limit - bytes.len() {
            return Err(io::Error::other("music response exceeds size limit"));
        }
        bytes.extend_from_slice(&chunk);
    }
    Ok(bytes)
}

pub(super) fn success(response: Response) -> io::Result<Response> {
    if !response.status().is_success() {
        return Err(io::Error::other(format!(
            "music provider returned HTTP {}",
            response.status()
        )));
    }
    Ok(response)
}
