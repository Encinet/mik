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
use serde_json::{Value, json};
use std::{
    fs,
    io::{self, Read, Write},
    os::unix::fs::{OpenOptionsExt, PermissionsExt},
    path::Path,
};
use zeroize::Zeroizing;

type Suite = DhP256HkdfSha256;
pub(super) type Sender = AeadCtxS<AesGcm256, HkdfSha256, Suite>;
pub(super) type Receiver = AeadCtxR<AesGcm256, HkdfSha256, Suite>;
pub(crate) struct Identity(SigningKey);

impl Identity {
    pub(crate) fn open(path: &Path) -> io::Result<Self> {
        let read = || -> io::Result<Self> {
            let file = fs::OpenOptions::new()
                .read(true)
                .custom_flags(
                    (rustix::fs::OFlags::NOFOLLOW | rustix::fs::OFlags::NONBLOCK)
                        .bits()
                        .cast_signed(),
                )
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
        let directory = path
            .parent()
            .ok_or_else(|| io::Error::other("missing signing key directory"))?;
        let mut file = tempfile::NamedTempFile::new_in(directory)?;
        file.write_all(der.as_bytes())?;
        file.as_file().sync_all()?;
        match file.persist_noclobber(path) {
            Ok(_) => {
                fs::File::open(directory)?.sync_all()?;
                Ok(Self(key))
            }
            Err(error) if error.error.kind() == io::ErrorKind::AlreadyExists => read(),
            Err(error) => Err(error.error),
        }
    }

    pub(crate) fn public_key(&self) -> String {
        BASE64.encode(self.0.verifying_key().to_sec1_point(false).as_bytes())
    }
}

fn rng() -> Result<StdRng, InvalidKey> {
    let mut seed = Zeroizing::new([0; 32]);
    getrandom::fill(&mut *seed).map_err(|_| InvalidKey)?;
    Ok(StdRng::from_seed(*seed))
}

pub(super) const TAG_SIZE: usize = 16;
pub(super) const PUBLIC_KEY_SIZE: usize = 65;

pub(super) struct Exchange {
    context: String,
    keys: (<Suite as Kem>::PrivateKey, <Suite as Kem>::PublicKey),
}

impl Exchange {
    pub(super) fn begin(
        identity: &Identity,
        origin: &str,
        id: &str,
    ) -> Result<(Self, Value), InvalidKey> {
        let keys = Suite::gen_keypair_with_rng(&mut rng()?);
        let public = BASE64.encode(keys.1.to_bytes());
        let context = format!(
            "{}\0{}\0{origin}\0{id}\0{public}",
            super::VERSION,
            super::PATH
        );
        let signature: Signature = identity.0.sign(context.as_bytes());
        let hello = json!({
            "type":"hello", "connectionId":id, "serverKey":public,
            "signature":BASE64.encode(signature.to_bytes())
        });
        Ok((Self { context, keys }, hello))
    }

    pub(super) fn accept(self, frame: &[u8]) -> Result<Established, InvalidKey> {
        let (public, rest) = frame
            .split_first_chunk::<PUBLIC_KEY_SIZE>()
            .ok_or(InvalidKey)?;
        let (enc, proof) = rest
            .split_first_chunk::<PUBLIC_KEY_SIZE>()
            .ok_or(InvalidKey)?;
        if proof.len() != TAG_SIZE + 1 {
            return Err(InvalidKey);
        }
        let peer = <Suite as Kem>::PublicKey::from_bytes(public).map_err(|_| InvalidKey)?;
        let enc = <Suite as Kem>::EncappedKey::from_bytes(enc).map_err(|_| InvalidKey)?;
        let mut incoming = setup_receiver::<AesGcm256, HkdfSha256, Suite>(
            &OpModeR::Auth(peer.clone()),
            &self.keys.0,
            &enc,
            format!("{}\0client", self.context).as_bytes(),
        )
        .map_err(|_| InvalidKey)?;
        let proof = incoming.open(proof, &[]).map_err(|_| InvalidKey)?;
        let deflate = match proof.as_slice() {
            [0] => false,
            [1] => true,
            _ => return Err(InvalidKey),
        };
        let (enc, outgoing) = setup_sender_with_rng::<AesGcm256, HkdfSha256, Suite>(
            &OpModeS::Auth(self.keys),
            &peer,
            format!("{}\0server", self.context).as_bytes(),
            &mut rng()?,
        )
        .map_err(|_| InvalidKey)?;
        Ok(Established {
            client_key: BASE64.encode(public),
            enc: enc.to_bytes().to_vec(),
            incoming,
            outgoing,
            deflate,
        })
    }
}

pub(super) struct Established {
    pub(super) client_key: String,
    pub(super) enc: Vec<u8>,
    pub(super) incoming: Receiver,
    pub(super) outgoing: Sender,
    pub(super) deflate: bool,
}

#[derive(Debug, thiserror::Error)]
#[error("invalid web chat key exchange")]
pub(super) struct InvalidKey;
