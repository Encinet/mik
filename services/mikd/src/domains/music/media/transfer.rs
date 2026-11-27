use super::{Entry, Media, MediaInfo, Progress, Source, cancelled};
use crate::domains::music::source::{log_playback_source, playback_source_error};
use std::{io, time::Duration};
use tokio::{
    fs::{self, File},
    io::{AsyncWriteExt, BufWriter},
    time::{Instant, sleep_until, timeout},
};

const PUBLISH_BYTES: usize = 64 * 1024;
const PUBLISH_DELAY: Duration = Duration::from_millis(20);

impl Media {
    pub(in crate::domains::music) async fn test_audio_start(
        &self,
        url: url::Url,
    ) -> io::Result<()> {
        let _permit = self.downloads.acquire().await.map_err(io::Error::other)?;
        let mut response = self.request(url).await?;
        while let Some(chunk) = response
            .chunk()
            .await
            .map_err(|error| io::Error::other(error.without_url()))?
        {
            if !chunk.is_empty() {
                return Ok(());
            }
        }
        Err(io::Error::other("audio provider returned no data"))
    }

    pub(super) async fn transfer(
        &self,
        source: &Source,
        entry: &Entry,
        excluded: Vec<String>,
    ) -> io::Result<(MediaInfo, u64)> {
        let complete = tokio::select! {
            biased;
            () = entry.cancel.cancelled() => return Err(cancelled()),
            () = entry.promoted.cancelled() => None,
            permit = self.complete_only.acquire() => Some(permit.map_err(io::Error::other)?),
        };
        let transfer = self.download_bytes(source, entry, excluded);
        tokio::pin!(transfer);
        if let Some(permit) = complete {
            tokio::select! {
                biased;
                () = entry.promoted.cancelled() => drop(permit),
                result = &mut transfer => return result,
            }
        }
        transfer.await
    }

    async fn download_bytes(
        &self,
        source: &Source,
        entry: &Entry,
        excluded: Vec<String>,
    ) -> io::Result<(MediaInfo, u64)> {
        let _permit = tokio::select! {
            biased;
            () = entry.cancel.cancelled() => return Err(cancelled()),
            permit = self.downloads.acquire() => permit.map_err(io::Error::other)?,
        };
        let (mut response, mut info) = tokio::select! {
            biased;
            () = entry.cancel.cancelled() => return Err(cancelled()),
            prepared = timeout(Duration::from_secs(60), self.prepare(source, entry, excluded)) => prepared??,
        };
        let maximum = info.byte_length.unwrap_or(self.file_bytes);
        self.reserve(entry, maximum).await?;
        if entry.cancel.is_cancelled() {
            return Err(cancelled());
        }
        let file = fs::OpenOptions::new()
            .write(true)
            .create_new(true)
            .open(&entry.path)
            .await?;
        let mut file = BufWriter::with_capacity(2 * PUBLISH_BYTES, file);
        entry.progress.send_replace(Progress::Growing {
            info: info.clone(),
            bytes: 0,
        });
        let mut written = 0_u64;
        let mut published = 0_u64;
        let mut publish_at = Instant::now();
        loop {
            let chunk = tokio::select! {
                biased;
                () = entry.cancel.cancelled() => {
                    file.into_inner().flush().await?;
                    return Err(cancelled());
                }
                () = sleep_until(publish_at), if written != published => {
                    publish(entry, &mut file, written).await?;
                    published = written;
                    continue;
                }
                chunk = response.chunk() => chunk.map_err(|error| io::Error::other(error.without_url()))?,
            };
            let Some(chunk) = chunk else { break };
            if chunk.len() as u64 > maximum - written {
                return Err(io::Error::other("audio exceeds the reserved file size"));
            }
            file.write_all(&chunk).await?;
            if written == published {
                publish_at = Instant::now() + PUBLISH_DELAY;
            }
            written += chunk.len() as u64;
            if published == 0 || written - published >= PUBLISH_BYTES as u64 {
                publish(entry, &mut file, written).await?;
                published = written;
            }
        }
        if written == 0 || info.byte_length.is_some_and(|expected| expected != written) {
            return Err(io::Error::new(
                io::ErrorKind::UnexpectedEof,
                "incomplete audio download",
            ));
        }
        if written != published {
            publish(entry, &mut file, written).await?;
        }
        file.get_ref().sync_data().await?;
        info.byte_length = Some(written);
        Ok((info, written))
    }

    async fn prepare(
        &self,
        source: &Source,
        entry: &Entry,
        mut excluded: Vec<String>,
    ) -> io::Result<(reqwest::Response, MediaInfo)> {
        let input = source
            .resolver_input(&entry.track)
            .await?
            .ok_or_else(|| io::Error::new(io::ErrorKind::NotFound, "unknown resolver key"))?;
        let mut last_error = None;
        for _ in 0..4 {
            let resolved = match source
                .resolve(&entry.track, entry.version, &input, &excluded)
                .await
            {
                Ok(resolved) => resolved,
                Err(error) => return Err(last_error.unwrap_or(error)),
            };
            match self.request(resolved.url).await {
                Ok(response) => {
                    log_playback_source(
                        &entry.track.platform,
                        entry.version,
                        &resolved.provider_id,
                        "stream_opened",
                    );
                    let info = MediaInfo {
                        byte_length: response.content_length(),
                        provider: resolved.provider_id,
                        content_type: response
                            .headers()
                            .get(reqwest::header::CONTENT_TYPE)
                            .and_then(|value| value.to_str().ok())
                            .map(|value| value.split(';').next().unwrap_or(value))
                            .filter(|value| value.len() <= 128 && value.is_ascii())
                            .map(str::to_owned),
                    };
                    return Ok((response, info));
                }
                Err(error) => {
                    log_playback_source(
                        &entry.track.platform,
                        entry.version,
                        &resolved.provider_id,
                        "request_failed",
                    );
                    last_error = Some(playback_source_error(
                        &resolved.provider_id,
                        "audio request",
                        &error,
                    ));
                    excluded.push(resolved.provider_id);
                }
            }
        }
        Err(last_error.unwrap_or_else(|| io::Error::other("no audio source available")))
    }

    async fn request(&self, url: url::Url) -> io::Result<reqwest::Response> {
        let response = self
            .http
            .get(url)
            .header(reqwest::header::USER_AGENT, "MIK-Music/1.0")
            .header(
                reqwest::header::ACCEPT,
                "audio/*, application/octet-stream;q=0.9, */*;q=0.1",
            )
            .send()
            .await
            .map_err(|error| io::Error::other(error.without_url()))?;
        if response.status() != reqwest::StatusCode::OK {
            return Err(io::Error::other(format!(
                "audio provider returned HTTP {}",
                response.status()
            )));
        }
        if response
            .content_length()
            .is_some_and(|length| length == 0 || length > self.file_bytes)
        {
            return Err(io::Error::other("invalid audio content length"));
        }
        if response
            .headers()
            .get(reqwest::header::CONTENT_TYPE)
            .and_then(|value| value.to_str().ok())
            .is_some_and(|value| {
                value.starts_with("text/") || value.starts_with("application/json")
            })
        {
            return Err(io::Error::other(
                "music provider returned text instead of audio",
            ));
        }
        Ok(response)
    }
}

async fn publish(entry: &Entry, file: &mut BufWriter<File>, written: u64) -> io::Result<()> {
    file.flush().await?;
    entry.progress.send_modify(|progress| {
        if let Progress::Growing { bytes, .. } = progress {
            *bytes = written;
        }
    });
    Ok(())
}
