use flate2::{Compression, Decompress, FlushDecompress, Status, write::ZlibEncoder};
use futures_util::{
    FutureExt,
    future::{BoxFuture, Shared},
};
use std::{
    io::{self, Write},
    sync::{Arc, OnceLock},
};
use zeroize::Zeroizing;

pub(super) const APPLICATION: u8 = 0;
pub(super) const COMPRESSED: u8 = 1;
pub(super) const MAX_REQUEST: usize = 8192;
pub(super) const MAX_RESPONSE: usize = 128 * 1024;

pub(super) struct Record {
    pub(super) kind: u8,
    pub(super) payload: Vec<u8>,
}

impl Record {
    pub(super) async fn prepare(json: String, compress: bool) -> io::Result<Self> {
        if compress {
            tokio::task::spawn_blocking(move || Self::encode(json, true)).await?
        } else {
            Self::encode(json, false)
        }
    }

    fn encode(json: String, compress: bool) -> io::Result<Self> {
        let raw = Self::plain(json.into_bytes())?;
        if compress && let Some(smaller) = raw.deflate()? {
            return Ok(smaller);
        }
        Ok(raw)
    }

    fn plain(payload: Vec<u8>) -> io::Result<Self> {
        if payload.len() > MAX_RESPONSE {
            return Err(io::Error::other("chat response too large"));
        }
        Ok(Self {
            kind: APPLICATION,
            payload,
        })
    }

    fn deflate(&self) -> io::Result<Option<Self>> {
        let size = u32::try_from(self.payload.len()).map_err(io::Error::other)?;
        let mut encoder = ZlibEncoder::new(size.to_be_bytes().to_vec(), Compression::fast());
        encoder.write_all(&self.payload)?;
        let payload = encoder.finish()?;
        Ok((payload.len() < self.payload.len()).then_some(Self {
            kind: COMPRESSED,
            payload,
        }))
    }
}

type CompressionTask = Shared<BoxFuture<'static, Result<Arc<Record>, Arc<io::Error>>>>;

/// One serialized variant of a live event, shared only until the event is released.
pub(super) struct SharedRecord {
    plain: Arc<Record>,
    compression: OnceLock<CompressionTask>,
}

impl SharedRecord {
    pub(super) fn new(payload: Vec<u8>) -> io::Result<Self> {
        Ok(Self {
            plain: Arc::new(Record::plain(payload)?),
            compression: OnceLock::new(),
        })
    }

    pub(super) async fn select(&self, compress: bool) -> io::Result<Arc<Record>> {
        if !compress {
            return Ok(self.plain.clone());
        }
        let task = self.compression.get_or_init(|| {
            let plain = self.plain.clone();
            // Start once even if the first waiting socket disconnects.
            tokio::task::spawn_blocking(move || Ok(plain.deflate()?.map_or(plain, Arc::new)))
                .map(|joined| {
                    joined
                        .map_err(io::Error::other)
                        .and_then(|record| record)
                        .map_err(Arc::new)
                })
                .boxed()
                .shared()
        });
        task.clone().await.map_err(io::Error::other)
    }
}

/// One complete zlib stream. A fixed output buffer bounds native expansion, including on errors.
pub(super) fn inflate_request(packet: &[u8]) -> io::Result<Zeroizing<Vec<u8>>> {
    let invalid = || io::Error::other("invalid compressed chat request");
    let (length, stream) = packet.split_first_chunk::<4>().ok_or_else(invalid)?;
    let expected = u32::from_be_bytes(*length) as usize;
    if expected > MAX_REQUEST || packet.len() >= expected {
        return Err(invalid());
    }
    let mut output = Zeroizing::new(vec![0; expected]);
    let mut decoder = Decompress::new(true);
    if decoder.decompress(stream, &mut output, FlushDecompress::Finish)? != Status::StreamEnd
        || decoder.total_in() != stream.len() as u64
        || decoder.total_out() != expected as u64
    {
        return Err(invalid());
    }
    Ok(output)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn includes_prefix_and_selects_even_one_byte_of_savings() {
        let mut tie = false;
        let mut saved_one = false;
        for len in 1..64 {
            let plain = "a".repeat(len);
            let mut encoder = ZlibEncoder::new(Vec::new(), Compression::fast());
            encoder.write_all(plain.as_bytes()).unwrap();
            let size = encoder.finish().unwrap().len() + 4;
            let record = Record::encode(plain.clone(), true).unwrap();
            tie |= size == len;
            saved_one |= size + 1 == len;
            if size < len {
                assert_eq!(record.kind, COMPRESSED);
                assert_eq!(
                    &**inflate_request(&record.payload).unwrap(),
                    plain.as_bytes()
                );
            } else {
                assert_eq!(record.kind, APPLICATION);
                assert_eq!(record.payload, plain.as_bytes());
            }
        }
        assert!(tie && saved_one);
        assert!(Record::encode("a".repeat(MAX_RESPONSE + 1), true).is_err());
    }

    #[test]
    fn requires_exactly_one_complete_authenticated_request_within_limit() {
        let packet = Record::encode("x".repeat(MAX_REQUEST), true)
            .unwrap()
            .payload;
        assert_eq!(inflate_request(&packet).unwrap().len(), MAX_REQUEST);
        for size in [0, 32, MAX_REQUEST - 1, MAX_REQUEST + 1] {
            let mut altered = packet.clone();
            altered[..4].copy_from_slice(&u32::try_from(size).unwrap().to_be_bytes());
            assert!(inflate_request(&altered).is_err());
        }
        assert!(inflate_request(&packet[..packet.len() - 1]).is_err());
        let mut trailing = packet.clone();
        trailing.push(0);
        assert!(inflate_request(&trailing).is_err());
        let mut corrupt = packet;
        *corrupt.last_mut().unwrap() ^= 1;
        assert!(inflate_request(&corrupt).is_err());
    }
}
