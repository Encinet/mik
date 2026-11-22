use super::{BindingChange, Error, store::now_ms};
use tokio_rusqlite::rusqlite::{self, Connection, Row, params};
use uuid::Uuid;

const FIELDS: &str = "id,operation_id,account_id,kind,binding_id,binding_platform";

pub(super) fn check_operation(db: &Connection, id: Uuid) -> Result<(), Error> {
    if id.is_nil() {
        return Err(Error::Invalid("community operation ID required"));
    }
    if db
        .prepare_cached("SELECT 1 FROM community_changes WHERE operation_id=?1")?
        .exists([id.to_string()])?
    {
        return Err(Error::Committed);
    }
    Ok(())
}

pub(super) fn record(
    db: &Connection,
    operation_id: Uuid,
    account: Uuid,
    change: BindingChange,
    binding: Uuid,
    platform: &str,
    now: i64,
) -> Result<i64, Error> {
    Ok(db.query_row(
        "INSERT INTO community_changes(operation_id,account_id,kind,occurred_at,binding_id,binding_platform)
         VALUES (?1,?2,?3,?4,?5,?6) RETURNING id",
        params![operation_id.to_string(), account.to_string(), change.label(), now, binding.to_string(), platform],
        |row| row.get(0),
    )?)
}

pub(super) fn pending<T>(
    db: &Connection,
    read: impl FnMut(&Row<'_>) -> rusqlite::Result<T>,
) -> Result<Vec<T>, Error> {
    Ok(db.prepare_cached(&format!(
        "SELECT {FIELDS} FROM community_changes WHERE delivered_at IS NULL ORDER BY id LIMIT 128"
    ))?.query_map([], read)?.collect::<Result<_, _>>()?)
}

pub(super) fn acknowledge(db: &Connection, id: Uuid) -> Result<(), Error> {
    db.execute(
        "UPDATE community_changes SET delivered_at=coalesce(delivered_at,?1) WHERE operation_id=?2",
        params![now_ms()?, id.to_string()],
    )?;
    Ok(())
}
