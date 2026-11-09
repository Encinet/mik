package org.encinet.mik.shared.util;

import org.bukkit.Bukkit;
import org.bukkit.Server;

import java.util.Objects;
import java.util.UUID;

/** Main-thread account names. Permission-provider names are only a fallback, never a casing authority. */
public final class PlayerNames {
    private PlayerNames() { }

    /** Uses live names and the server's UUID profile cache without network or offline player-file reads. */
    public static String resolve(UUID id, String fallback) {
        return resolve(Bukkit.getServer(), id, fallback);
    }

    static String resolve(Server server, UUID id, String fallback) {
        Objects.requireNonNull(id);
        var online = server.getPlayer(id);
        if (online != null) return online.getName();
        // Supplying the provider's name here would make the profile appear complete and retain its wrong casing.
        var profile = server.createProfile(id);
        profile.completeFromCache();
        String name = profile.getName();
        if (id.equals(profile.getId()) && name != null && !name.isBlank()) return name;
        return fallback == null || fallback.isBlank() ? id.toString() : fallback;
    }
}
