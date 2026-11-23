PRAGMA application_id=0x4d494b43;
PRAGMA user_version=1;

CREATE TABLE accounts (
    id TEXT PRIMARY KEY CHECK(length(id)=36),
    display_name TEXT CHECK(length(trim(display_name))>0)
) STRICT, WITHOUT ROWID;

CREATE TABLE bindings (
    id TEXT PRIMARY KEY CHECK(length(id)=36),
    account_id TEXT NOT NULL REFERENCES accounts(id),
    platform TEXT NOT NULL,
    issuer TEXT NOT NULL,
    scope TEXT NOT NULL,
    subject TEXT NOT NULL,
    label TEXT NOT NULL,
    scope_label TEXT NOT NULL,
    linked_at INTEGER NOT NULL,
    UNIQUE(platform,issuer,scope,subject),
    UNIQUE(account_id,platform,issuer,scope)
) STRICT, WITHOUT ROWID;

CREATE INDEX binding_account_page ON bindings(account_id,id);

CREATE TABLE link_challenges (
    digest BLOB PRIMARY KEY CHECK(length(digest)=32),
    account_id TEXT NOT NULL REFERENCES accounts(id),
    platform TEXT NOT NULL,
    expires_at INTEGER NOT NULL CHECK(expires_at>=0),
    UNIQUE(account_id,platform)
) STRICT, WITHOUT ROWID;

CREATE INDEX challenge_expiry ON link_challenges(expires_at);

CREATE TABLE binding_attempts (
    principal_digest BLOB PRIMARY KEY CHECK(length(principal_digest)=32),
    failure_count INTEGER NOT NULL CHECK(failure_count BETWEEN 1 AND 5),
    window_started INTEGER NOT NULL CHECK(window_started>=0),
    locked_until INTEGER CHECK(locked_until>=window_started),
    expires_at INTEGER NOT NULL CHECK(expires_at>=window_started),
    CHECK((failure_count=5)=(locked_until IS NOT NULL))
) STRICT, WITHOUT ROWID;

CREATE INDEX binding_attempt_expiry ON binding_attempts(expires_at);
