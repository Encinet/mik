package org.encinet.mik.feature.entertainment.music.entry;

import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.encinet.mik.feature.entertainment.music.application.MusicProblem;
import org.encinet.mik.feature.entertainment.music.ui.MusicWorkspace;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;

/** Commands are alternate entry points into the same workspace. */
public final class MusicCommandRegistrar {
    private final MusicWorkspace workspace;
    private final LanguageService language;
    public MusicCommandRegistrar(MusicWorkspace workspace, LanguageService language) {
        this.workspace = workspace;
        this.language = language;
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
                                            .then(Commands.literal("import")
                                                    .then(Commands.argument("reference", StringArgumentType.greedyString())
                                                            .executes(c -> player(c.getSource().getExecutor(), p ->
                                                                    workspace.importPlaylist(p, StringArgumentType.getString(c, "reference")))))))
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
