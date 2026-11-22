CREATE TABLE gov_players (
    player_id TEXT PRIMARY KEY,
    display_name TEXT NOT NULL,
    first_login_ms INTEGER,
    last_login_ms INTEGER,
    activity_through_ms INTEGER,
    member_since_ms INTEGER,
    credential_revision INTEGER,
    credential_changed_ms INTEGER,
    has_password INTEGER NOT NULL DEFAULT 0 CHECK (has_password IN (0, 1)),
    password_removal TEXT
) STRICT;

CREATE TABLE gov_password_removal_receipts (
    removal_id TEXT PRIMARY KEY
) STRICT;

CREATE TABLE gov_ban_snapshot (
    singleton INTEGER PRIMARY KEY CHECK (singleton = 1),
    observed_ms INTEGER NOT NULL
) STRICT;

CREATE TABLE gov_game_bans (
    subject TEXT PRIMARY KEY,
    expires_ms INTEGER
) STRICT, WITHOUT ROWID;

CREATE TABLE gov_activity (
    player_id TEXT NOT NULL REFERENCES gov_players(player_id),
    day INTEGER NOT NULL,
    duration_ms INTEGER NOT NULL CHECK (duration_ms BETWEEN 0 AND 86400000),
    PRIMARY KEY (player_id, day)
) STRICT, WITHOUT ROWID;

CREATE TABLE gov_promotion_pauses (
    id INTEGER PRIMARY KEY,
    player_id TEXT NOT NULL REFERENCES gov_players(player_id),
    created_ms INTEGER NOT NULL,
    ends_ms INTEGER NOT NULL CHECK (ends_ms > created_ms),
    actor TEXT NOT NULL,
    reason TEXT NOT NULL,
    appeal TEXT NOT NULL,
    lifted_ms INTEGER,
    lifted_by TEXT,
    lift_reason TEXT
) STRICT;

CREATE INDEX gov_promotion_pauses_player ON gov_promotion_pauses(player_id, ends_ms);

CREATE TABLE gov_maintainers (
    id INTEGER PRIMARY KEY,
    player_id TEXT NOT NULL REFERENCES gov_players(player_id),
    responsibility TEXT NOT NULL,
    source TEXT NOT NULL,
    started_ms INTEGER,
    ended_ms INTEGER
) STRICT;
CREATE UNIQUE INDEX gov_one_maintainer ON gov_maintainers(player_id) WHERE ended_ms IS NULL;

CREATE TABLE gov_tenures (
    id INTEGER PRIMARY KEY,
    player_id TEXT NOT NULL REFERENCES gov_players(player_id),
    started_ms INTEGER,
    source TEXT NOT NULL,
    ended_ms INTEGER,
    end_reason TEXT,
    CHECK ((ended_ms IS NULL) = (end_reason IS NULL))
) STRICT;

CREATE UNIQUE INDEX gov_one_tenure ON gov_tenures(player_id) WHERE ended_ms IS NULL;

CREATE TABLE gov_votes (
    id INTEGER PRIMARY KEY,
    player_id TEXT NOT NULL REFERENCES gov_players(player_id),
    tenure_id INTEGER REFERENCES gov_tenures(id),
    subject_name TEXT NOT NULL,
    opens_ms INTEGER NOT NULL,
    closes_ms INTEGER NOT NULL CHECK (closes_ms > opens_ms),
    quorum INTEGER NOT NULL CHECK (quorum >= 2),
    status INTEGER NOT NULL DEFAULT 1 CHECK (status BETWEEN 1 AND 4),
    finalized_ms INTEGER,
    failure INTEGER NOT NULL DEFAULT 0 CHECK (failure BETWEEN 0 AND 4),
    termination INTEGER NOT NULL DEFAULT 0 CHECK (termination BETWEEN 0 AND 4),
    announcement_delivered INTEGER NOT NULL DEFAULT 0 CHECK (announcement_delivered IN (0, 1)),
    CHECK ((status = 1) = (finalized_ms IS NULL)),
    CHECK ((status = 3) = (failure <> 0)),
    CHECK ((status = 4) = (termination <> 0))
) STRICT;

CREATE UNIQUE INDEX gov_one_open_vote ON gov_votes(player_id, (tenure_id IS NULL)) WHERE status = 1;
CREATE INDEX gov_votes_subject ON gov_votes(player_id, id DESC);
CREATE INDEX gov_votes_unannounced ON gov_votes(id) WHERE finalized_ms IS NOT NULL AND announcement_delivered = 0;

CREATE TABLE gov_electors (
    vote_id INTEGER NOT NULL REFERENCES gov_votes(id),
    player_id TEXT NOT NULL REFERENCES gov_players(player_id),
    choice INTEGER CHECK (choice BETWEEN 1 AND 3),
    PRIMARY KEY(vote_id, player_id)
) STRICT, WITHOUT ROWID;

CREATE TABLE gov_petitions (
    id INTEGER PRIMARY KEY,
    tenure_id INTEGER NOT NULL REFERENCES gov_tenures(id),
    created_ms INTEGER NOT NULL,
    expires_ms INTEGER NOT NULL CHECK (expires_ms > created_ms),
    required INTEGER NOT NULL CHECK (required BETWEEN 2 AND 3),
    vote_id INTEGER UNIQUE REFERENCES gov_votes(id)
) STRICT;

CREATE INDEX gov_petitions_tenure ON gov_petitions(tenure_id, expires_ms);

CREATE TABLE gov_sponsors (
    petition_id INTEGER NOT NULL REFERENCES gov_petitions(id),
    player_id TEXT NOT NULL REFERENCES gov_players(player_id),
    PRIMARY KEY (petition_id, player_id)
) STRICT, WITHOUT ROWID;

CREATE TABLE gov_game_grants (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    player_id TEXT NOT NULL REFERENCES gov_players(player_id),
    permission INTEGER NOT NULL CHECK (permission IN (1, 2)),
    enabled INTEGER NOT NULL CHECK (enabled IN (0, 1)),
    requested_ms INTEGER NOT NULL,
    vote_id INTEGER REFERENCES gov_votes(id),
    confirmed_ms INTEGER,
    superseded_ms INTEGER
) STRICT;

CREATE UNIQUE INDEX gov_current_grant ON gov_game_grants(player_id, permission) WHERE superseded_ms IS NULL;
CREATE INDEX gov_game_grants_pending ON gov_game_grants(enabled, id) WHERE confirmed_ms IS NULL AND superseded_ms IS NULL;

CREATE TABLE gov_schedule (
    singleton INTEGER PRIMARY KEY CHECK (singleton = 1),
    tenure_check_day INTEGER NOT NULL
) STRICT;
