package org.encinet.mik.feature.community.social.platform.qq;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/** QQ owns its observed group membership; bindings remain exclusively in the identity database. */
final class QqMembershipStore {
    private static final Gson GSON = new Gson();
    private final Path file;

    QqMembershipStore(Path file) { this.file = file.toAbsolutePath(); }

    Map<String, Map<String, Boolean>> load(String appId) throws IOException {
        if (!Files.exists(file)) return Map.of();
        try {
            var root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            var version = root.get("version");
            var issuer = root.get("app_id");
            if (version == null || !version.isJsonPrimitive() || !version.getAsJsonPrimitive().isNumber()
                    || !"1".equals(version.getAsString())
                    || issuer == null || !issuer.isJsonPrimitive() || !issuer.getAsJsonPrimitive().isString()
                    || issuer.getAsString().isBlank() || !root.has("groups") || !root.get("groups").isJsonObject()) {
                throw new IllegalArgumentException("Invalid QQ membership store");
            }
            if (!appId.equals(issuer.getAsString())) return Map.of();
            Map<String, Map<String, Boolean>> groups = new LinkedHashMap<>();
            for (var group : root.getAsJsonObject("groups").entrySet()) {
                if (group.getKey().isBlank() || group.getKey().codePoints().anyMatch(Character::isISOControl)
                        || !group.getValue().isJsonObject()) throw new IllegalArgumentException("Invalid QQ membership group");
                Map<String, Boolean> members = new LinkedHashMap<>();
                for (var member : group.getValue().getAsJsonObject().entrySet()) {
                    if (!member.getValue().isJsonPrimitive() || !member.getValue().getAsJsonPrimitive().isBoolean()) {
                        throw new IllegalArgumentException("Invalid QQ membership state");
                    }
                    if (QqAccountIdentity.from(appId, member.getKey(), "").isEmpty()) {
                        throw new IllegalArgumentException("Invalid QQ membership identity");
                    }
                    members.put(member.getKey(), member.getValue().getAsBoolean());
                }
                groups.put(group.getKey(), Map.copyOf(members));
            }
            return Map.copyOf(groups);
        } catch (RuntimeException error) {
            throw new IOException("Invalid QQ membership store " + file, error);
        }
    }

    void save(String appId, Map<String, Map<String, Boolean>> groups) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        root.addProperty("app_id", appId);
        root.add("groups", GSON.toJsonTree(groups));
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "qq-memberships-", ".tmp");
        try {
            Files.writeString(temporary, GSON.toJson(root), StandardCharsets.UTF_8);
            try { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
