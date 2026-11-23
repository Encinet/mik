CREATE TABLE community_changes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    operation_id TEXT NOT NULL UNIQUE CHECK (length(operation_id)=36),
    account_id TEXT NOT NULL REFERENCES accounts(id),
    kind TEXT NOT NULL CHECK (kind IN ('LINKED','REFRESHED','UNLINKED')),
    occurred_at INTEGER NOT NULL CHECK (occurred_at>=0),
    binding_id TEXT NOT NULL CHECK (length(binding_id)=36),
    binding_platform TEXT NOT NULL,
    delivered_at INTEGER CHECK (delivered_at>=0)
) STRICT;

CREATE INDEX community_changes_pending ON community_changes(id) WHERE delivered_at IS NULL;
