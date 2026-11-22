use base64::{Engine, engine::general_purpose::URL_SAFE_NO_PAD as BASE64};
use hpke::{
    Deserializable, Kem, OpModeR, OpModeS, Serializable,
    aead::{AeadCtxR, AeadCtxS, AesGcm256},
    kdf::HkdfSha256,
    kem::DhP256HkdfSha256,
    setup_receiver, setup_sender_with_rng,
};
use p256::ecdsa::{Signature, SigningKey, signature::Signer};
use p256::pkcs8::{DecodePrivateKey, EncodePrivateKey};
use rand::{SeedableRng, rngs::StdRng};
use serde::Deserialize;
use serde_json::{Value, json};
use std::{
    fs,
    io::{self, Read, Write},
    os::unix::fs::{OpenOptionsExt, PermissionsExt},
    path::Path,
};
use subtle::ConstantTimeEq;
use zeroize::{Zeroize, Zeroizing};

type Suite = DhP256HkdfSha256;
type Sender = AeadCtxS<AesGcm256, HkdfSha256, Suite>;
type Receiver = AeadCtxR<AesGcm256, HkdfSha256, Suite>;
pub(super) const APPLICATION: u8 = 0;
pub(super) const EXCHANGE: u8 = 1;
const PROTOCOL: &str = "mik-chat-hpke-2026-11-13\0https://mcmik.top\0";

pub(super) struct Identity(SigningKey);

impl Identity {
    pub(super) fn open(path: &Path) -> io::Result<Self> {
        let read = || -> io::Result<Self> {
            let file = fs::OpenOptions::new()
                .read(true)
                .custom_flags(rustix::fs::OFlags::NOFOLLOW.bits().cast_signed())
                .open(path)?;
            let metadata = file.metadata()?;
            if !metadata.is_file() || metadata.permissions().mode() & 0o077 != 0 {
                return Err(io::Error::other("web chat signing key must be private"));
            }
            let mut der = Zeroizing::new(Vec::new());
            file.take(1025).read_to_end(&mut der)?;
            let key = SigningKey::from_pkcs8_der(&der).map_err(io::Error::other)?;
            Ok(Self(key))
        };
        match read() {
            Err(error) if error.kind() == io::ErrorKind::NotFound => {}
            result => return result,
        }
        let (secret, _) = Suite::gen_keypair_with_rng(&mut rng().map_err(io::Error::other)?);
        let key = SigningKey::from_slice(&secret.to_bytes()).map_err(io::Error::other)?;
        let der = key.to_pkcs8_der().map_err(io::Error::other)?;
        let mut file = fs::OpenOptions::new()
            .write(true)
            .create_new(true)
            .mode(0o600)
            .open(path)?;
        file.write_all(der.as_bytes())?;
        file.sync_all()?;
        Ok(Self(key))
    }

    pub(super) fn public_key(&self) -> String {
        BASE64.encode(self.0.verifying_key().to_sec1_point(false).as_bytes())
    }
}

fn rng() -> Result<StdRng, InvalidKey> {
    let mut seed = Zeroizing::new([0; 32]);
    getrandom::fill(&mut *seed).map_err(|_| InvalidKey)?;
    Ok(StdRng::from_seed(*seed))
}

#[derive(Debug, thiserror::Error)]
#[error("invalid web chat key exchange")]
pub(super) struct InvalidKey;

pub(super) struct Exchange<'a> {
    id: &'a str,
    bound_key: &'a str,
    generation: u64,
    keys: (<Suite as Kem>::PrivateKey, <Suite as Kem>::PublicKey),
}

#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
struct Reply {
    #[serde(rename = "type")]
    kind: String,
    generation: u64,
    enc: String,
    ciphertext: String,
}

#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
struct Finished {
    #[serde(rename = "type")]
    kind: String,
    generation: u64,
    verify: String,
    compression: Option<String>,
}

impl<'a> Exchange<'a> {
    pub(super) fn begin(
        identity: &Identity,
        id: &'a str,
        bound_key: &'a str,
        generation: u64,
    ) -> Result<(Self, Value), InvalidKey> {
        let keys = Suite::gen_keypair_with_rng(&mut rng()?);
        let key = BASE64.encode(keys.1.to_bytes());
        let signature: Signature = identity
            .0
            .sign(info(id, bound_key, generation, &format!("key\0{key}")).as_bytes());
        let announcement = json!({"type":"key", "generation":generation, "key":key, "signature":BASE64.encode(signature.to_bytes())});
        Ok((
            Self {
                id,
                bound_key,
                generation,
                keys,
            },
            announcement,
        ))
    }

    pub(super) fn accept(self, bytes: &[u8]) -> Result<(Keys, Value), InvalidKey> {
        let reply: Reply = serde_json::from_slice(bytes).map_err(|_| InvalidKey)?;
        if reply.kind != "key" || reply.generation != self.generation {
            return Err(InvalidKey);
        }
        let enc = <Suite as Kem>::EncappedKey::from_bytes(
            &BASE64.decode(reply.enc).map_err(|_| InvalidKey)?,
        )
        .map_err(|_| InvalidKey)?;
        let mut incoming = setup_receiver::<AesGcm256, HkdfSha256, Suite>(
            &OpModeR::Base,
            &self.keys.0,
            &enc,
            info(self.id, self.bound_key, self.generation, "client").as_bytes(),
        )
        .map_err(|_| InvalidKey)?;
        let clear = incoming
            .open(
                &BASE64.decode(reply.ciphertext).map_err(|_| InvalidKey)?,
                &[],
            )
            .map_err(|_| InvalidKey)?;
        if self.generation == 0 && BASE64.encode(&clear) != self.bound_key {
            return Err(InvalidKey);
        }
        let peer = <Suite as Kem>::PublicKey::from_bytes(&clear).map_err(|_| InvalidKey)?;
        let (enc, mut outgoing) = setup_sender_with_rng::<AesGcm256, HkdfSha256, Suite>(
            &OpModeS::Auth(self.keys),
            &peer,
            info(self.id, self.bound_key, self.generation, "server").as_bytes(),
            &mut rng()?,
        )
        .map_err(|_| InvalidKey)?;
        let confirmation = outgoing.seal(b"ready", &[]).map_err(|_| InvalidKey)?;
        let mut proof = Zeroizing::new([0; 32]);
        outgoing
            .export(
                info(self.id, self.bound_key, self.generation, "client-finished").as_bytes(),
                &mut *proof,
            )
            .map_err(|_| InvalidKey)?;
        Ok((
            Keys {
                incoming,
                outgoing,
                proof,
                generation: self.generation,
                read: 0,
                written: 0,
            },
            json!({"type":"key", "generation":self.generation, "enc":BASE64.encode(enc.to_bytes()), "ciphertext":BASE64.encode(confirmation)}),
        ))
    }
}

fn info(id: &str, bound_key: &str, generation: u64, label: &str) -> String {
    format!("{PROTOCOL}{id}\0{generation}\0{bound_key}\0{label}")
}

pub(super) struct Keys {
    incoming: Receiver,
    outgoing: Sender,
    proof: Zeroizing<[u8; 32]>,
    pub(super) generation: u64,
    read: u32,
    written: u32,
}

impl Keys {
    pub(super) fn finish(&mut self, frame: &[u8]) -> Result<(), InvalidKey> {
        let clear = self.open(frame)?;
        let Some((&EXCHANGE, body)) = clear.split_first() else {
            return Err(InvalidKey);
        };
        let finished: Finished = serde_json::from_slice(body).map_err(|_| InvalidKey)?;
        let proof = Zeroizing::new(BASE64.decode(finished.verify).map_err(|_| InvalidKey)?);
        if finished.kind != "finished"
            || finished.generation != self.generation
            || finished
                .compression
                .as_deref()
                .is_some_and(|name| name != "deflate")
            || !bool::from(proof.as_slice().ct_eq(&*self.proof))
        {
            return Err(InvalidKey);
        }
        self.proof.zeroize();
        Ok(())
    }

    pub(super) fn needs_rotation(&self) -> bool {
        self.read >= 16_384 || self.written >= 16_384
    }

    pub(super) fn open(&mut self, frame: &[u8]) -> Result<Zeroizing<Vec<u8>>, InvalidKey> {
        if self.read >= 16_640 {
            return Err(InvalidKey);
        }
        self.read += 1;
        self.incoming
            .open(frame, &[])
            .map(Zeroizing::new)
            .map_err(|_| InvalidKey)
    }

    pub(super) fn seal(&mut self, kind: u8, json: &str) -> Result<Vec<u8>, InvalidKey> {
        if self.written >= 16_640 || json.len() > 128 * 1024 {
            return Err(InvalidKey);
        }
        self.written += 1;
        let mut clear = Zeroizing::new(Vec::with_capacity(json.len() + 1));
        clear.push(kind);
        clear.extend_from_slice(json.as_bytes());
        self.outgoing.seal(&clear, &[]).map_err(|_| InvalidKey)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn accepts_browser_hpke_packets_in_sequence() {
        // @hpke/core 1.10.1, DHKEM(P-256)/HKDF-SHA256/AES-256-GCM.
        // Server/client/encapsulation derivation material: 32 bytes of 42/13/7.
        let exchange = Exchange {
            id: "fixture-session",
            bound_key: "BOUalKD2O0rYW_fwMaZraz-ZXga7g_WxYIwM67W3969b3czl3FsgCgA9wO7rWvbn4ntvL284VT4O0VzptSeGvGQ",
            generation: 0,
            keys: Suite::derive_keypair(&[42; 32]),
        };
        let browser_key = br#"{
            "type":"key","generation":0,
            "enc":"BHOOCgd5I7robQEmamEVXdGFIBuqWJDqkXd2AiQH0K4Snsb_gqVpfZypds301wuAunj6Izm9_xbdBa8lu7Vijfw",
            "ciphertext":"h-H7fkKOlF_hGXechQZf53XJk0awdLad6dYTpRISGaUkZTPvKzevk7LMf6jNoA0hJnV9LaL0juv4LtS8d5GQLrHsNvUkwHbaanthn_zoTvrM"
        }"#;
        let (mut keys, _) = exchange.accept(browser_key).unwrap();
        let browser_record = BASE64.decode("aZ_PT2m1fu_Z506EgsXw-6TTAg").unwrap();
        assert_eq!(keys.open(&browser_record).unwrap().as_slice(), b"\0{}");
    }
}
