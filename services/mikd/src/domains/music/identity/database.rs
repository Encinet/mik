use std::{io, sync::Arc};
use tokio::sync::Semaphore;
use tokio_rusqlite::{Connection, rusqlite};
use tokio_util::sync::CancellationToken;

pub(super) struct Database {
    connection: Connection,
    slots: Arc<Semaphore>,
}

impl Database {
    pub fn new(connection: Connection, capacity: usize) -> Self {
        Self {
            connection,
            slots: Arc::new(Semaphore::new(capacity)),
        }
    }

    pub async fn call<T: Send + 'static>(
        &self,
        operation: impl FnOnce(&mut rusqlite::Connection, &CancellationToken) -> rusqlite::Result<T>
        + Send
        + 'static,
    ) -> io::Result<T> {
        let permit = self
            .slots
            .clone()
            .try_acquire_owned()
            .map_err(|_| io::Error::new(io::ErrorKind::WouldBlock, "music database busy"))?;
        let cancel = CancellationToken::new();
        let _cancel_on_drop = cancel.clone().drop_guard();
        self.connection
            .call(move |db| {
                let _permit = permit;
                check_cancelled(&cancel)?;
                operation(db, &cancel)
            })
            .await
            .map_err(io::Error::other)
    }

    pub async fn close(&self) -> io::Result<()> {
        self.connection
            .clone()
            .close()
            .await
            .map_err(io::Error::other)
    }
}

pub(super) fn check_cancelled(cancel: &CancellationToken) -> rusqlite::Result<()> {
    if cancel.is_cancelled() {
        return Err(rusqlite::Error::SqliteFailure(
            rusqlite::ffi::Error::new(rusqlite::ffi::SQLITE_INTERRUPT),
            None,
        ));
    }
    Ok(())
}
