package org.encinet.mik.feature.entertainment.music.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.encinet.mik.feature.entertainment.music.application.MusicProblem;
import org.encinet.mik.feature.entertainment.music.online.LxSourceService;
import org.encinet.mik.feature.entertainment.music.ui.MusicWorkspace;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.role.RolePermissions;

/** Commands are alternate entry points into the same workspace and administration capabilities. */
public final class MusicCommandRegistrar {
    private final MusicWorkspace workspace;
    private final LanguageService language;
    private final LxSourceService sources;
    private final Supplier<CompletableFuture<MusicReloadReport>> reload;
    private final Consumer<Runnable> main;
    public MusicCommandRegistrar(MusicWorkspace workspace, LanguageService language, LxSourceService sources,
            Supplier<CompletableFuture<MusicReloadReport>> reload, Consumer<Runnable> main) {
        this.workspace = workspace;
        this.language = language;
        this.sources = sources;
        this.reload = reload;
        this.main = main;
    }
    public void register(LifecycleEventManager<Plugin> lifecycle) {
        lifecycle.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register(
                    Commands.literal("music")
                            .executes(c -> player(c.getSource().getExecutor(), workspace::open))
                            .then(Commands.literal("search").then(
                                    Commands.argument("query", StringArgumentType.greedyString())
                                            .executes(c
                                                    -> player(c.getSource().getExecutor(),
                                                            p
                                                            -> workspace.search(p,
                                                                    StringArgumentType.getString(
                                                                            c, "query"))))))
                            .then(Commands.literal("playlist")
                                            .then(Commands.argument("id", StringArgumentType.word())
                                                            .executes(c
                                                                    -> player(c.getSource().getExecutor(),
                                                                            p
                                                                            -> workspace.playlist(p,
                                                                                    playlistId(
                                                                                            StringArgumentType.getString(
                                                                                                    c,
                                                                                                    "id"))))))
                                            .then(Commands.literal("import").then(
                                                    Commands.argument("platform", StringArgumentType.word())
                                                            .suggests((_, b) -> {
                                                                List.of("wy", "tx", "kg", "kw", "mg")
                                                                        .stream()
                                                                        .filter(v
                                                                                -> v.startsWith(
                                                                                        b.getRemaining()))
                                                                        .forEach(b::suggest);
                                                                return b.buildFuture();
                                                            })
                                                            .then(Commands.argument("reference",
                                                                                  StringArgumentType
                                                                                          .greedyString())
                                                                            .executes(c
                                                                                    -> player(
                                                                                            c.getSource()
                                                                                                    .getExecutor(),
                                                                                            p
                                                                                            -> workspace.importPlaylist(
                                                                                                    p,
                                                                                                    StringArgumentType
                                                                                                            .getString(
                                                                                                                    c,
                                                                                                                    "platform"),
                                                                                                    StringArgumentType
                                                                                                            .getString(
                                                                                                                    c,
                                                                                                                    "reference"))))))))
                            .then(Commands.literal("reload")
                                            .requires(s
                                                    -> s.getSender().hasPermission(RolePermissions.CUSTODIAN))
                                            .executes(c -> {
                                                var sender = c.getSource().getSender();
                                                sender.sendMessage(language.text(sender instanceof Player p
                                                                ? language.language(p)
                                                                : Language.DEFAULT,
                                                        Message.MUSIC_RELOAD_STARTED,
                                                        net.kyori.adventure.text.format.NamedTextColor
                                                                .YELLOW));
                                                reload.get().whenComplete(
                                                        (report, error)
                                                                -> main.accept(
                                                                        ()
                                                                                -> sender.sendMessage(
                                                                                        error == null
                                                                                                ? language.text(
                                                                                                          sender instanceof Player p
                                                                                                                  ? language.language(
                                                                                                                            p)
                                                                                                                  : Language.DEFAULT,
                                                                                                          Message.MUSIC_SPACE_LABEL,
                                                                                                          net.kyori
                                                                                                                  .adventure
                                                                                                                  .text
                                                                                                                  .format
                                                                                                                  .NamedTextColor
                                                                                                                  .WHITE,
                                                                                                          report.successful()
                                                                                                                  ? "saved"
                                                                                                                  : "unavailable")
                                                                                                : language.text(
                                                                                                          sender instanceof Player p
                                                                                                                  ? language.language(
                                                                                                                            p)
                                                                                                                  : Language.DEFAULT,
                                                                                                          Message.MUSIC_RELOAD_FAILED,
                                                                                                          net.kyori
                                                                                                                  .adventure
                                                                                                                  .text
                                                                                                                  .format
                                                                                                                  .NamedTextColor
                                                                                                                  .YELLOW,
                                                                                                          error.getMessage()))));
                                                return 1;
                                            }))
                            .then(Commands.literal("sources")
                                            .requires(s
                                                    -> s.getSender().hasPermission(RolePermissions.CUSTODIAN))
                                            .executes(c -> {
                                                for (var s : sources.statuses())
                                                    c.getSource().getSender().sendMessage(s.id() + " · "
                                                            + s.name() + " · " + s.available() + " · "
                                                            + Objects.toString(s.lastError(), ""));
                                                return 1;
                                            })
                                            .then(
                                                    Commands.literal("import").then(
                                                            Commands.argument("url",
                                                                            StringArgumentType.greedyString())
                                                                    .executes(
                                                                            c -> {
                                                                                sources.importSourceAsync(
                                                                                               StringArgumentType
                                                                                                       .getString(
                                                                                                               c,
                                                                                                               "url"))
                                                                                        .whenComplete(
                                                                                                (_,
                                                                                                        error)
                                                                                                        -> main.accept(
                                                                                                                ()
                                                                                                                        -> c.getSource()
                                                                                                                                .getSender()
                                                                                                                                .sendMessage(
                                                                                                                                        language.text(
                                                                                                                                                c.getSource().getSender()
                                                                                                                                                                        instanceof Player
                                                                                                                                                                                p
                                                                                                                                                        ? language.language(
                                                                                                                                                                  p)
                                                                                                                                                        : Language.DEFAULT,
                                                                                                                                                Message.MUSIC_SPACE_LABEL,
                                                                                                                                                net.kyori
                                                                                                                                                        .adventure
                                                                                                                                                        .text
                                                                                                                                                        .format
                                                                                                                                                        .NamedTextColor
                                                                                                                                                        .WHITE,
                                                                                                                                                error == null
                                                                                                                                                        ? "saved"
                                                                                                                                                        : "unavailable"))));
                                                                                return 1;
                                                                            })))
                                            .then(Commands.literal("update").executes(_ -> {
                                                sources.refreshSubscriptionsAsync();
                                                return 1;
                                            })))
                            .build(),
                    language.t(Language.DEFAULT, Message.MUSIC_COMMAND_DESCRIPTION));
        });
    }
    private int player(org.bukkit.entity.Entity entity, Consumer<Player> action) {
        if (entity instanceof Player p)
            try {
                action.accept(p);
            } catch (Exception error) {
                workspace.error(p, error);
            }
        return 1;
    }

    static UUID playlistId(String value) {
        try { return UUID.fromString(value); }
        catch (IllegalArgumentException invalid) { throw new MusicProblem(MusicProblem.Code.INVALID); }
    }
}
