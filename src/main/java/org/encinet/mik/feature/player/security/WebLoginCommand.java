package org.encinet.mik.feature.player.security;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.integration.mikd.RequestPort;
import org.encinet.mik.protocol.WebLoginApproval;
import org.encinet.mik.protocol.WebLoginReceipt;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;

public final class WebLoginCommand {
    private final JavaPlugin plugin;
    private final LanguageService language;
    private final PasswordModule security;
    private final RequestPort daemon;

    public WebLoginCommand(JavaPlugin plugin, LanguageService language, PasswordModule security, RequestPort daemon) {
        this.plugin = plugin;
        this.language = language;
        this.security = security;
        this.daemon = daemon;
    }

    public void register() {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(Commands.literal("weblogin")
                        .then(Commands.argument("code", StringArgumentType.word()).executes(context -> {
                            confirm(context.getSource().getSender(), StringArgumentType.getString(context, "code"));
                            return Command.SINGLE_SUCCESS;
                        })).build(), language.t(Language.DEFAULT, Message.WEBLOGIN_COMMAND_DESCRIPTION)));
    }

    void confirm(CommandSender sender, String code) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(language.t(Language.DEFAULT, Message.PLAYER_ONLY), NamedTextColor.RED));
            return;
        }
        if (!player.isOnline()) return;
        var authorized = security.authentication(player);
        if (!authorized.getAsBoolean()) {
            sendResult(player, Message.PASSWORD_CONFLICT);
            return;
        }
        var playerId = player.getUniqueId();
        String loginReference = security.webLoginReference(playerId);
        if (loginReference.isEmpty()) {
            sendResult(player, Message.WEBLOGIN_UNAVAILABLE);
            return;
        }
        security.confirmWebLogin(player, () -> {
            if (!player.isOnline() || !authorized.getAsBoolean()) return;
            var approval = WebLoginApproval.newBuilder().setCode(code)
                    .setPlayerUuid(playerId.toString()).setPlayerName(player.getName())
                    .setLoginReference(loginReference).build();
            daemon.approveWebLogin(approval, authorized).whenComplete((receipt, failure) -> {
                if (!plugin.isEnabled()) return;
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (plugin.isEnabled() && player.isOnline() && authorized.getAsBoolean()) {
                        sendResult(player, resultMessage(receipt, failure));
                    }
                });
            });
        });
    }

    private static Message resultMessage(WebLoginReceipt receipt, Throwable failure) {
        if (failure != null) return RequestPort.definitelyNotSubmitted(failure)
                ? Message.WEBLOGIN_UNAVAILABLE : Message.WEBLOGIN_UNCONFIRMED;
        if (receipt == null) return Message.WEBLOGIN_UNCONFIRMED;
        return switch (receipt.getOutcome()) {
            case APPROVED -> Message.WEBLOGIN_CONFIRMED;
            case REJECTED -> Message.WEBLOGIN_REJECTED;
            case UNAVAILABLE -> Message.WEBLOGIN_UNAVAILABLE;
            case OUTCOME_UNSPECIFIED, UNRECOGNIZED -> Message.WEBLOGIN_UNCONFIRMED;
        };
    }

    private void sendResult(Player player, Message message) {
        var color = switch (message) {
            case WEBLOGIN_CONFIRMED -> NamedTextColor.GREEN;
            case WEBLOGIN_UNCONFIRMED, WEBLOGIN_UNAVAILABLE -> NamedTextColor.YELLOW;
            default -> NamedTextColor.RED;
        };
        player.sendMessage(Component.text(language.t(player, message), color));
    }
}
