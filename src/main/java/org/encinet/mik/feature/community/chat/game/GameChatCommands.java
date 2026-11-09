package org.encinet.mik.feature.community.chat.game;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.role.RolePermissions;
import java.util.List;
import java.util.Locale;

/** Game command syntax delegates to the same channel workflow as events and delayed sends. */
final class GameChatCommands {
    private final GameChatController game;
    private final LanguageService languageService;
    GameChatCommands(GameChatController game, LanguageService languageService) {
        this.game = game; this.languageService = languageService;
    }
    void register(LifecycleEventManager<Plugin> lifecycleManager) {
        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();
            commands.register(Commands.literal("staff")
                    .requires(source -> RolePermissions.canModerate(source.getSender()))
                    .executes(ctx -> {
                        Player player = game.requirePlayer(ctx.getSource().getSender());
                        if (player != null) {
                            game.toggleStaff(player);
                        }
                        return Command.SINGLE_SUCCESS;
                    })
                    .then(Commands.argument("message", StringArgumentType.greedyString())
                            .executes(ctx -> {
                                Player player = game.requirePlayer(ctx.getSource().getSender());
                                if (player != null) {
                                    game.sendStaffCommand(player, StringArgumentType.getString(ctx, "message"));
                                }
                                return Command.SINGLE_SUCCESS;
                            }))
                    .build(), languageService.t(Language.DEFAULT, Message.CHAT_STAFF_COMMAND_DESCRIPTION));

            commands.register(Commands.literal("public")
                    .executes(ctx -> {
                        Player player = game.requirePlayer(ctx.getSource().getSender());
                        if (player != null) {
                            game.switchPublic(player);
                        }
                        return Command.SINGLE_SUCCESS;
                    })
                    .then(Commands.argument("message", StringArgumentType.greedyString())
                            .executes(ctx -> {
                                Player player = game.requirePlayer(ctx.getSource().getSender());
                                if (player != null) {
                                    game.sendTemporaryPublicCommand(player, StringArgumentType.getString(ctx, "message"));
                                }
                                return Command.SINGLE_SUCCESS;
                            }))
                    .build(), languageService.t(Language.DEFAULT, Message.CHAT_PUBLIC_COMMAND_DESCRIPTION), List.of("global"));

            commands.register(Commands.literal("msg")
                    .executes(ctx -> {
                        Player player = game.requirePlayer(ctx.getSource().getSender());
                        if (player != null) {
                            game.switchPrivateOrShowUsage(player);
                        }
                        return Command.SINGLE_SUCCESS;
                    })
                    .then(Commands.argument("player", StringArgumentType.word())
                            .suggests((ctx, builder) -> {
                                game.onlinePeerNames(ctx.getSource().getSender()).stream()
                                        .filter(name -> name.toLowerCase(Locale.ROOT)
                                                .startsWith(builder.getRemaining().toLowerCase(Locale.ROOT)))
                                        .forEach(builder::suggest);
                                return builder.buildFuture();
                            })
                            .executes(ctx -> {
                                Player player = game.requirePlayer(ctx.getSource().getSender());
                                if (player != null) {
                                    game.switchPrivate(player, StringArgumentType.getString(ctx, "player"));
                                }
                                return Command.SINGLE_SUCCESS;
                            })
                            .then(Commands.argument("message", StringArgumentType.greedyString())
                                    .executes(ctx -> {
                                        Player player = game.requirePlayer(ctx.getSource().getSender());
                                        if (player != null) {
                                            game.sendTemporaryPrivateCommand(player,
                                                    StringArgumentType.getString(ctx, "player"),
                                                    StringArgumentType.getString(ctx, "message"));
                                        }
                                        return Command.SINGLE_SUCCESS;
                                    })))
                    .build(), languageService.t(Language.DEFAULT, Message.CHAT_MSG_COMMAND_DESCRIPTION), List.of("tell", "w", "whisper"));

            commands.register(Commands.literal("r")
                    .executes(ctx -> {
                        Player player = game.requirePlayer(ctx.getSource().getSender());
                        if (player != null) {
                            game.switchReply(player);
                        }
                        return Command.SINGLE_SUCCESS;
                    })
                    .then(Commands.argument("message", StringArgumentType.greedyString())
                            .executes(ctx -> {
                                Player player = game.requirePlayer(ctx.getSource().getSender());
                                if (player != null) {
                                    game.sendReplyCommand(player, StringArgumentType.getString(ctx, "message"));
                                }
                                return Command.SINGLE_SUCCESS;
                            }))
                    .build(), languageService.t(Language.DEFAULT, Message.CHAT_REPLY_COMMAND_DESCRIPTION), List.of("reply"));

            commands.register(Commands.literal("cancel")
                    .executes(ctx -> {
                        Player player = game.requirePlayer(ctx.getSource().getSender());
                        if (player != null) {
                            game.cancelDelayedMessages(player);
                        }
                        return Command.SINGLE_SUCCESS;
                    })
                    .build(), languageService.t(Language.DEFAULT, Message.CHAT_DELAY_CANCEL_COMMAND_DESCRIPTION), List.of("c"));

        });
    }
}
