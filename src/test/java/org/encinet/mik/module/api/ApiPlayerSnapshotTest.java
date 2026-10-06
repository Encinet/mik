package org.encinet.mik.module.api;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApiPlayerSnapshotTest {
    @TempDir Path directory;

    @Test
    void publishesCompletePlayerSnapshotsAndPersistsThePeak() throws Exception {
        Files.writeString(directory.resolve("api-state.yml"), "peak-online: 3\n");
        ApiPlayerSnapshot players = new ApiPlayerSnapshot(directory,
                Logger.getLogger("ApiPlayerSnapshotTest"));
        players.load();
        Instant joinedAt = Instant.parse("2026-09-27T10:15:30Z");
        UUID first = UUID.randomUUID();
        players.bootstrap(List.of(new ApiPlayerSnapshot.Identity(first, "First")), joinedAt);
        String original = new String(players.jsonBytes(), StandardCharsets.UTF_8);
        assertEquals(1, playerCount(original));
        assertEquals(3, peak(original));
        assertEquals(joinedAt.toString(), JsonParser.parseString(original).getAsJsonObject()
                .getAsJsonArray("players").get(0).getAsJsonObject().get("joined_at").getAsString());

        players.joined(UUID.randomUUID(), "Second", joinedAt);
        players.joined(UUID.randomUUID(), "Third", joinedAt);
        players.joined(UUID.randomUUID(), "Fourth", joinedAt);
        assertEquals(4, playerCount(new String(players.jsonBytes(), StandardCharsets.UTF_8)));
        assertEquals(4, players.peakOnline());
        assertEquals(1, playerCount(original));

        players.left(first);
        assertEquals(3, players.onlineCount());
        assertEquals(4, peak(new String(players.jsonBytes(), StandardCharsets.UTF_8)));

        ApiPlayerSnapshot reloaded = new ApiPlayerSnapshot(directory,
                Logger.getLogger("ApiPlayerSnapshotTest"));
        reloaded.load();
        reloaded.bootstrap(List.of(), joinedAt);
        assertEquals(4, reloaded.peakOnline());
    }

    @Test
    void publishesValidJsonForControlCharactersWithoutExposingItsByteArray() {
        ApiPlayerSnapshot players = new ApiPlayerSnapshot(directory,
                Logger.getLogger("ApiPlayerSnapshotTest"));
        players.load();
        String name = "Name\u0001\"\\中文";
        players.bootstrap(List.of(new ApiPlayerSnapshot.Identity(UUID.randomUUID(), name)),
                Instant.parse("2026-09-27T10:15:30Z"));

        byte[] body = players.jsonBytes();
        assertEquals(name, JsonParser.parseString(new String(body, StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonArray("players").get(0)
                .getAsJsonObject().get("name").getAsString());
        body[0] = '!';
        assertEquals('{', players.jsonBytes()[0]);
    }

    @Test
    void invalidPeakFileCannotBeSilentlyOverwritten() throws Exception {
        Path stateFile = directory.resolve("api-state.yml");
        String invalid = "peak-online: [unterminated\n";
        Files.writeString(stateFile, invalid);
        ApiPlayerSnapshot players = new ApiPlayerSnapshot(directory,
                Logger.getLogger("ApiPlayerSnapshotTest"));

        assertThrows(IllegalStateException.class, players::load);
        players.clear();
        assertEquals(invalid, Files.readString(stateFile));
        assertThrows(IllegalStateException.class, () ->
                players.bootstrap(List.of(), Instant.parse("2026-09-27T10:15:30Z")));
    }

    private static int playerCount(String json) {
        return JsonParser.parseString(json).getAsJsonObject().get("online").getAsInt();
    }

    private static int peak(String json) {
        return JsonParser.parseString(json).getAsJsonObject().get("peak_online").getAsInt();
    }
}
