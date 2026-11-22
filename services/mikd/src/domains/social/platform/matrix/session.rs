use super::{super::super::config::secret, Matrix, config::MatrixRecipients, request};
use matrix_sdk::{
    Client, ClientBuildError, HttpError, SessionMeta, SessionTokens,
    authentication::matrix::MatrixSession,
    config::RequestConfig,
    encryption::{
        BackupDownloadStrategy, EncryptionSettings,
        identities::ManualVerifyError,
        recovery::RecoveryError,
        secret_storage::{ImportError, SecretStorageError},
    },
    store::RoomLoadSettings,
};
use matrix_sdk_crypto::{CollectStrategy, DecryptionSettings, TrustRequirement};
use serde::{Deserialize, Serialize};
use std::{env, io, io::Write as _, path::Path};
use url::Url;
use uuid::Uuid;
use zeroize::Zeroizing;

#[derive(Deserialize, Serialize)]
struct StoredSession {
    homeserver: Url,
    device: SessionMeta,
    tokens: Option<SessionTokens>,
}

pub(super) async fn open(config: &Matrix, state: &Path) -> io::Result<Client> {
    let passphrase = Zeroizing::new(secret("MIKD_MATRIX_STORE_PASSPHRASE")?);
    let client = open_client(config, state, &passphrase).await?;
    establish_trust(&client, config).await?;
    Ok(client)
}

async fn open_client(config: &Matrix, state: &Path, passphrase: &str) -> io::Result<Client> {
    crate::http_egress::ensure_tls_provider();
    let root = state.join("matrix");
    tokio::fs::create_dir_all(&root).await?;
    let session_file = root.join("session.json");
    let mut stored = provision_device(config, &session_file).await?;
    let build = || {
        let recipients = match config.recipient_devices {
            MatrixRecipients::Verified => CollectStrategy::OnlyTrustedDevices,
            MatrixRecipients::CrossSigned => CollectStrategy::IdentityBasedStrategy,
        };
        Client::builder()
            .homeserver_url(config.homeserver.as_str())
            .respect_login_well_known(false)
            .request_config(RequestConfig::new().disable_retry())
            .sqlite_store(&root, Some(passphrase))
            .with_room_key_recipient_strategy(recipients)
            .with_decryption_settings(DecryptionSettings {
                sender_device_trust_requirement: TrustRequirement::CrossSigned,
            })
            .with_encryption_settings(EncryptionSettings {
                auto_enable_cross_signing: false,
                auto_enable_backups: false,
                backup_download_strategy: BackupDownloadStrategy::AfterDecryptionFailure,
            })
            .build()
    };
    let client = request::retry(build, |error| match error {
        ClientBuildError::Http(error) => Some(error),
        _ => None,
    })
    .await
    .map_err(|_| io::Error::other("could not open Matrix client and crypto store"))?;
    let auth = client.matrix_auth();
    if let Some(tokens) = stored.tokens.take() {
        auth.restore_session(
            MatrixSession {
                meta: stored.device,
                tokens,
            },
            RoomLoadSettings::default(),
        )
        .await
        .map_err(|_| io::Error::other("could not restore Matrix session"))?;
    } else {
        let password = Zeroizing::new(secret("MIKD_MATRIX_PASSWORD")?);
        request::retry(
            || {
                auth.login_username(config.user.as_str(), &password)
                    .device_id(stored.device.device_id.as_str())
                    .initial_device_display_name("mikd")
                    .send()
            },
            request::sdk_http,
        )
        .await
        .map_err(|_| io::Error::other("Matrix login failed"))?;
        let session = auth
            .session()
            .filter(|session| session.meta == stored.device)
            .ok_or_else(|| io::Error::other("unexpected Matrix login identity or device"))?;
        stored.tokens = Some(session.tokens);
        save_session(&stored, &session_file)?;
    }
    Ok(client)
}

async fn provision_device(config: &Matrix, path: &Path) -> io::Result<StoredSession> {
    let bytes = match tokio::fs::read(path).await {
        Ok(bytes) => Zeroizing::new(bytes),
        Err(error) if error.kind() == io::ErrorKind::NotFound => {
            let record = StoredSession {
                homeserver: config.homeserver.clone(),
                device: SessionMeta {
                    user_id: config.user.clone(),
                    device_id: format!("MIKD-{}", Uuid::new_v4().simple()).into(),
                },
                tokens: None,
            };
            save_session(&record, path)?;
            return Ok(record);
        }
        Err(error) => return Err(error),
    };
    let stored: StoredSession = serde_json::from_slice(&bytes)
        .map_err(|_| io::Error::other("invalid or unbound Matrix session"))?;
    if stored.homeserver != config.homeserver || stored.device.user_id != config.user {
        return Err(io::Error::other(
            "Matrix session belongs to another homeserver or user",
        ));
    }
    Ok(stored)
}

fn save_session(record: &StoredSession, path: &Path) -> io::Result<()> {
    let parent = path
        .parent()
        .ok_or_else(|| io::Error::other("Matrix session path requires a directory"))?;
    let bytes = Zeroizing::new(serde_json::to_vec(record).map_err(io::Error::other)?);
    let mut file = tempfile::NamedTempFile::new_in(parent)?;
    file.write_all(&bytes)?;
    file.as_file().sync_all()?;
    file.persist(path).map_err(io::Error::other)?;
    std::fs::File::open(parent)?.sync_all()
}

async fn establish_trust(client: &Client, config: &Matrix) -> io::Result<()> {
    let encryption = client.encryption();
    if let Ok(key) = env::var("MIKD_MATRIX_RECOVERY_KEY")
        && !key.is_empty()
    {
        let key = Zeroizing::new(key);
        let recovery = encryption.recovery();
        request::retry(|| recovery.recover(&key), recovery_http)
            .await
            .map_err(|_| io::Error::other("Matrix secret storage recovery failed"))?;
    }
    for (user, expected) in &config.trusted_users {
        let identity = request::retry(|| encryption.request_user_identity(user), request::sdk_http)
            .await
            .map_err(|_| io::Error::other("could not query Matrix user identity"))?
            .ok_or_else(|| io::Error::other("Matrix user identity is unavailable"))?;
        let actual = identity
            .master_key()
            .get_first_key()
            .map(|key| key.to_base64());
        if actual.as_ref() != Some(expected) {
            return Err(io::Error::other(
                "Matrix user identity fingerprint did not match",
            ));
        }
        if !identity.is_verified() {
            request::retry(|| identity.verify(), verification_http)
                .await
                .map_err(|_| io::Error::other("could not verify Matrix user identity"))?;
        }
    }
    Ok(())
}

fn verification_http(error: &ManualVerifyError) -> Option<&HttpError> {
    match error {
        ManualVerifyError::Http(error) => Some(error),
        ManualVerifyError::Signature(_) => None,
    }
}

fn recovery_http(error: &RecoveryError) -> Option<&HttpError> {
    match error {
        RecoveryError::Sdk(error)
        | RecoveryError::SecretStorage(
            SecretStorageError::Sdk(error)
            | SecretStorageError::ImportError {
                error: ImportError::Sdk(error),
                ..
            },
        ) => request::sdk_http(error),
        RecoveryError::SecretStorage(SecretStorageError::Verification(error)) => {
            verification_http(error)
        }
        _ => None,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn reopening_the_session_preserves_the_device_and_crypto_identity() {
        let state = tempfile::tempdir().unwrap();
        let root = state.path().join("matrix");
        tokio::fs::create_dir(&root).await.unwrap();
        let path = root.join("session.json");
        let mut config: Matrix = serde_yaml_ng::from_str(
            "homeserver: https://127.0.0.1:0\nuser: '@bot:example.org'\nrooms: {}",
        )
        .unwrap();
        let mut stored = provision_device(&config, &path).await.unwrap();
        let restarted = provision_device(&config, &path).await.unwrap();
        assert_eq!(restarted.device, stored.device);
        assert!(restarted.tokens.is_none());
        stored.tokens = Some(SessionTokens {
            access_token: "fixture".into(),
            refresh_token: None,
        });
        save_session(&stored, &path).unwrap();
        let passphrase = "matrix fixture store passphrase";
        let fingerprint = {
            let client = open_client(&config, state.path(), passphrase)
                .await
                .unwrap();
            client
                .encryption()
                .wait_for_e2ee_initialization_tasks()
                .await;
            client.encryption().ed25519_key().await.unwrap()
        };
        let client = open_client(&config, state.path(), passphrase)
            .await
            .unwrap();
        let session = client.matrix_auth().session().unwrap();
        assert_eq!(session.meta, stored.device);
        assert_eq!(Some(session.tokens), stored.tokens);
        assert_eq!(
            client.encryption().ed25519_key().await.unwrap(),
            fingerprint
        );
        client
            .encryption()
            .wait_for_e2ee_initialization_tasks()
            .await;

        config.homeserver = Url::parse("https://localhost:0").unwrap();
        assert!(
            open_client(&config, state.path(), passphrase)
                .await
                .is_err()
        );
    }
}
