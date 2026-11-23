use super::{Error, java_whitespace};
use tokio_rusqlite::rusqlite::{self, Connection, Row, params};
use uuid::Uuid;

pub(super) const APPLICATION_ID: i32 = 0x4d49_4b43; // MIKC
pub(super) const SCHEMA_VERSION: i32 = 1;
const SCHEMA: &[&str] = &[
    include_str!("schema/accounts.sql"),
    include_str!("schema/outbox.sql"),
    include_str!("schema/governance.sql"),
];

fn create_schema(connection: &Connection) -> Result<(), Error> {
    for sql in SCHEMA {
        connection.execute_batch(sql)?;
    }
    Ok(())
}

pub(super) fn initialize_or_validate(connection: &Connection) -> Result<(), Error> {
    let (application, version) = header(connection)?;
    if application == 0
        && version == 0
        && !connection
            .prepare("SELECT 1 FROM sqlite_schema LIMIT 1")?
            .exists([])?
    {
        return create_schema(connection);
    }
    validate_version(connection)?;
    validate_layout(connection)?;
    validate(connection)
}

fn header(connection: &Connection) -> Result<(i32, i32), Error> {
    Ok((
        connection.pragma_query_value(None, "application_id", |row| row.get(0))?,
        connection.pragma_query_value(None, "user_version", |row| row.get(0))?,
    ))
}

pub(super) fn validate_version(connection: &Connection) -> Result<(), Error> {
    if header(connection)? != (APPLICATION_ID, SCHEMA_VERSION) {
        return Err(Error::Invalid(
            "unsupported community database; configure an empty database path",
        ));
    }
    Ok(())
}

pub(super) fn set_account_name(connection: &Connection, id: Uuid, name: &str) -> Result<(), Error> {
    connection.execute(
        "INSERT INTO accounts(id,display_name) VALUES (?1,?2)
        ON CONFLICT(id) DO UPDATE SET display_name=excluded.display_name",
        params![id.to_string(), name],
    )?;
    Ok(())
}

pub(super) fn validate_layout(connection: &Connection) -> Result<(), Error> {
    let expected = Connection::open_in_memory()?;
    create_schema(&expected)?;
    let sql = "SELECT type,name,tbl_name,sql FROM main.sqlite_schema
               WHERE name NOT GLOB 'sqlite_*' ORDER BY type,name";
    let mut expected = expected.prepare(sql)?;
    let mut actual = connection.prepare(sql)?;
    let mut left = expected.query([])?;
    let mut right = actual.query([])?;
    loop {
        match (left.next()?, right.next()?) {
            (None, None) => return Ok(()),
            (Some(left), Some(right)) => {
                for column in 0..4 {
                    if left.get_ref(column)? != right.get_ref(column)? {
                        return Err(Error::Invalid(
                            "community schema differs from the storage contract",
                        ));
                    }
                }
            }
            _ => {
                return Err(Error::Invalid(
                    "community schema differs from the storage contract",
                ));
            }
        }
    }
}

pub(super) fn validate(connection: &Connection) -> Result<(), Error> {
    let result: String = connection.query_row("PRAGMA quick_check", [], |row| row.get(0))?;
    if result != "ok" || connection.prepare("PRAGMA foreign_key_check")?.exists([])? {
        return Err(Error::Invalid(
            "community integrity or foreign key check failed",
        ));
    }
    for sql in [
        "SELECT id FROM accounts",
        "SELECT id FROM bindings",
        "SELECT operation_id FROM community_changes",
        "SELECT binding_id FROM community_changes WHERE binding_id IS NOT NULL",
    ] {
        known_uuids(connection, sql)?;
    }
    validate_identity(connection)
}

fn validate_identity(connection: &Connection) -> Result<(), Error> {
    let mut statement = connection.prepare("SELECT platform,issuer,scope,subject FROM bindings")?;
    let mut rows = statement.query([])?;
    while let Some(row) = rows.next()? {
        platform(&row.get::<_, String>(0)?)?;
        identity_part(&row.get::<_, String>(1)?, false)?;
        identity_part(&row.get::<_, String>(2)?, true)?;
        identity_part(&row.get::<_, String>(3)?, false)?;
    }
    let mut statement = connection.prepare("SELECT DISTINCT platform FROM link_challenges")?;
    let mut rows = statement.query([])?;
    while let Some(row) = rows.next()? {
        platform(&row.get::<_, String>(0)?)?;
    }
    Ok(())
}

pub(super) fn canonical_uuid(value: &str) -> Result<(), Error> {
    if Uuid::parse_str(value).is_ok_and(|id| id.to_string() == value) {
        Ok(())
    } else {
        Err(Error::Invalid("noncanonical community UUID"))
    }
}

fn known_uuids(connection: &Connection, query: &str) -> Result<(), Error> {
    let mut statement = connection.prepare(query)?;
    let mut rows = statement.query([])?;
    while let Some(row) = rows.next()? {
        canonical_uuid(&row.get::<_, String>(0)?)?;
    }
    Ok(())
}

pub(super) fn platform(value: &str) -> Result<(), Error> {
    if value.is_empty()
        || value.len() > 32
        || !value.bytes().all(|byte| {
            byte.is_ascii_lowercase() || byte.is_ascii_digit() || b"._-".contains(&byte)
        })
    {
        return Err(Error::Invalid("noncanonical external platform"));
    }
    Ok(())
}

pub(super) fn identity_part(value: &str, optional: bool) -> Result<(), Error> {
    if value.is_empty() && optional {
        return Ok(());
    }
    let blank = value.chars().all(java_whitespace);
    if blank || value.encode_utf16().count() > 256 || value.chars().any(char::is_control) {
        return Err(Error::Invalid("invalid scoped external identity"));
    }
    Ok(())
}

pub(super) fn row_uuid(row: &Row<'_>, index: usize) -> rusqlite::Result<Uuid> {
    Uuid::parse_str(&row.get::<_, String>(index)?).map_err(|error| {
        rusqlite::Error::FromSqlConversionFailure(
            index,
            rusqlite::types::Type::Text,
            Box::new(error),
        )
    })
}
