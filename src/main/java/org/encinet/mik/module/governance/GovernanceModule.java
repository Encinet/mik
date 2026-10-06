package org.encinet.mik.module.governance;

import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import net.luckperms.api.LuckPerms;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.module.afk.AfkService;
import org.encinet.mik.module.ban.BanManager;
import org.encinet.mik.module.governance.delivery.DeliveryService;
import org.encinet.mik.module.governance.membership.MembershipService;
import org.encinet.mik.module.governance.persistence.sqlite.SqliteGovernanceRepository;
import org.encinet.mik.module.governance.platform.paper.GovernanceTaskExecutor;
import org.encinet.mik.module.governance.platform.paper.GovernanceText;
import org.encinet.mik.module.governance.platform.paper.dashboard.GovernanceDashboardAccess;
import org.encinet.mik.module.governance.platform.paper.command.GovernanceCommandController;
import org.encinet.mik.module.governance.platform.paper.delivery.GovernanceDeliveryCoordinator;
import org.encinet.mik.module.governance.platform.paper.membership.AutomaticPromotionCoordinator;
import org.encinet.mik.module.governance.platform.paper.membership.EffectivePlaytimeTracker;
import org.encinet.mik.module.governance.platform.paper.membership.ModeratorTenureCoordinator;
import org.encinet.mik.module.governance.platform.paper.membership.MembershipRoleSnapshot;
import org.encinet.mik.module.governance.platform.paper.role.LuckPermsGovernanceRoles;
import org.encinet.mik.module.governance.removal.RemovalService;
import org.encinet.mik.module.governance.voting.VotingService;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.util.ShutdownSequence;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/** Composition root for the governance capability slices and Paper adapters. */
public final class GovernanceModule {
    private final JavaPlugin plugin;
    private final LanguageService languages;
    private final AfkService afkService;
    private final BanManager banManager;
    private final SqliteGovernanceRepository database;
    private final MembershipRoleSnapshot membershipRoles;
    private final MembershipService membership;
    private final VotingService voting;
    private final RemovalService removal;
    private final DeliveryService delivery;
    private final ExecutorService storageExecutor;
    private GovernanceDashboardAccess dashboard;

    private GovernanceCommandController commands;
    private EffectivePlaytimeTracker playtime;
    private ModeratorTenureCoordinator moderatorTenure;

    public GovernanceModule(
            JavaPlugin plugin,
            LanguageService languages,
            AfkService afkService,
            BanManager banManager
    ) {
        this.plugin = plugin;
        this.languages = languages;
        this.afkService = afkService;
        this.banManager = banManager;
        this.database = new SqliteGovernanceRepository(
                new File(plugin.getDataFolder(), "governance.db"));
        this.membershipRoles = new MembershipRoleSnapshot(plugin);
        this.membership = new MembershipService(
                database,
                (playerId, playerName) -> banManager.active(playerId, playerName).isPresent(),
                membershipRoles);
        this.voting = new VotingService(database, membership);
        this.removal = new RemovalService(database, database, membership);
        this.delivery = new DeliveryService(database);
        this.storageExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "mik-governance-storage");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void enable() {
        LuckPerms luckPerms = requireLuckPerms();
        try {
            database.open();
        } catch (GovernanceRepositoryException error) {
            throw new IllegalStateException("Governance database could not be opened", error);
        }

        membershipRoles.start(luckPerms);
        LuckPermsGovernanceRoles roleManager = new LuckPermsGovernanceRoles(luckPerms);
        GovernanceText text = new GovernanceText(languages);
        GovernanceDeliveryCoordinator coordinator = new GovernanceDeliveryCoordinator(
                plugin, membership, voting, delivery, roleManager, banManager,
                this::submitStorage, text);
        dashboard = new GovernanceDashboardAccess(plugin, voting,
                this::submitStorage, text, coordinator);
        AutomaticPromotionCoordinator promotions = new AutomaticPromotionCoordinator(
                plugin, languages, membership, membershipRoles,
                roleManager, this::submitStorage);
        playtime = new EffectivePlaytimeTracker(
                plugin, afkService, membership, membershipRoles,
                promotions, coordinator, this::submitStorage, text);
        moderatorTenure = new ModeratorTenureCoordinator(
                plugin, roleManager, voting, coordinator, this::submitStorage, text);
        commands = new GovernanceCommandController(
                plugin, languages, membership, voting, removal, roleManager,
                coordinator, this::submitStorage);

        playtime.start();
        moderatorTenure.start();
        submitStorage(coordinator::reviewOpenVotes);
        plugin.getLogger().info(
                "GovernanceModule enabled (UTC+8 effective-play ledger active)");
    }

    public void registerCommands(LifecycleEventManager<Plugin> manager) {
        if (commands == null) {
            throw new IllegalStateException("GovernanceModule must be enabled first");
        }
        commands.registerCommands(manager);
    }

    public void openMenu(Player player) {
        if (commands == null) {
            throw new IllegalStateException("GovernanceModule must be enabled first");
        }
        commands.openMenu(player);
    }

    public GovernanceDashboardAccess dashboard() {
        if (dashboard == null) throw new IllegalStateException("Governance dashboard is not enabled");
        return dashboard;
    }

    public void disable() {
        ShutdownSequence shutdown = new ShutdownSequence();
        ModeratorTenureCoordinator activeTenure = moderatorTenure;
        moderatorTenure = null;
        if (activeTenure != null) shutdown.attempt("moderator tenure", activeTenure::stop);
        EffectivePlaytimeTracker activePlaytime = playtime;
        playtime = null;
        if (activePlaytime != null) shutdown.attempt("effective playtime", activePlaytime::stop);
        shutdown.attempt("membership roles", membershipRoles::stop);
        commands = null;
        dashboard = null;

        shutdown.attempt("governance storage", () -> {
            storageExecutor.shutdown();
            try {
                if (storageExecutor.awaitTermination(5, TimeUnit.SECONDS)) return;
                storageExecutor.shutdownNow();
                throw new IllegalStateException(
                        "Governance storage did not drain within five seconds");
            } catch (InterruptedException error) {
                storageExecutor.shutdownNow();
                Thread.currentThread().interrupt();
                throw error;
            }
        });
        shutdown.attempt("governance database", database::close);
        shutdown.finish("governance module");
    }

    private LuckPerms requireLuckPerms() {
        RegisteredServiceProvider<LuckPerms> provider =
                Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        if (provider == null) {
            throw new IllegalStateException("LuckPerms is required for governance roles");
        }
        return provider.getProvider();
    }

    private void submitStorage(GovernanceTaskExecutor.Task task) {
        storageExecutor.execute(() -> {
            try {
                task.run();
            } catch (GovernanceException error) {
                plugin.getLogger().log(Level.SEVERE,
                        "Governance storage operation failed", error);
            } catch (RuntimeException error) {
                plugin.getLogger().log(Level.SEVERE,
                        "Unexpected governance operation failure", error);
            }
        });
    }
}
