package org.encinet.mik.feature.community.governance;

import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import net.luckperms.api.LuckPerms;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.encinet.mik.feature.community.governance.gateway.GovernanceGateway;
import org.encinet.mik.feature.community.governance.command.GovernanceCommandController;
import org.encinet.mik.feature.community.governance.delivery.GovernanceDeliveryCoordinator;
import org.encinet.mik.feature.community.governance.membership.EffectivePlaytimeTracker;
import org.encinet.mik.feature.community.governance.delivery.LuckPermsGovernanceRoles;
import org.encinet.mik.feature.player.afk.AfkService;
import org.encinet.mik.feature.player.security.PasswordAccess;
import org.encinet.mik.feature.server.ban.BanManager;
import org.encinet.mik.integration.mikd.RequestPort;
import org.encinet.mik.protocol.GovernanceCredential;
import org.encinet.mik.protocol.GovernancePasswordRemoval;
import org.encinet.mik.protocol.GovernanceResult;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.menu.MenuPanel;
import org.encinet.mik.shared.util.ShutdownSequence;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;

public final class GovernanceModule implements org.encinet.mik.feature.player.security.PasswordService.RemovalAuthority {
    private final JavaPlugin plugin;
    private final LanguageService languages;
    private final PasswordAccess passwords;
    private final Function<UUID, GovernanceCredential> credentials;
    private final GovernanceGateway governance;
    private final EffectivePlaytimeTracker facts;
    private final ExecutorService workers = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(128), Thread.ofPlatform().daemon().name("mik-governance-adapter").factory());
    private GovernanceCommandController commands;
    private GovernanceDeliveryCoordinator delivery;
    private BukkitTask polling;
    private long lastError;

    public GovernanceModule(JavaPlugin plugin, LanguageService languages, AfkService afk, BanManager bans,
                            PasswordAccess passwords, Predicate<Player> authenticated,
                            Function<UUID, GovernanceCredential> credentials, RequestPort requests) {
        this.plugin = plugin;
        this.languages = languages;
        this.passwords = passwords;
        this.credentials = credentials;
        this.governance = new GovernanceGateway(requests);
        this.facts = new EffectivePlaytimeTracker(plugin, afk, bans, authenticated, credentials, governance, this::submit);
    }

    public void enable() {
        var permissions = Objects.requireNonNull(Bukkit.getServicesManager().load(LuckPerms.class), "LuckPerms is required");
        delivery = new GovernanceDeliveryCoordinator(plugin, governance,
                new LuckPermsGovernanceRoles(permissions, passwords::roleChanged), new GovernanceText(languages));
        commands = new GovernanceCommandController(plugin, languages, governance, this::submit, passwords, credentials);
        facts.start();
        polling = Bukkit.getScheduler().runTaskTimer(plugin, delivery::poll, 5, 20);
    }

    public void accountBanChanged(UUID id) { facts.accountBanChanged(id); }

    public void registerCommands(LifecycleEventManager<Plugin> manager) { commands.registerCommands(manager); }

    public boolean requiresPassword(UUID id) throws GovernanceException {
        var profile = governance.profile(id);
        return profile != null && profile.getRequiresPassword();
    }

    public boolean canResetPassword(UUID id) throws GovernanceException {
        var profile = governance.profile(id);
        return profile != null && profile.getGovernanceAuthorized();
    }

    @Override public boolean reserve(UUID account, UUID operation, long revision) throws GovernanceException {
        return governRemoval(account, operation, revision, true, false) == GovernanceResult.Outcome.DONE;
    }

    @Override public void finish(UUID account, UUID operation, long revision, boolean present) throws GovernanceException {
        if (governRemoval(account, operation, revision, present, true) != GovernanceResult.Outcome.DONE) {
            throw new GovernanceException("Password removal receipt rejected");
        }
    }

    private GovernanceResult.Outcome governRemoval(UUID account, UUID operation, long revision, boolean present,
                                                     boolean finalize) throws GovernanceException {
        var credential = GovernanceCredential.newBuilder().setRevision(revision).setChangedMs(revision).setPresent(present);
        return governance.passwordRemoval(GovernancePasswordRemoval.newBuilder().setPlayerId(account.toString())
                .setRemovalId(operation.toString()).setCredential(credential).build(), finalize);
    }

    public void credentialChanged(UUID id) { facts.credentialChanged(id); }

    public void openMenu(Player player) { commands.openMenu(player); }

    public MenuPanel identityPanel(Player player, Runnable redraw) { return commands.identityPanel(player, redraw); }

    public MenuPanel activityPanel(Player player, Runnable redraw) { return commands.activityPanel(player, redraw); }

    public void disable() {
        var shutdown = new ShutdownSequence();
        if (polling != null) polling.cancel();
        shutdown.attempt("governance facts", facts::close);
        if (delivery != null) shutdown.attempt("governance delivery", delivery::close);
        if (commands != null) shutdown.attempt("governance menus", commands::close);
        workers.shutdownNow();
        shutdown.finish("governance module");
    }

    private void submit(GovernanceTaskExecutor.Task task) {
        workers.execute(() -> {
            try {
                task.run();
            } catch (GovernanceException | RuntimeException error) {
                long now = System.nanoTime();
                if (lastError == 0 || now - lastError >= Duration.ofMinutes(1).toNanos()) {
                    lastError = now;
                    plugin.getLogger().log(Level.WARNING, "Governance adapter operation failed", error);
                }
            }
        });
    }
}
