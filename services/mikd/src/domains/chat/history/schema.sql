CREATE TABLE chat_messages (
    cursor INTEGER PRIMARY KEY AUTOINCREMENT,
    message_id TEXT NOT NULL UNIQUE,
    source_key TEXT UNIQUE,
    channel TEXT NOT NULL,
    private_first TEXT, private_second TEXT,
    restricted INTEGER NOT NULL CHECK(restricted IN (0, 1)),
    recorded_at INTEGER NOT NULL,
    record BLOB NOT NULL, order_key TEXT NOT NULL
) STRICT;
CREATE TABLE chat_viewers (
    message_cursor INTEGER NOT NULL REFERENCES chat_messages(cursor) ON DELETE CASCADE,
    viewer TEXT NOT NULL,
    PRIMARY KEY(message_cursor, viewer)
) STRICT, WITHOUT ROWID;
CREATE TABLE chat_conversations (conversation TEXT PRIMARY KEY, order_key TEXT NOT NULL) STRICT, WITHOUT ROWID;
CREATE TABLE chat_requests (
    sender TEXT NOT NULL, request_id TEXT NOT NULL, fingerprint TEXT NOT NULL,
    message_cursor INTEGER NOT NULL REFERENCES chat_messages(cursor) ON DELETE CASCADE,
    PRIMARY KEY(sender, request_id)
) STRICT, WITHOUT ROWID;
CREATE INDEX chat_channel_cursor ON chat_messages(channel, cursor);
CREATE INDEX chat_private_first ON chat_messages(channel, private_first, cursor);
CREATE INDEX chat_private_second ON chat_messages(channel, private_second, cursor);
CREATE INDEX chat_recorded_at ON chat_messages(recorded_at);
CREATE INDEX chat_requests_message ON chat_requests(message_cursor);
CREATE TABLE chat_metadata (name TEXT PRIMARY KEY, value TEXT NOT NULL) STRICT, WITHOUT ROWID;
PRAGMA application_id=0x4d494b48;
PRAGMA user_version=1;
