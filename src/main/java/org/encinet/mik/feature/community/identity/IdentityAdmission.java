package org.encinet.mik.feature.community.identity;

import com.destroystokyo.paper.event.profile.ProfileWhitelistVerifyEvent;
import io.papermc.paper.event.connection.configuration.AsyncPlayerConnectionConfigureEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/** Binding-based admission, authenticated first-login challenges, and online revocation. */
final class IdentityAdmission implements Listener, AutoCloseable {
    private static final int MAX_CHALLENGES = 512;
    private final JavaPlugin plugin;
    private final IdentityBindingRuntime bindings;
    private final LanguageService languages;
    private final LinkedHashMap<UUID, Challenge> challenges = new LinkedHashMap<>();
    private BukkitTask heartbeat;
    private volatile boolean closed;

    IdentityAdmission(JavaPlugin plugin, IdentityBindingRuntime bindings, LanguageService languages) {
        this.plugin = plugin;
        this.bindings = bindings;
        this.languages = languages;
    }

    void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        heartbeat = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            bindings.reconcileMemberships();
            for (var player : Bukkit.getOnlinePlayers()) {
                if (bindings.mustDisconnect(player.getUniqueId())) {
                    player.kick(Component.text(languages.t(player, Message.IDENTITY_ADMISSION_REVOKED), NamedTextColor.YELLOW));
                }
            }
        }, 20L, 20L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void whitelist(ProfileWhitelistVerifyEvent event) {
        UUID id = event.getPlayerProfile().getId();
        // The connection must reach CONFIGURATION to verify its password and receive a first-binding challenge.
        // This does not admit it to the world; configure() below is the binding gate.
        event.setWhitelisted(!closed && id != null && bindings.isAvailable());
        if (!event.isWhitelisted()) event.kickMessage(unavailable());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void configure(AsyncPlayerConnectionConfigureEvent event) {
        var connection = event.getConnection();
        // NORMAL-priority account authentication finishes before this HIGHEST-priority admission check.
        if (!connection.isConnected()) return;
        Language language = Language.fromId(connection.getClientOption(com.destroystokyo.paper.ClientOption.LOCALE))
                .orElse(Language.DEFAULT);
        UUID id = connection.getProfile().getId();
        if (closed || id == null || !bindings.isAvailable()) { connection.disconnect(unavailable(language)); return; }
        if (bindings.canJoin(id)) {
            synchronized (this) { challenges.remove(id); }
            return;
        }
        try { connection.disconnect(challenge(id, connection.getProfile().getName(), language)); }
        catch (IdentityBindingException error) {
            plugin.getLogger().log(Level.WARNING, "Could not prepare identity admission", error);
            connection.disconnect(unavailable(language));
        }
    }

    private synchronized Component challenge(UUID id, String name, Language language) {
        Instant now = Instant.now();
        challenges.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expiresAt()));
        List<IdentityPlatform> platforms = bindings.platforms();
        if (platforms.isEmpty()) return unavailable(language);
        Challenge current = challenges.get(id);
        if (current == null || !current.name().equals(name) || !current.platforms().equals(platforms)
                || current.instructions().stream().anyMatch(instruction -> !bindings.codeIsPending(id, instruction.code()))) {
            List<Instruction> instructions = new ArrayList<>();
            Instant expires = now.plus(IdentityBindingService.CODE_TTL);
            for (IdentityPlatform platform : platforms) {
                var code = bindings.issueCode(id, name, platform.id()).orElseThrow();
                if (code.expiresAt().isBefore(expires)) expires = code.expiresAt();
                instructions.add(new Instruction(platform, code));
            }
            while (challenges.size() >= MAX_CHALLENGES) challenges.pollFirstEntry();
            current = new Challenge(name, platforms, List.copyOf(instructions), expires);
            challenges.put(id, current);
        }
        Component message = Component.text(languages.t(language, bindings.hasBindings(id)
                ? Message.IDENTITY_ADMISSION_PENDING : Message.IDENTITY_ADMISSION_REQUIRED), NamedTextColor.YELLOW)
                .appendNewline().append(IdentityBindingGuidance.community(languages, language))
                .appendNewline().appendNewline().append(Component.text(languages.t(language,
                        Message.IDENTITY_ADMISSION_INSTRUCTIONS), NamedTextColor.WHITE));
        for (Instruction instruction : current.instructions()) {
            message = message.appendNewline().append(IdentityBindingGuidance.instruction(languages, language,
                    Message.IDENTITY_ADMISSION_BIND_RICH, instruction.platform(), instruction.code()));
        }
        return message.appendNewline().appendNewline().append(IdentityBindingGuidance.expiry(languages, language))
                .appendNewline().append(Component.text(languages.t(language,
                        Message.IDENTITY_ADMISSION_RECONNECT), NamedTextColor.WHITE));
    }

    private Component unavailable(Language language) {
        return Component.text(languages.t(language, Message.IDENTITY_UNAVAILABLE), NamedTextColor.YELLOW);
    }

    private Component unavailable() {
        return Component.text(languages.t(Language.ZH_CN, Message.IDENTITY_UNAVAILABLE), NamedTextColor.YELLOW)
                .appendNewline().append(Component.text(languages.t(Language.EN_US, Message.IDENTITY_UNAVAILABLE), NamedTextColor.WHITE));
    }

    @Override
    public synchronized void close() {
        closed = true;
        if (heartbeat != null) heartbeat.cancel();
        HandlerList.unregisterAll(this);
        challenges.clear();
    }

    private record Instruction(IdentityPlatform platform, IdentityLinkCode code) { }
    private record Challenge(String name, List<IdentityPlatform> platforms, List<Instruction> instructions, Instant expiresAt) { }
}
