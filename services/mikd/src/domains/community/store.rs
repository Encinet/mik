use super::{Error, identity::BindingIndex, schema, validate_target_path};
use rustix::fs::{Mode, OFlags};
use std::{
    fs::{self, File, Metadata},
    io,
    os::unix::fs::{MetadataExt, PermissionsExt},
    path::Path,
    sync::Arc,
    time::{Duration, SystemTime, UNIX_EPOCH},
};
use tokio::sync::{OwnedRwLockReadGuard, OwnedSemaphorePermit, RwLock, Semaphore};
use tokio_rusqlite::{
    Connection as AsyncConnection,
    rusqlite::{Connection, OpenFlags, TransactionBehavior, config::DbConfig},
};

pub struct CommunityStore {
    pub(super) binding_index: Arc<BindingIndex>,
    connection: AsyncConnection,
    ownership: Arc<File>,
    gate: Arc<RwLock<()>>,
    requests: Arc<Semaphore>,
}

struct Work {
    _permit: OwnedSemaphorePermit,
    _admission: OwnedRwLockReadGuard<()>,
    _ownership: Arc<File>,
}

impl CommunityStore {
    /// Opens the current community database, creating a private empty database when needed.
    /// # Errors
    /// Refuses an unsafe path, incompatible snapshot or a competing runtime owner.
    pub async fn open(path: &Path) -> Result<Self, Error> {
        let path = path.to_owned();
        let ownership = Arc::new(
            tokio::task::spawn_blocking({
                let path = path.clone();
                move || lock_path(&path)
            })
            .await
            .map_err(|_| Error::Busy)??,
        );
        let connection = AsyncConnection::open_with_flags(
            path,
            OpenFlags::SQLITE_OPEN_READ_WRITE | OpenFlags::SQLITE_OPEN_NOFOLLOW,
        )
        .await?;
        let initialization_lock = Arc::clone(&ownership);
        let binding_index = connection
            .call(move |connection| {
                let _lock = initialization_lock;
                initialize(connection)?;
                BindingIndex::load(connection)
            })
            .await
            .map_err(worker_error)?;
        Ok(Self {
            binding_index: Arc::new(binding_index),
            connection,
            ownership,
            gate: Arc::new(RwLock::new(())),
            requests: Arc::new(Semaphore::new(64)),
        })
    }

    pub(crate) fn bindings(&self) -> &BindingIndex {
        &self.binding_index
    }

    fn admit(&self) -> Result<Work, Error> {
        Ok(Work {
            _permit: Arc::clone(&self.requests)
                .try_acquire_owned()
                .map_err(|_| Error::Busy)?,
            _admission: Arc::clone(&self.gate)
                .try_read_owned()
                .map_err(|_| Error::Busy)?,
            _ownership: Arc::clone(&self.ownership),
        })
    }

    pub(super) async fn call<T: Send + 'static>(
        &self,
        f: impl FnOnce(&mut Connection) -> Result<T, Error> + Send + 'static,
    ) -> Result<T, Error> {
        let work = self.admit()?;
        self.connection
            .call(move |connection| {
                let _work = work;
                f(connection)
            })
            .await
            .map_err(worker_error)
    }

    /// Stops admission and drains SQLite work, including canceled requests.
    /// # Errors
    /// Reports a worker close failure.
    pub async fn close(&self) -> Result<(), Error> {
        self.requests.close();
        let _drained = self.gate.write().await;
        self.connection
            .clone()
            .close()
            .await
            .map_err(|error| match error {
                tokio_rusqlite::Error::Close((_, error)) => Error::Database(error),
                _ => Error::Busy,
            })
    }
}

pub(super) fn now_ms() -> Result<i64, Error> {
    i64::try_from(
        SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .map_err(|_| Error::Invalid("clock before epoch"))?
            .as_millis(),
    )
    .map_err(|_| Error::Invalid("clock exhausted"))
}

fn worker_error(error: tokio_rusqlite::Error<Error>) -> Error {
    match error {
        tokio_rusqlite::Error::Error(error) => error,
        _ => Error::Busy,
    }
}

fn private_regular_file(metadata: &Metadata) -> Result<(), Error> {
    if !metadata.is_file() || metadata.permissions().mode() & 0o077 != 0 || metadata.nlink() != 1 {
        return Err(Error::Invalid(
            "a private regular file with one link is required",
        ));
    }
    Ok(())
}

fn lock_path(path: &Path) -> Result<File, Error> {
    validate_target_path(path)?;
    let mut lock = path.as_os_str().to_owned();
    lock.push(".owner");
    let lock = File::from(
        rustix::fs::open(
            lock.as_os_str(),
            OFlags::NOFOLLOW | OFlags::RDWR | OFlags::CREATE | OFlags::CLOEXEC | OFlags::NONBLOCK,
            Mode::RUSR | Mode::WUSR,
        )
        .map_err(io::Error::from)?,
    );
    private_regular_file(&lock.metadata()?)?;
    lock.try_lock().map_err(io::Error::other)?;
    for suffix in ["-wal", "-shm", "-journal"] {
        let mut sidecar = path.as_os_str().to_owned();
        sidecar.push(suffix);
        match fs::symlink_metadata(sidecar) {
            Ok(metadata) => private_regular_file(&metadata)?,
            Err(error) if error.kind() == io::ErrorKind::NotFound => {}
            Err(error) => return Err(error.into()),
        }
    }
    match fs::symlink_metadata(path) {
        Ok(metadata) => private_regular_file(&metadata)?,
        Err(error) if error.kind() == io::ErrorKind::NotFound => {
            rustix::fs::open(
                path,
                OFlags::NOFOLLOW | OFlags::WRONLY | OFlags::CREATE | OFlags::EXCL | OFlags::CLOEXEC,
                Mode::RUSR | Mode::WUSR,
            )
            .map_err(io::Error::from)?;
        }
        Err(error) => return Err(error.into()),
    }
    Ok(lock)
}

fn initialize(connection: &mut Connection) -> Result<(), Error> {
    connection.busy_timeout(Duration::from_secs(3))?;
    connection.set_db_config(DbConfig::SQLITE_DBCONFIG_DEFENSIVE, true)?;
    connection.execute_batch(
        "PRAGMA trusted_schema=OFF; PRAGMA foreign_keys=ON; PRAGMA synchronous=FULL;
         PRAGMA secure_delete=ON; PRAGMA temp_store=MEMORY;",
    )?;
    let transaction = connection.transaction_with_behavior(TransactionBehavior::Immediate)?;
    schema::initialize_or_validate(&transaction)?;
    transaction.commit()?;
    connection.execute_batch("PRAGMA journal_mode=WAL;")?;
    Ok(())
}
