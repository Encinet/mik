package org.encinet.mik.module.governance.persistence.sqlite;

import org.encinet.mik.module.governance.membership.participation.DailyParticipation;
import org.encinet.mik.module.governance.membership.profile.PlayerProfile;
import org.encinet.mik.module.governance.membership.promotion.PromotionPause;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** SQLite statements owned by the membership capability. */
final class SqliteMembershipStore {
    private final Connection connection;

    SqliteMembershipStore(Connection connection) {
        this.connection = connection;
    }

    void observePlayer(
            UUID playerId,
            String playerName,
            Instant firstJoinedAt,
            Instant lastLoginAt
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO governance_players(
                    player_uuid, player_name, first_joined_at, member_since,
                    last_successful_login_at
                ) VALUES (?, ?, ?, NULL, ?)
                ON CONFLICT(player_uuid) DO UPDATE SET
                    player_name = excluded.player_name,
                    first_joined_at = MIN(first_joined_at, excluded.first_joined_at),
                    last_successful_login_at = MAX(last_successful_login_at,
                                                   excluded.last_successful_login_at)
                """)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, SqliteGovernanceRows.requireText(playerName, "playerName"));
            statement.setLong(3, firstJoinedAt.toEpochMilli());
            statement.setLong(4, lastLoginAt.toEpochMilli());
            statement.executeUpdate();
        }
    }

    void recordMemberSince(UUID playerId, Instant memberSince)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE governance_players
                SET member_since = ?
                WHERE player_uuid = ? AND member_since IS NULL
                """)) {
            statement.setLong(1, memberSince.toEpochMilli());
            statement.setString(2, playerId.toString());
            if (statement.executeUpdate() == 0 && player(playerId).isEmpty()) {
                throw new SQLException("Unknown governance player " + playerId);
            }
        }
    }

    Optional<PlayerProfile> player(UUID playerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM governance_players WHERE player_uuid = ?")) {
            statement.setString(1, playerId.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(SqliteGovernanceRows.readPlayer(result)) : Optional.empty();
            }
        }
    }

    List<PlayerProfile> players() throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM governance_players ORDER BY player_name COLLATE NOCASE");
             ResultSet result = statement.executeQuery()) {
            List<PlayerProfile> players = new ArrayList<>();
            while (result.next()) players.add(SqliteGovernanceRows.readPlayer(result));
            return List.copyOf(players);
        }
    }

    void addParticipation(UUID playerId, LocalDate date, long seconds)
            throws SQLException {
        if (seconds <= 0) return;
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO governance_participation(
                    player_uuid, participation_date, eligible_seconds
                ) VALUES (?, ?, ?)
                ON CONFLICT(player_uuid, participation_date) DO UPDATE SET
                    eligible_seconds = eligible_seconds + excluded.eligible_seconds
                """)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, date.toString());
            statement.setLong(3, seconds);
            statement.executeUpdate();
        }
    }

    List<DailyParticipation> participation(UUID playerId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT participation_date, eligible_seconds
                FROM governance_participation
                WHERE player_uuid = ?
                ORDER BY participation_date
                """)) {
            statement.setString(1, playerId.toString());
            try (ResultSet result = statement.executeQuery()) {
                List<DailyParticipation> days = new ArrayList<>();
                while (result.next()) {
                    days.add(new DailyParticipation(
                            LocalDate.parse(result.getString(1)), result.getLong(2)));
                }
                return List.copyOf(days);
            }
        }
    }

    PromotionPause createPause(
            UUID playerId,
            String reason,
            Instant startsAt,
            Instant endsAt,
            String appealPath,
            String imposedBy
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO governance_promotion_pauses(
                    player_uuid, reason, starts_at, ends_at, appeal_path, imposed_by
                ) VALUES (?, ?, ?, ?, ?, ?)
                """, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, SqliteGovernanceRows.requireText(reason, "reason"));
            statement.setLong(3, startsAt.toEpochMilli());
            statement.setLong(4, endsAt.toEpochMilli());
            statement.setString(5, SqliteGovernanceRows.requireText(appealPath, "appealPath"));
            statement.setString(6, SqliteGovernanceRows.requireText(imposedBy, "imposedBy"));
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("SQLite returned no pause id");
                return new PromotionPause(keys.getLong(1), reason, startsAt, endsAt,
                        appealPath, imposedBy);
            }
        }
    }

    Optional<PromotionPause> activePause(UUID playerId, Instant now)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, reason, starts_at, ends_at, appeal_path, imposed_by
                FROM governance_promotion_pauses
                WHERE player_uuid = ? AND lifted_at IS NULL
                  AND starts_at <= ? AND ends_at > ?
                ORDER BY starts_at DESC, id DESC LIMIT 1
                """)) {
            statement.setString(1, playerId.toString());
            statement.setLong(2, now.toEpochMilli());
            statement.setLong(3, now.toEpochMilli());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(SqliteGovernanceRows.readPause(result)) : Optional.empty();
            }
        }
    }

    boolean liftActivePause(
            UUID playerId,
            Instant now,
            String liftedBy,
            String liftReason
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE governance_promotion_pauses
                SET lifted_at = ?, lifted_by = ?, lift_reason = ?
                WHERE id = (
                    SELECT id FROM governance_promotion_pauses
                    WHERE player_uuid = ? AND lifted_at IS NULL
                      AND starts_at <= ? AND ends_at > ?
                    ORDER BY starts_at DESC, id DESC LIMIT 1
                )
                """)) {
            statement.setLong(1, now.toEpochMilli());
            statement.setString(2, SqliteGovernanceRows.requireText(liftedBy, "liftedBy"));
            statement.setString(3, SqliteGovernanceRows.requireText(liftReason, "liftReason"));
            statement.setString(4, playerId.toString());
            statement.setLong(5, now.toEpochMilli());
            statement.setLong(6, now.toEpochMilli());
            return statement.executeUpdate() == 1;
        }
    }

}

