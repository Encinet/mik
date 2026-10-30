package org.encinet.mik.feature.player.security;

import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.event.EventSubscription;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.menu.FloatingMenus;
import org.encinet.mik.shared.menu.MenuPanel;
import org.encinet.mik.shared.menu.MenuScene;
import org.encinet.mik.shared.menu.ui.UiTemplates;
import org.encinet.mik.shared.role.PlayerRole;
import org.encinet.mik.shared.role.RolePermissions;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

/** Owns account credentials, sensitive forms, authentication and their entire lifetime. */
public final class PasswordModule implements Listener, PasswordAccess {
    private final JavaPlugin plugin;
    private final LanguageService language;
    private final PasswordService passwords;
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(64), runnable -> {
                Thread thread = new Thread(runnable, "mik-password"); thread.setDaemon(true); return thread;
            }, new ThreadPoolExecutor.AbortPolicy());
    private final PasswordAttempts attempts;
    private PasswordDialogs dialogs;
    private PasswordRoles roles;
    private PasswordLogin login;
    private EventSubscription<UserDataRecalculateEvent> subscription;
    private BukkitTask sweep;
    private boolean enabled;
    private Consumer<UUID> credentialsChanged = id -> { };

    public PasswordModule(JavaPlugin plugin, LanguageService language) {
        this.plugin = plugin; this.language = language;
        passwords = new PasswordService(plugin.getDataFolder().toPath().resolve("passwords.db"));
        passwords.onStorageFailure(() -> onMain(() -> {
            if (login != null) login.close();
            plugin.getServer().shutdown();
        }));
        attempts = new PasswordAttempts(() -> System.nanoTime() / 1_000_000, passwords::currentCredential);
    }

    public void enable() {
        try {
            LuckPerms luckPerms = Bukkit.getServicesManager().load(LuckPerms.class);
            if (luckPerms == null) throw new IllegalStateException("LuckPerms is required for account security");
            workers.submit(passwords::open).get(10, TimeUnit.SECONDS);
            roles = new PasswordRoles(luckPerms, this::operator);
            dialogs = new PasswordDialogs(plugin, language);
            login = new PasswordLogin(passwords, roles::role, dialogs, language, workers, attempts);
            Bukkit.getPluginManager().registerEvents(this, plugin);
            Bukkit.getPluginManager().registerEvents(login, plugin);
            subscription = luckPerms.getEventBus().subscribe(plugin, UserDataRecalculateEvent.class,
                    event -> roleChanged(event.getUser().getUniqueId()));
            sweep = Bukkit.getScheduler().runTaskTimer(plugin, () -> { dialogs.expire(); login.sweep(); }, 20, 20);
            enabled = true;
            // Reloading a credential guard must not trust already-present connections.
            for (Player player : Bukkit.getOnlinePlayers()) player.kick(component(language.language(player), Message.PASSWORD_LOGIN_EXPIRED));
        } catch (Exception | LinkageError error) {
            plugin.getServer().shutdown();
            throw new IllegalStateException("Account security could not start; server shutdown requested", error);
        }
    }

    public void retention(PasswordService.Retention retention) { passwords.retention(retention); }

    public void onCredentialsChanged(Consumer<UUID> observer) {
        credentialsChanged = java.util.Objects.requireNonNull(observer);
    }

    /** Capture on the main thread; the returned check only reads authentication state and is safe on HTTP threads. */
    public java.util.function.BooleanSupplier authentication(Player player) {
        if (login == null || !login.verified(player)) return () -> false;
        UUID id = player.getUniqueId();
        PasswordService.Session session = login.authority(player);
        PasswordCredential credential = passwords.currentCredential(id);
        return () -> passwords.authenticated(session) && passwords.currentCredential(id) == credential;
    }

    @Override public boolean hasPassword(UUID id) { return passwords.hasPassword(id); }
    @Override public <T, E extends Exception> T coordinate(Work<T, E> work) throws E { return passwords.coordinate(work); }

    @Override public void roleChanged(UUID id) {
        onMain(() -> {
            Player player = Bukkit.getPlayer(id);
            if (player != null && login != null) login.enforce(player);
        });
    }

    @EventHandler public void customClick(PlayerCustomClickEvent event) { dialogs.click(event); }

    public void registerCommands(LifecycleEventManager<Plugin> manager) {
        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> event.registrar().register(
                Commands.literal("password").executes(ctx -> openCommand(ctx.getSource().getSender(), null))
                        .then(Commands.literal("set").executes(ctx -> openCommand(ctx.getSource().getSender(), PasswordEdit.SET)))
                        .then(Commands.literal("change").executes(ctx -> openCommand(ctx.getSource().getSender(), PasswordEdit.CHANGE)))
                        .then(Commands.literal("remove").executes(ctx -> openCommand(ctx.getSource().getSender(), PasswordEdit.REMOVE)))
                        .then(Commands.literal("reset")
                                .requires(source -> source.getSender() instanceof ConsoleCommandSender || RolePermissions.canModerate(source.getSender()))
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
                                                if (player.getName() != null && player.getName().toLowerCase(java.util.Locale.ROOT)
                                                        .startsWith(builder.getRemainingLowerCase())) builder.suggest(player.getName());
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> resetCommand(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "player")))))
                        .build(), language.t(Language.DEFAULT, Message.PASSWORD_COMMAND_DESCRIPTION)));
    }

    private int openCommand(CommandSender sender, PasswordEdit operation) {
        if (!(sender instanceof Player player)) { send(sender, Message.PLAYER_ONLY); return 0; }
        if (!authorized(player)) return 0;
        if (operation == null) open(player, () -> { });
        else edit(player, operation, () -> open(player, () -> { }));
        return 1;
    }

    @Override public void open(Player player, Runnable back) {
        if (!authorized(player)) return;
        UUID id = player.getUniqueId();
        work(player, () -> passwords.retained(id), retained -> {
            if (!authorized(player)) return;
            MenuScene panel = settings(player, () -> open(player, back), retained);
            MenuScene.Builder menu = panel.toBuilder().content(UiTemplates.sheet(panel.content(),
                    UiTemplates.toolbar("navigation", UiTemplates.navigation("navigation"))));
            menu.back(component(language.language(player), Message.BACK)).region("navigation")
                    .primary((viewer, handle) -> { handle.close(); back.run(); });
            FloatingMenus.present(player, menu.build());
        });
    }

    public MenuPanel settingsPanel(Player player, Runnable redraw) {
        UUID id = player.getUniqueId();
        return new MenuPanel() {
            private boolean active = true;
            private Boolean retained;
            private boolean failed;
            { load(); }
            private void load() {
                failed = false;
                try {
                    workers.execute(() -> {
                        try {
                            boolean value = passwords.retained(id);
                            onMain(() -> { if (active) { retained = value; redraw.run(); } });
                        } catch (RuntimeException error) {
                            onMain(() -> { if (active) { failed = true; redraw.run(); } });
                        }
                    });
                } catch (java.util.concurrent.RejectedExecutionException error) { failed = true; }
            }
            @Override public MenuScene render() {
                if (retained != null) return settings(player, () -> { retained = null; load(); redraw.run(); }, retained);
                MenuScene.Builder menu = MenuScene.screen("account-security")
                        .content(UiTemplates.window("security", UiTemplates.menu(UiTemplates.title("heading"),
                                UiTemplates.information("status"), UiTemplates.actions("actions", 1))));
                menu.information("heading", component(language.language(player), Message.PASSWORD_TITLE)).region("heading");
                menu.information("state", component(language.language(player), failed ? Message.PASSWORD_STORAGE : Message.PASSWORD_CHECKING)).region("status");
                if (failed) menu.control("retry", component(language.language(player), Message.PASSWORD_RETRY)).region("actions")
                        .primary((viewer, handle) -> { load(); redraw.run(); });
                return menu.build();
            }
            @Override public void close() { active = false; dialogs.cancel(player.getConnection()); }
        };
    }

    private MenuScene settings(Player player, Runnable redraw, boolean retained) {
        return PasswordSettings.scene(language.language(player), language, hasPassword(player.getUniqueId()),
                PasswordRoles.role(player), retained,
                (viewer, handle) -> edit(viewer, hasPassword(viewer.getUniqueId()) ? PasswordEdit.CHANGE : PasswordEdit.SET, redraw),
                (viewer, handle) -> edit(viewer, PasswordEdit.REMOVE, redraw));
    }

    private void edit(Player player, PasswordEdit operation, Runnable back) {
        if (!authorized(player)) return;
        UUID id = player.getUniqueId();
        PasswordCredential expected = passwords.credential(id);
        boolean present = hasPassword(id);
        Message restriction = operation.restriction(present, PasswordRoles.role(player));
        if (restriction != null) {
            send(player, restriction); return;
        }
        // Read the governance-owned retention capability off-thread before offering removal.
        if (operation == PasswordEdit.REMOVE) {
            work(player, () -> { if (passwords.retained(id)) throw new PasswordException(PasswordException.Code.REQUIRED_APPLICATION); return true; },
                    ignored -> form(player, operation, expected, back));
        } else form(player, operation, expected, back);
    }

    private void form(Player player, PasswordEdit operation, PasswordCredential expected, Runnable back) {
        if (!authorized(player)) return;
        if (passwords.currentCredential(player.getUniqueId()) != expected) {
            send(player, Message.PASSWORD_CONFLICT); return;
        }
        Language locale = language.language(player);
        var inputs = new java.util.ArrayList<DialogInput>();
        if (operation != PasswordEdit.SET) inputs.add(input(locale, "current", Message.PASSWORD_CURRENT));
        if (operation != PasswordEdit.REMOVE) {
            inputs.add(input(locale, "new", Message.PASSWORD_NEW));
            inputs.add(input(locale, "confirm", Message.PASSWORD_CONFIRM));
        }
        dialogs.show(player.getConnection(), locale, operation.title, component(locale, operation.instructions), inputs, operation.title,
                response -> {
                    if (!authorized(player)) return;
                    UUID id = player.getUniqueId();
                    String name = player.getName();
                    String source = player.getAddress() == null ? "local" : player.getAddress().getAddress().getHostAddress();
                    PasswordService.Session continuation = login.authority(player);
                    if (continuation == null) return;
                    mutate(player, expected, source, () -> switch (operation) {
                        case SET -> passwords.set(id, name, expected, response.getText("new"), response.getText("confirm"), () -> roles.role(id), continuation);
                        case CHANGE -> passwords.change(id, name, expected, response.getText("current"), response.getText("new"), response.getText("confirm"), continuation);
                        case REMOVE -> passwords.remove(id, expected, response.getText("current"), () -> roles.role(id), continuation);
                    }, ignored -> {
                        send(player, operation.completed);
                        back.run();
                    });
                }, back);
    }

    private int resetCommand(CommandSender sender, String name) {
        if (!(sender instanceof ConsoleCommandSender) && !(sender instanceof Player player && authorized(player) && RolePermissions.canModerate(player))) {
            send(sender, Message.PASSWORD_DENIED); return 0;
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        if (cached != null) {
            resetKnown(sender, new PasswordRoles.Target(cached.getUniqueId(), cached.getName() == null ? name : cached.getName()));
        } else work(sender, () -> roles.lookup(name), target -> {
            if (target == null) send(sender, Message.GOVERNANCE_PLAYER_NEVER_JOINED, name);
            else resetKnown(sender, target);
        });
        return 1;
    }

    private void resetKnown(CommandSender sender, PasswordRoles.Target target) {
        if (sender instanceof Player player) { reset(player, target.id()); return; }
        if (!(sender instanceof ConsoleCommandSender)) { send(sender, Message.PASSWORD_DENIED); return; }
        PasswordCredential expected = passwords.credential(target.id());
        mutate(sender, null, "console",
                () -> passwords.reset(target.id(), expected, null, null, () -> PlayerRole.CUSTODIAN, () -> roles.role(target.id())),
                password -> send(sender, Message.PASSWORD_RESET_CONSOLE, target.name(), password));
    }

    private record Management(PlayerRole actorRole, PlayerRole targetRole, boolean present) { }

    @Override public void manage(Player actor, UUID target, Runnable back) {
        if (!authorized(actor) || !RolePermissions.canModerate(actor)) { send(actor, Message.PASSWORD_DENIED); return; }
        UUID actorId = actor.getUniqueId();
        String targetName = Bukkit.getOfflinePlayer(target).getName();
        work(actor, () -> new Management(roles.role(actorId), roles.role(target), hasPassword(target)), state -> {
            if (!authorized(actor)) return;
            Language locale = language.language(actor);
            MenuScene.Builder menu = MenuScene.screen("account-security-player", component(locale, Message.PASSWORD_TITLE))
                    .withoutTitle().stableAnchor().content(UiTemplates.sheet(
                            UiTemplates.window("security", UiTemplates.menu(UiTemplates.title("heading"),
                                    UiTemplates.information("status"), UiTemplates.actions("actions", 1))),
                            UiTemplates.toolbar("navigation", UiTemplates.navigation("navigation"))));
            menu.information("heading", component(locale, Message.PASSWORD_TITLE)).region("heading");
            menu.information("target", Component.text(targetName == null ? target.toString() : targetName, NamedTextColor.AQUA)).region("status");
            menu.information("role", component(locale, state.targetRole().label())).region("status");
            menu.information("state", component(locale, state.present() ? Message.PASSWORD_ENABLED : Message.PASSWORD_DISABLED)).region("status");
            var reset = menu.control("reset", component(locale, Message.PASSWORD_RESET)).region("actions")
                    .primary((viewer, handle) -> reset(viewer, target));
            if (!PasswordPolicy.canReset(state.actorRole(), state.targetRole(), state.present(), actorId.equals(target))) {
                reset.disabled(component(locale, Message.PASSWORD_DENIED));
            }
            menu.back(component(locale, Message.BACK)).region("navigation").primary((viewer, handle) -> { handle.close(); back.run(); });
            FloatingMenus.present(actor, menu.build());
        });
    }

    @Override public void reset(Player actor, UUID target) {
        if (!authorized(actor) || !RolePermissions.canModerate(actor)) { send(actor, Message.PASSWORD_DENIED); return; }
        UUID actorId = actor.getUniqueId();
        PasswordCredential expected = passwords.credential(target);
        PasswordCredential actorCredential = passwords.credential(actorId);
        String name = Bukkit.getOfflinePlayer(target).getName();
        String targetName = name == null ? target.toString() : name;
        work(actor, () -> {
            if (!PasswordPolicy.canReset(roles.role(actorId), roles.role(target), hasPassword(target), actorId.equals(target))) {
                throw new PasswordException(PasswordException.Code.DENIED);
            }
            return true;
        }, ignored -> {
            if (!authorized(actor)) return;
            Language locale = language.language(actor);
            dialogs.show(actor.getConnection(), locale, Message.PASSWORD_RESET,
                    component(locale, Message.PASSWORD_RESET_QUESTION, targetName),
                    List.of(input(locale, "current", Message.PASSWORD_CURRENT)), Message.PASSWORD_RESET,
                    response -> {
                        if (!authorized(actor)) return;
                        String source = actor.getAddress() == null ? "local" : actor.getAddress().getAddress().getHostAddress();
                        mutate(actor, actorCredential, source, () -> {
                            if (passwords.verify(actorId, response.getText("current")) != actorCredential) throw new PasswordException(PasswordException.Code.CONFLICT);
                            return passwords.reset(target, expected, actorId, actorCredential, () -> {
                                if (!login.currentSession(actorId, actor)) throw new PasswordException(PasswordException.Code.SESSION_EXPIRED);
                                return roles.role(actorId);
                            }, () -> roles.role(target));
                        }, password -> {
                            dialogs.show(actor.getConnection(), locale, Message.PASSWORD_RESET,
                                    component(locale, Message.PASSWORD_RESET_DONE, targetName),
                                    List.of(DialogInput.text("generated", component(locale, Message.PASSWORD_NEW)).initial(password).maxLength(24).build()),
                                    Message.CLOSE, result -> { }, () -> { });
                        });
                    }, () -> { });
        });
    }

    private DialogInput input(Language locale, String key, Message label) {
        return DialogInput.text(key, component(locale, label)).maxLength(256).build();
    }

    private boolean authorized(Player player) {
        if (player.isOnline() && login != null && login.verified(player)) return true;
        if (player.isOnline()) player.kick(component(language.language(player), Message.PASSWORD_LOGIN_EXPIRED));
        return false;
    }

    /** Every mutation applies authentication changes before running any user-interface continuation. */
    private <T> void mutate(CommandSender sender, PasswordCredential actorCredential, String source,
                            Supplier<PasswordService.Change<T>> operation, Consumer<T> view) {
        Consumer<PasswordService.Change<T>> committed = change -> {
            login.credentialsChanged(change);
            credentialsChanged.accept(change.account());
            if (passwords.currentCredential(change.account()) != change.credential()) {
                send(sender, Message.PASSWORD_CONFLICT); return;
            }
            if (sender instanceof Player player && !authorized(player)) return;
            view.accept(change.value());
        };
        if (sender instanceof Player player) {
            verifiedWork(player, player.getUniqueId(), actorCredential, source, operation, committed);
        } else work(sender, operation, committed);
    }

    private <T> void verifiedWork(Player sender, UUID id, PasswordCredential credential, String source,
                                  Supplier<T> operation, Consumer<T> done) {
        try {
            attempts.submit(workers, id, credential, source, operation).whenComplete((value, error) -> {
                if (error != null) reportFailure(sender, error);
                else onMain(() -> done.accept(value));
            });
        } catch (RuntimeException error) { reportFailure(sender, error); }
    }

    private <T> void work(CommandSender sender, Supplier<T> operation, Consumer<T> done) {
        try {
            workers.execute(() -> {
                try {
                    T result = operation.get();
                    // Committed credential changes must invalidate sessions even if the actor left.
                    onMain(() -> done.accept(result));
                } catch (RuntimeException error) { reportFailure(sender, error); }
            });
        } catch (java.util.concurrent.RejectedExecutionException error) { send(sender, Message.PASSWORD_BUSY); }
    }

    private void reportFailure(CommandSender sender, Throwable error) {
        while (error instanceof java.util.concurrent.CompletionException && error.getCause() != null) error = error.getCause();
        Message message = error instanceof PasswordException failure ? PasswordFeedback.failure(failure.code()) : Message.PASSWORD_STORAGE;
        if (message == Message.PASSWORD_STORAGE) plugin.getLogger().log(Level.SEVERE, "Account security operation failed", error);
        if (error instanceof PasswordException failure && failure.code() == PasswordException.Code.COOLDOWN) {
            long remaining = failure.retryAfterSeconds();
            onMain(() -> send(sender, message, remaining));
        } else onMain(() -> send(sender, message));
    }

    private void send(CommandSender sender, Message message, Object... args) {
        if (sender instanceof Player player && !player.isOnline()) return;
        sender.sendMessage(component(sender instanceof Player player ? language.language(player) : Language.DEFAULT, message, args));
    }

    private Component component(Language locale, Message message, Object... args) {
        if (message == Message.PASSWORD_RULES) {
            return Component.text(language.t(locale, Message.PASSWORD_RULES,
                    PasswordPolicy.MIN_LENGTH, PasswordPolicy.MAX_LENGTH), NamedTextColor.YELLOW);
        }
        return Component.text(language.t(locale, message, args), NamedTextColor.YELLOW);
    }

    private boolean operator(UUID id) {
        if (Bukkit.isPrimaryThread()) return Bukkit.getOfflinePlayer(id).isOp();
        var result = new java.util.concurrent.CompletableFuture<Boolean>();
        onMain(() -> result.complete(Bukkit.getOfflinePlayer(id).isOp()));
        try { return result.get(5, TimeUnit.SECONDS); }
        catch (Exception error) { throw new PasswordException(PasswordException.Code.STORAGE, error); }
    }

    private void onMain(Runnable action) {
        if (!plugin.isEnabled()) return;
        if (Bukkit.isPrimaryThread()) action.run();
        else Bukkit.getScheduler().runTask(plugin, action);
    }

    public void disable() {
        boolean stopServer = enabled && !plugin.getServer().isStopping();
        enabled = false;
        if (sweep != null) sweep.cancel();
        if (subscription != null) subscription.close();
        if (login != null) {
            HandlerList.unregisterAll(login);
            Component reason = plugin.getServer().shutdownMessage();
            login.close(reason != null ? reason : Component.translatable("multiplayer.disconnect.server_shutdown"));
        }
        if (dialogs != null) dialogs.close();
        attempts.close();
        credentialsChanged = id -> { };
        HandlerList.unregisterAll(this);
        workers.shutdownNow();
        try {
            if (!workers.awaitTermination(5, TimeUnit.SECONDS)) throw new IllegalStateException("Password workers did not stop");
        } catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
        finally { passwords.close(); if (stopServer) plugin.getServer().shutdown(); }
    }
}
