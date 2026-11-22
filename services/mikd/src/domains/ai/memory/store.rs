use super::{Change, NOTE_CHARS, NOTES_PER_PLAYER, Note, Scope};
use rustix::fs::{Mode, OFlags};
use std::{
    collections::{HashMap, HashSet},
    fs::{self, File, Metadata},
    io,
    os::unix::fs::{MetadataExt, PermissionsExt},
    path::{Path, PathBuf},
    sync::Arc,
    time::Duration,
};
use tokio_rusqlite::{
    Connection,
    rusqlite::{self, OpenFlags, TransactionBehavior, config::DbConfig, params},
};
use uuid::Uuid;

const HEADER: (i32, i32) = (0x4d49_4b4d, 1);
const MAX_DATABASE_BYTES: i64 = 32 * 1024 * 1024;
const MAX_NOTES: usize = NOTES_PER_PLAYER * 2048;

pub(super) type Notes = HashMap<Scope, Vec<Note>>;

pub(super) struct Update {
    pub(super) scope: Scope,
    pub(super) changes: Vec<Change>,
}

pub(super) struct Database {
    connection: Connection,
    owner: Arc<File>,
    total: usize,
    pub(super) generation: Uuid,
}

impl Database {
    pub(super) async fn open(path: PathBuf) -> io::Result<Self> {
        tokio::task::spawn_blocking(move || {
            let owner = Arc::new(lock_private_file(&path)?);
            let mut connection = rusqlite::Connection::open_with_flags(
                path,
                OpenFlags::SQLITE_OPEN_READ_WRITE | OpenFlags::SQLITE_OPEN_NOFOLLOW,
            )
            .map_err(io::Error::other)?;
            let total = initialize(&mut connection)?;
            Ok(Self {
                connection: Connection::from(connection),
                owner,
                total,
                generation: Uuid::new_v4(),
            })
        })
        .await
        .map_err(io::Error::other)?
    }

    pub(super) async fn notes(&self) -> io::Result<Notes> {
        self.call(|connection| all_notes(connection, now())).await
    }

    pub(super) async fn save(&mut self, updates: Vec<Update>) -> io::Result<Notes> {
        let total = self.total;
        let (total, changed) = self
            .call(move |connection| {
                let now = now();
                let written = write(connection, updates, total, now)?;
                let changed = written
                    .scopes
                    .into_iter()
                    .map(|scope| {
                        let notes = select(connection, &scope, now)?;
                        Ok((scope, notes))
                    })
                    .collect::<rusqlite::Result<_>>()?;
                Ok((written.total, changed))
            })
            .await?;
        self.total = total;
        Ok(changed)
    }

    async fn call<T: Send + 'static>(
        &self,
        work: impl FnOnce(&mut rusqlite::Connection) -> rusqlite::Result<T> + Send + 'static,
    ) -> io::Result<T> {
        let owner = Arc::clone(&self.owner);
        self.connection
            .call(move |connection| {
                let _owner = owner;
                work(connection)
            })
            .await
            .map_err(io::Error::other)
    }

    pub(super) async fn close(self) {
        if self.connection.close().await.is_err() {
            eprintln!("mikd AI player memory database could not close");
        }
    }
}

fn initialize(connection: &mut rusqlite::Connection) -> io::Result<usize> {
    connection
        .busy_timeout(Duration::from_millis(200))
        .map_err(io::Error::other)?;
    connection
        .set_db_config(DbConfig::SQLITE_DBCONFIG_DEFENSIVE, true)
        .map_err(io::Error::other)?;
    connection
        .execute_batch(
            "PRAGMA trusted_schema=OFF; PRAGMA secure_delete=ON; PRAGMA synchronous=FULL;",
        )
        .map_err(io::Error::other)?;
    let header = connection
        .query_row(
            "SELECT application_id, user_version FROM pragma_application_id(), pragma_user_version()",
            [],
            |row| Ok((row.get(0)?, row.get(1)?)),
        )
        .map_err(io::Error::other)?;
    let new = match header {
        HEADER => false,
        (0, 0)
            if !connection
                .query_row("SELECT EXISTS(SELECT 1 FROM sqlite_schema)", [], |row| {
                    row.get::<_, bool>(0)
                })
                .map_err(io::Error::other)? =>
        {
            true
        }
        _ => {
            return Err(io::Error::new(
                io::ErrorKind::InvalidData,
                "unexpected AI memory database",
            ));
        }
    };
    let journal: String = connection
        .pragma_update_and_check(None, "journal_mode", "DELETE", |row| row.get(0))
        .map_err(io::Error::other)?;
    if journal != "delete" {
        return Err(io::Error::other("AI memory requires a rollback journal"));
    }
    let page_size: i64 = connection
        .pragma_query_value(None, "page_size", |row| row.get(0))
        .map_err(io::Error::other)?;
    let limit = MAX_DATABASE_BYTES / page_size;
    let max_pages: i64 = connection
        .pragma_update_and_check(None, "max_page_count", limit, |row| row.get(0))
        .map_err(io::Error::other)?;
    if max_pages > limit {
        return Err(io::Error::new(
            io::ErrorKind::StorageFull,
            "AI memory database exceeds its limit",
        ));
    }
    if new {
        let transaction = connection
            .transaction_with_behavior(TransactionBehavior::Immediate)
            .map_err(io::Error::other)?;
        transaction
            .execute_batch(&format!(
                "CREATE TABLE notes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                instance TEXT NOT NULL CHECK (length(instance) BETWEEN 1 AND 64),
                player BLOB NOT NULL CHECK (length(player) = 16),
                text TEXT NOT NULL CHECK (length(text) BETWEEN 1 AND {NOTE_CHARS}),
                updated_at INTEGER NOT NULL,
                expires_at INTEGER CHECK (expires_at IS NULL OR expires_at > updated_at)
            ) STRICT;
            CREATE INDEX notes_scope ON notes (instance, player, updated_at DESC, id DESC);
            CREATE INDEX notes_expiry ON notes (expires_at) WHERE expires_at IS NOT NULL;
            CREATE INDEX notes_age ON notes (updated_at);
            PRAGMA application_id = {};
            PRAGMA user_version = {};",
                HEADER.0, HEADER.1,
            ))
            .map_err(io::Error::other)?;
        transaction.commit().map_err(io::Error::other)?;
    }
    connection
        .query_row("SELECT count(*) FROM notes", [], |row| row.get(0))
        .map_err(io::Error::other)
}

fn note(row: &rusqlite::Row<'_>) -> rusqlite::Result<Note> {
    Ok(Note {
        id: row.get("id")?,
        text: row.get("text")?,
        expires_at: row.get("expires_at")?,
    })
}

fn scope(row: &rusqlite::Row<'_>) -> rusqlite::Result<Scope> {
    Ok(Scope {
        instance: row.get("instance")?,
        player: Uuid::from_bytes(row.get("player")?),
    })
}

fn all_notes(connection: &rusqlite::Connection, now: i64) -> rusqlite::Result<Notes> {
    let mut query = connection.prepare(
        "SELECT instance, player, id, text, expires_at FROM notes
         WHERE expires_at IS NULL OR expires_at > ?1 ORDER BY updated_at DESC, id DESC LIMIT ?2",
    )?;
    let mut notes = Notes::new();
    let mut rows = query.query(params![now, MAX_NOTES])?;
    while let Some(row) = rows.next()? {
        let entries = notes.entry(scope(row)?).or_default();
        if entries.len() < NOTES_PER_PLAYER {
            entries.push(note(row)?);
        }
    }
    Ok(notes)
}

fn select(
    connection: &rusqlite::Connection,
    scope: &Scope,
    now: i64,
) -> rusqlite::Result<Vec<Note>> {
    let mut query = connection.prepare_cached(
        "SELECT id, text, expires_at FROM notes WHERE instance = ?1 AND player = ?2
           AND (expires_at IS NULL OR expires_at > ?3) ORDER BY updated_at DESC, id DESC LIMIT ?4",
    )?;
    query
        .query_map(
            params![
                scope.instance,
                scope.player.as_bytes(),
                now,
                NOTES_PER_PLAYER
            ],
            note,
        )?
        .collect()
}

struct Written {
    total: usize,
    scopes: HashSet<Scope>,
}

fn write(
    connection: &mut rusqlite::Connection,
    updates: Vec<Update>,
    total: usize,
    now: i64,
) -> rusqlite::Result<Written> {
    let transaction = connection.transaction_with_behavior(TransactionBehavior::Immediate)?;
    let expired = transaction
        .prepare_cached("DELETE FROM notes WHERE expires_at <= ?1")?
        .execute([now])?;
    let mut total = total
        .checked_sub(expired)
        .ok_or(rusqlite::Error::InvalidQuery)?;
    let mut scopes = HashSet::new();
    for Update { scope, changes } in updates {
        let mut added = false;
        for change in changes {
            let instance = &scope.instance;
            let player = scope.player.as_bytes();
            match change {
                Change::Add { text, ttl_days } => {
                    total += transaction.prepare_cached(
                        "INSERT INTO notes (instance, player, text, updated_at, expires_at) VALUES (?1, ?2, ?3, ?4, ?5)",
                    )?.execute(params![instance, player, text, now, expires_at(now, ttl_days)])?;
                    added = true;
                }
                Change::Replace { id, text, ttl_days } => {
                    transaction
                        .prepare_cached(
                            "UPDATE notes SET text = ?1, updated_at = ?2, expires_at = ?3
                         WHERE id = ?4 AND instance = ?5 AND player = ?6
                           AND (text <> ?1 OR expires_at IS NOT ?3)",
                        )?
                        .execute(params![
                            text,
                            now,
                            expires_at(now, ttl_days),
                            id,
                            instance,
                            player
                        ])?;
                }
                Change::Forget { id } => {
                    let removed = transaction
                        .prepare_cached(
                            "DELETE FROM notes WHERE id = ?1 AND instance = ?2 AND player = ?3",
                        )?
                        .execute(params![id, instance, player])?;
                    total = total
                        .checked_sub(removed)
                        .ok_or(rusqlite::Error::InvalidQuery)?;
                }
            }
        }
        if added {
            let removed = transaction.prepare_cached(
                "DELETE FROM notes WHERE id IN (SELECT id FROM notes WHERE instance = ?1 AND player = ?2
                   ORDER BY updated_at DESC, id DESC LIMIT -1 OFFSET ?3)",
            )?.execute(params![scope.instance, scope.player.as_bytes(), NOTES_PER_PLAYER])?;
            total = total
                .checked_sub(removed)
                .ok_or(rusqlite::Error::InvalidQuery)?;
        }
        scopes.insert(scope);
    }
    if total > MAX_NOTES {
        let mut delete = transaction.prepare_cached(
            "DELETE FROM notes WHERE id IN (SELECT id FROM notes ORDER BY updated_at, id LIMIT ?1)
             RETURNING instance, player",
        )?;
        for evicted in delete.query_map([total - MAX_NOTES], scope)? {
            scopes.insert(evicted?);
            total = total.checked_sub(1).ok_or(rusqlite::Error::InvalidQuery)?;
        }
    }
    transaction.commit()?;
    Ok(Written { total, scopes })
}

fn expires_at(now: i64, ttl_days: u16) -> Option<i64> {
    (ttl_days != 0).then_some(now + i64::from(ttl_days) * 86_400)
}

pub(super) fn now() -> i64 {
    time::OffsetDateTime::now_utc().unix_timestamp()
}

fn lock_private_file(path: &Path) -> io::Result<File> {
    let parent = path
        .parent()
        .filter(|_| path.is_absolute() && path.file_name().is_some())
        .ok_or_else(|| {
            io::Error::new(
                io::ErrorKind::InvalidInput,
                "absolute database path required",
            )
        })?;
    let metadata = fs::symlink_metadata(parent)?;
    if !metadata.is_dir() || metadata.permissions().mode() & 0o077 != 0 {
        return Err(io::Error::new(
            io::ErrorKind::PermissionDenied,
            "a private database directory is required",
        ));
    }
    for suffix in ["", "-journal", "-wal", "-shm"] {
        let mut name = path.as_os_str().to_owned();
        name.push(suffix);
        match fs::symlink_metadata(name) {
            Ok(metadata) => check_private_file(&metadata)?,
            Err(error) if error.kind() == io::ErrorKind::NotFound => {}
            Err(error) => return Err(error),
        }
    }
    let file = File::from(
        rustix::fs::open(
            path,
            OFlags::RDWR | OFlags::NOFOLLOW | OFlags::NONBLOCK | OFlags::CLOEXEC | OFlags::CREATE,
            Mode::RUSR | Mode::WUSR,
        )
        .map_err(io::Error::from)?,
    );
    check_private_file(&file.metadata()?)?;
    file.try_lock().map_err(io::Error::from)?;
    Ok(file)
}

fn check_private_file(metadata: &Metadata) -> io::Result<()> {
    if !metadata.is_file() || metadata.permissions().mode() & 0o077 != 0 || metadata.nlink() != 1 {
        return Err(io::Error::new(
            io::ErrorKind::PermissionDenied,
            "a private regular database file with one link is required",
        ));
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    fn scope(instance: &str) -> Scope {
        Scope {
            instance: instance.into(),
            player: Uuid::from_u128(1),
        }
    }

    fn add(text: &str, ttl_days: u16) -> Change {
        Change::Add {
            text: text.into(),
            ttl_days,
        }
    }

    #[test]
    fn expiration_and_changes_do_not_rewrite_other_notes() {
        let file = tempfile::NamedTempFile::new().unwrap();
        let mut connection = rusqlite::Connection::open(file.path()).unwrap();
        let total = initialize(&mut connection).unwrap();
        let total = write(
            &mut connection,
            vec![
                Update {
                    scope: scope("paper"),
                    changes: vec![
                        add("Builds with redstone first.", 0),
                        add("Building a temporary bridge.", 1),
                    ],
                },
                Update {
                    scope: scope("other"),
                    changes: vec![add("Builds underwater.", 0)],
                },
            ],
            total,
            10,
        )
        .unwrap()
        .total;
        let upcoming = 86_410;
        let leaving = select(&connection, &scope("paper"), upcoming - 1).unwrap();
        assert_eq!(leaving[0].text, "Building a temporary bridge.");
        assert_eq!(
            select(&connection, &scope("paper"), upcoming)
                .unwrap()
                .len(),
            1
        );
        let other = select(&connection, &scope("other"), upcoming).unwrap()[0].id;
        connection
            .execute_batch(
                "CREATE TRIGGER preserve_other BEFORE DELETE ON notes WHEN old.instance = 'other'
               BEGIN SELECT RAISE(ABORT, 'unrelated row was rewritten'); END;",
            )
            .unwrap();
        let total = write(
            &mut connection,
            vec![Update {
                scope: scope("paper"),
                changes: vec![
                    Change::Replace {
                        id: leaving[0].id,
                        text: "Tries to revive an expired note.".into(),
                        ttl_days: 0,
                    },
                    Change::Replace {
                        id: leaving[1].id,
                        text: "Starts with a redstone prototype.".into(),
                        ttl_days: 0,
                    },
                    Change::Forget { id: other },
                ],
            }],
            total,
            upcoming,
        )
        .unwrap()
        .total;
        assert_eq!(total, 2);
        assert_eq!(
            connection
                .query_row("SELECT count(*) FROM notes", [], |row| row
                    .get::<_, usize>(0))
                .unwrap(),
            total
        );
        let later = upcoming * 400;
        let recalled = select(&connection, &scope("paper"), later).unwrap();
        assert_eq!(recalled.len(), 1);
        assert_eq!(recalled[0].id, leaving[1].id);
        assert_eq!(recalled[0].text, "Starts with a redstone prototype.");
        assert_eq!(
            select(&connection, &scope("other"), later).unwrap()[0].id,
            other
        );
    }

    #[test]
    fn full_player_memory_evicts_oldest_and_forget_deletes_exactly_one_note() {
        let file = tempfile::NamedTempFile::new().unwrap();
        let mut connection = rusqlite::Connection::open(file.path()).unwrap();
        let total = initialize(&mut connection).unwrap();
        let total = write(
            &mut connection,
            vec![Update {
                scope: scope("paper"),
                changes: (0..=NOTES_PER_PLAYER)
                    .map(|index| add(&format!("Project {index}"), 0))
                    .collect(),
            }],
            total,
            10,
        )
        .unwrap()
        .total;
        assert_eq!(total, NOTES_PER_PLAYER);
        let notes = select(&connection, &scope("paper"), 10).unwrap();
        assert_eq!(notes.last().unwrap().text, "Project 1");
        let total = write(
            &mut connection,
            vec![Update {
                scope: scope("paper"),
                changes: vec![Change::Forget { id: notes[0].id }],
            }],
            total,
            20,
        )
        .unwrap()
        .total;
        let after = select(&connection, &scope("paper"), 20).unwrap();
        assert_eq!(after.len(), total);
        assert_eq!(
            after.iter().map(|note| note.id).collect::<Vec<_>>(),
            notes[1..].iter().map(|note| note.id).collect::<Vec<_>>()
        );
    }
}
