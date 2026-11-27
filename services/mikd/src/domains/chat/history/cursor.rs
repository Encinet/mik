use super::HistoryError;
use aes_gcm::{
    Aes256Gcm, KeyInit,
    aead::{Aead, Nonce, Payload},
};
use base64::{Engine, engine::general_purpose::URL_SAFE_NO_PAD};
use uuid::Uuid;
use zeroize::Zeroizing;

#[derive(Clone, Copy)]
pub(super) struct Position {
    pub after: i64,
    pub head: i64,
    pub before: i64,
}

pub(super) struct Cursors {
    cipher: Aes256Gcm,
    database: Uuid,
}

impl Cursors {
    pub fn new(secret: &str, database: &str) -> Result<Self, HistoryError> {
        let key = Zeroizing::new(
            URL_SAFE_NO_PAD
                .decode(secret)
                .map_err(|_| HistoryError::Invalid("invalid cursor key"))?,
        );
        Ok(Self {
            cipher: Aes256Gcm::new_from_slice(&key)
                .map_err(|_| HistoryError::Invalid("invalid cursor key"))?,
            database: Uuid::parse_str(database)
                .map_err(|_| HistoryError::Invalid("invalid database identity"))?,
        })
    }

    pub fn seal(&self, position: Position, binding: &str) -> Result<String, HistoryError> {
        let mut nonce = [0; 12];
        getrandom::fill(&mut nonce)
            .map_err(|_| HistoryError::Invalid("cursor entropy unavailable"))?;
        let mut clear = Zeroizing::new(Vec::with_capacity(40));
        clear.extend_from_slice(self.database.as_bytes());
        for value in [position.after, position.head, position.before] {
            clear.extend_from_slice(&value.to_be_bytes());
        }
        let ciphertext = self
            .cipher
            .encrypt(
                &Nonce::<Aes256Gcm>::from(nonce),
                Payload {
                    msg: &clear,
                    aad: binding.as_bytes(),
                },
            )
            .map_err(|_| HistoryError::Invalid("cursor encryption failed"))?;
        let mut packet = nonce.to_vec();
        packet.extend(ciphertext);
        Ok(URL_SAFE_NO_PAD.encode(packet))
    }

    pub fn open(&self, token: Option<&str>, binding: &str) -> Option<Position> {
        let token = token?;
        if token.len() != 91 {
            return None;
        }
        let packet = URL_SAFE_NO_PAD.decode(token).ok()?;
        if packet.len() != 68 || URL_SAFE_NO_PAD.encode(&packet) != token {
            return None;
        }
        let nonce = Nonce::<Aes256Gcm>::try_from(&packet[..12]).ok()?;
        let clear = Zeroizing::new(
            self.cipher
                .decrypt(
                    &nonce,
                    Payload {
                        msg: &packet[12..],
                        aad: binding.as_bytes(),
                    },
                )
                .ok()?,
        );
        if clear.len() != 40 || &clear[..16] != self.database.as_bytes() {
            return None;
        }
        let position = Position {
            after: i64::from_be_bytes(clear[16..24].try_into().ok()?),
            head: i64::from_be_bytes(clear[24..32].try_into().ok()?),
            before: i64::from_be_bytes(clear[32..40].try_into().ok()?),
        };
        (position.after >= 0 && position.head >= position.after && position.before > position.after)
            .then_some(position)
    }
}
