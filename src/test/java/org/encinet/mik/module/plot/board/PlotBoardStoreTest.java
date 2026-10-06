package org.encinet.mik.module.plot.board;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlotBoardStoreTest {
    @TempDir Path directory;

    @Test
    void recipientsAndAuthorsStayMatchedByUuidAfterNamesChange() throws Exception {
        UUID author = UUID.randomUUID();
        UUID neighbor = UUID.randomUUID();
        String noticeId;
        try (PlotBoardStore board = new PlotBoardStore(directory.resolve("board.db"))) {
            board.open();
            JsonObject notice = board.submit("create", author, "OldBuilder", false,
                    noticePayload(neighbor, "OldNeighbor"));
            noticeId = notice.get("id").getAsString();
            assertFalse(board.listPublic().toString().contains(neighbor.toString()));
            assertFalse(board.getPublic(noticeId).toString().contains(author.toString()));
            assertEquals("OldNeighbor", PlotBoardNotifications.recipients(
                    board.listAlerts().get(0).getAsJsonObject()).get(neighbor));
            assertTrue(board.isAuthor(noticeId, author));
            assertFalse(board.isAuthor(noticeId, UUID.randomUUID()));

            JsonObject feedback = noticePayload(neighbor, "OldNeighbor");
            feedback.addProperty("kind", "objection");
            feedback.addProperty("relatedNoticeId", noticeId);
            JsonObject objection = board.submit("create", neighbor, "NewNeighbor", false, feedback);
            assertNotNull(board.findIdByPrefix(objection.get("id").getAsString().substring(0, 8)));
            JsonObject withdraw = new JsonObject();
            withdraw.addProperty("id", objection.get("id").getAsString());
            assertEquals("withdrawn", board.submit("withdraw", neighbor, "RenamedAgain", false,
                    withdraw).get("status").getAsString());
        }
        try (PlotBoardStore reopened = new PlotBoardStore(directory.resolve("board.db"))) {
            reopened.open();
            assertTrue(reopened.isAuthor(noticeId, author));
            JsonObject noticeAlert = null;
            for (var alert : reopened.listAlerts()) {
                if (alert.getAsJsonObject().get("id").getAsString().equals(noticeId))
                    noticeAlert = alert.getAsJsonObject();
            }
            assertNotNull(noticeAlert);
            assertEquals("OldNeighbor", PlotBoardNotifications.recipients(
                    noticeAlert).get(neighbor));
            assertFalse(reopened.getPublic(noticeId).toString().contains(neighbor.toString()));
        }
    }

    @Test
    void linkedFeedbackAndRulingNotifyTheAuthorsByUuid() throws Exception {
        UUID author = UUID.randomUUID();
        UUID objector = UUID.randomUUID();
        UUID moderator = UUID.randomUUID();
        try (PlotBoardStore board = new PlotBoardStore(directory.resolve("board.db"))) {
            board.open();
            JsonObject notice = board.submit("create", author, "Builder", false,
                    noticePayload(objector, "OldObjector"));
            JsonObject feedback = noticePayload(null, null);
            feedback.addProperty("kind", "objection");
            feedback.addProperty("relatedNoticeId", notice.get("id").getAsString());
            feedback.addProperty("world", "elsewhere");
            feedback.addProperty("x", 999);
            JsonObject objection = board.submit("create", objector, "NewObjector", false, feedback);
            assertEquals(notice.get("world"), objection.get("world"));
            assertEquals(notice.get("x"), objection.get("x"));
            UUID id = UUID.fromString(objection.get("id").getAsString());
            assertTrue(PlotBoardNotifications.recipients(alert(board, id))
                    .containsKey(author));
            JsonObject ruling = new JsonObject();
            ruling.addProperty("id", id.toString());
            ruling.addProperty("ruling", "Keep the route open.");
            assertThrows(PlotBoardStore.Failure.class,
                    () -> board.submit("rule", author, "RenamedBuilder", true, ruling));
            board.submit("rule", moderator, "Moderator", true, ruling);
            var recipients = PlotBoardNotifications.recipients(alert(board, id));
            assertTrue(recipients.containsKey(author));
            assertTrue(recipients.containsKey(objector));
            assertFalse(recipients.containsKey(moderator));
        }
    }

    @Test
    void objectionRequiresAnExistingNotice() throws Exception {
        UUID objector = UUID.randomUUID();
        JsonObject feedback = noticePayload(null, null);
        feedback.addProperty("kind", "objection");
        try (PlotBoardStore board = new PlotBoardStore(directory.resolve("board.db"))) {
            board.open();
            PlotBoardStore.Failure missing = assertThrows(PlotBoardStore.Failure.class,
                    () -> board.submit("create", objector, "Objector", false, feedback));
            assertEquals("invalid_record", missing.getMessage());
            feedback.addProperty("relatedNoticeId", UUID.randomUUID().toString());
            PlotBoardStore.Failure unknown = assertThrows(PlotBoardStore.Failure.class,
                    () -> board.submit("create", objector, "Objector", false, feedback));
            assertEquals("invalid_record", unknown.getMessage());
            assertEquals(0, board.listPublic().size());
        }
    }

    private static JsonObject alert(PlotBoardStore board, UUID id) throws Exception {
        for (var item : board.listAlerts()) {
            JsonObject record = item.getAsJsonObject();
            if (record.get("id").getAsString().equals(id.toString())) return record;
        }
        throw new AssertionError("Missing board alert for " + id);
    }

    @Test
    void everyRecipientIsKeptWhilePostingFrequencyIsLimitedPerAuthor() throws Exception {
        UUID author = UUID.randomUUID();
        JsonObject payload = noticePayload(null, null);
        JsonArray people = payload.getAsJsonArray("notifiedPlayers");
        for (int index = 0; index < 12; index++) {
            JsonObject person = new JsonObject();
            person.addProperty("uuid", UUID.randomUUID().toString());
            person.addProperty("name", "Neighbor" + index);
            people.add(person);
        }
        try (PlotBoardStore board = new PlotBoardStore(directory.resolve("board.db"))) {
            board.open();
            for (int index = 0; index < 5; index++) {
                JsonObject notice = board.submit("create", author, "Builder", false, payload);
                assertEquals(12, notice.getAsJsonArray("notifiedPlayers").size());
            }
            assertEquals(12, PlotBoardNotifications.recipients(
                    board.listAlerts().get(0).getAsJsonObject()).size());
            PlotBoardStore.Failure failure = assertThrows(PlotBoardStore.Failure.class,
                    () -> board.submit("create", author, "Builder", false, payload));
            assertEquals("rate_limited", failure.getMessage());
        }
    }

    @Test
    void automaticPlotNoticesPreserveOwnershipAndDoNotLimitFeedback() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID neighbor = UUID.randomUUID();
        UUID plot = UUID.randomUUID();
        JsonObject payload = noticePayload(neighbor, "Neighbor");
        payload.addProperty("subject", "Station");
        payload.addProperty("projectId", plot.toString());
        JsonObject details = new JsonObject();
        details.addProperty("autoEvent", "registered");
        details.addProperty("plotName", "Station");
        details.addProperty("minX", 8);
        details.addProperty("maxX", 15);
        details.addProperty("minY", 64);
        details.addProperty("maxY", 67);
        details.addProperty("minZ", -8);
        details.addProperty("maxZ", -1);
        payload.add("details", details);
        try (PlotBoardStore board = new PlotBoardStore(directory.resolve("board.db"))) {
            board.open();
            JsonObject first = null;
            for (int index = 0; index < 6; index++) {
                JsonObject saved = board.submitAutomaticNotice(owner, "Builder", payload);
                if (first == null) first = saved;
            }
            assertNotNull(first);
            String id = first.get("id").getAsString();
            assertTrue(board.isAuthor(id, owner));
            assertEquals("plot", board.getPublic(id).get("source").getAsString());
            assertEquals(plot.toString(), board.getPublic(id).get("projectId").getAsString());
            assertEquals("registered", board.getPublic(id).getAsJsonObject("details")
                    .get("autoEvent").getAsString());
            assertEquals(neighbor, PlotBoardNotifications.recipients(
                    alert(board, UUID.fromString(id))).keySet().iterator().next());
            assertFalse(board.getPublic(id).toString().contains(owner.toString()));
            assertFalse(board.getPublic(id).toString().contains(neighbor.toString()));

            JsonObject feedback = noticePayload(null, null);
            feedback.addProperty("kind", "objection");
            feedback.addProperty("relatedNoticeId", id);
            for (int index = 0; index < 5; index++)
                board.submit("create", neighbor, "Neighbor", false, feedback);
            PlotBoardStore.Failure limit = assertThrows(PlotBoardStore.Failure.class,
                    () -> board.submit("create", neighbor, "Neighbor", false, feedback));
            assertEquals("rate_limited", limit.getMessage());
        }
    }

    @Test
    void registrationNoticeKeepsCurrentDetailsAcrossReopen() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID neighbor = UUID.randomUUID();
        JsonObject notice = noticePayload(neighbor, "Neighbor");
        notice.addProperty("subject", "Station");
        JsonObject details = new JsonObject();
        details.addProperty("autoEvent", "registered");
        details.addProperty("plotName", "Station");
        details.addProperty("minX", 0);
        details.addProperty("maxX", 7);
        details.addProperty("minY", 64);
        details.addProperty("maxY", 67);
        details.addProperty("minZ", 0);
        details.addProperty("maxZ", 3);
        notice.add("details", details);
        String noticeId;
        try (PlotBoardStore board = new PlotBoardStore(directory.resolve("registration.db"))) {
            board.open();
            noticeId = board.submitAutomaticNotice(owner, "Builder", notice).get("id").getAsString();
        }
        try (PlotBoardStore reopened = new PlotBoardStore(directory.resolve("registration.db"))) {
            reopened.open();
            JsonObject record = reopened.getPublic(noticeId);
            assertEquals(details, record.getAsJsonObject("details"));
            assertTrue(reopened.isAuthor(noticeId, owner));
            assertFalse(record.toString().contains(neighbor.toString()));
        }
    }

    private static JsonObject noticePayload(UUID recipient, String name) {
        JsonObject payload = new JsonObject();
        payload.addProperty("kind", "notice");
        payload.addProperty("world", "world");
        payload.addProperty("x", 12);
        payload.addProperty("y", 64);
        payload.addProperty("z", -8);
        payload.addProperty("text", "Build a route near the station entrance.");
        com.google.gson.JsonArray people = new com.google.gson.JsonArray();
        if (recipient != null) {
            JsonObject person = new JsonObject();
            person.addProperty("uuid", recipient.toString());
            person.addProperty("name", name);
            people.add(person);
        }
        payload.add("notifiedPlayers", people);
        return payload;
    }
}
