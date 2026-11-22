package org.encinet.mik.feature.community.identity;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.integration.mikd.RequestPort;
import org.encinet.mik.protocol.ChatPlayer;
import org.encinet.mik.protocol.IdentityBindingView;
import org.encinet.mik.protocol.IdentityCode;
import org.encinet.mik.protocol.IdentityCommand;
import org.encinet.mik.protocol.IdentityList;
import org.encinet.mik.protocol.IdentityPlayerCode;
import org.encinet.mik.protocol.IdentityResult;
import org.encinet.mik.protocol.IdentityUnlink;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.role.RolePermissions;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;

public final class IdentityBindingModule {
    private final JavaPlugin plugin;
    private final LanguageService languages;
    private final RequestPort requests;
    private final Function<Player, BooleanSupplier> authentication;

    public IdentityBindingModule(JavaPlugin plugin, LanguageService languages, RequestPort requests,
                                 Function<Player, BooleanSupplier> authentication) {
        this.plugin = plugin;
        this.languages = languages;
        this.requests = requests;
        this.authentication = authentication;
    }

    public void registerCommands() {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register(Commands.literal("bind")
                    .executes(context -> help(context.getSource().getSender()))
                    .then(Commands.literal("list").executes(context -> ownList(context.getSource().getSender())))
                    .then(Commands.literal("unlink").executes(context -> ownList(context.getSource().getSender()))
                            .then(Commands.argument("binding", StringArgumentType.word()).executes(context -> unlink(
                                    context.getSource().getSender(), StringArgumentType.getString(context, "binding"), false))))
                    .then(Commands.literal("cancel").then(Commands.argument("platform", StringArgumentType.word())
                            .executes(context -> code(context.getSource().getSender(), StringArgumentType.getString(context, "platform"), true))))
                    .then(Commands.argument("platform", StringArgumentType.word()).executes(context -> code(
                            context.getSource().getSender(), StringArgumentType.getString(context, "platform"), false)))
                    .build(), languages.t(Language.DEFAULT, Message.IDENTITY_COMMAND_DESCRIPTION));
            event.registrar().register(Commands.literal("bindadmin")
                    .requires(source -> source.getSender().hasPermission(RolePermissions.CUSTODIAN))
                    .executes(context -> {
                        send(context.getSource().getSender(), Message.IDENTITY_ADMIN_HELP, NamedTextColor.AQUA);
                        return Command.SINGLE_SUCCESS;
                    })
                    .then(Commands.literal("lookup").then(Commands.argument("player", StringArgumentType.word())
                            .executes(context -> lookup(context.getSource().getSender(), StringArgumentType.getString(context, "player")))))
                    .then(Commands.literal("unlink").then(Commands.argument("binding", StringArgumentType.word())
                            .then(Commands.literal("confirm").executes(context -> unlink(
                                    context.getSource().getSender(), StringArgumentType.getString(context, "binding"), true)))))
                    .build(), languages.t(Language.DEFAULT, Message.IDENTITY_ADMIN_COMMAND_DESCRIPTION));
        });
    }

    public Component challenge(Language language, List<IdentityCode> codes) {
        return IdentityBindingGuidance.challenge(languages, language, codes);
    }

    public static String platformName(String platform) {
        return IdentityBindingGuidance.platformName(platform);
    }

    private int help(CommandSender sender) {
        send(sender, Message.IDENTITY_HELP, NamedTextColor.AQUA);
        return submit(sender, IdentityCommand.newBuilder().setPlatforms(true), result -> {
            String supported = result.getPlatformsList().stream().map(platform ->
                    IdentityBindingGuidance.platformName(platform) + " (" + platform + ")")
                    .reduce((left, right) -> left + " · " + right)
                    .orElseGet(() -> languages.t(language(sender), Message.IDENTITY_PLATFORM_NONE));
            send(sender, Message.IDENTITY_PLATFORMS, NamedTextColor.WHITE, supported);
        });
    }

    private int code(CommandSender sender, String input, boolean cancel) {
        Player player = player(sender);
        if (player == null) return 0;
        String platform = input.toLowerCase(Locale.ROOT);
        var code = IdentityPlayerCode.newBuilder().setPlatform(platform).setPlayer(
                ChatPlayer.newBuilder().setId(player.getUniqueId().toString()).setName(player.getName()));
        var request = IdentityCommand.newBuilder();
        if (cancel) request.setCancel(code);
        else request.setIssue(code);
        return submit(sender, request, result -> {
            if (result.getOutcome() == IdentityResult.Outcome.UNSUPPORTED_PLATFORM) {
                send(sender, Message.IDENTITY_UNSUPPORTED_PLATFORM, NamedTextColor.RED, platform);
            } else if (cancel) {
                boolean done = result.getOutcome() == IdentityResult.Outcome.DONE;
                send(sender, done ? Message.IDENTITY_CODE_CANCELLED : Message.IDENTITY_CODE_NOT_PENDING,
                        done ? NamedTextColor.GREEN : NamedTextColor.YELLOW, IdentityBindingGuidance.platformName(platform));
            } else {
                IdentityCode issued = result.getCodes(0);
                Language language = language(sender);
                sender.sendMessage(IdentityBindingGuidance.community(languages, language));
                sender.sendMessage(IdentityBindingGuidance.instruction(languages, language, Message.IDENTITY_CODE_REDEEM_RICH, issued));
                sender.sendMessage(IdentityBindingGuidance.expiry(languages, language, issued.getExpiresAtMs()));
            }
        });
    }

    private int ownList(CommandSender sender) {
        Player player = player(sender);
        if (player == null) return 0;
        return list(sender, IdentityList.newBuilder().setPlayerId(player.getUniqueId().toString()), null);
    }

    private int lookup(CommandSender sender, String target) {
        var query = IdentityList.newBuilder();
        try { query.setPlayerId(UUID.fromString(target).toString()); }
        catch (IllegalArgumentException notAnId) { query.setPlayerName(target); }
        return list(sender, query, target);
    }

    private int list(CommandSender sender, IdentityList.Builder query, String target) {
        return submit(sender, IdentityCommand.newBuilder().setList(query), result -> {
            boolean admin = target != null;
            if (result.getBindingsCount() == 0) {
                if (query.getAfterBindingId().isEmpty()) {
                    send(sender, admin ? Message.IDENTITY_ADMIN_LOOKUP_EMPTY : Message.IDENTITY_BINDINGS_EMPTY, NamedTextColor.YELLOW);
                }
                return;
            }
            if (query.getAfterBindingId().isEmpty()) {
                if (admin) send(sender, Message.IDENTITY_ADMIN_LOOKUP_TITLE, NamedTextColor.AQUA, target);
                else send(sender, Message.IDENTITY_BINDINGS_TITLE, NamedTextColor.AQUA);
            }
            result.getBindingsList().forEach(binding -> row(sender, binding, admin));
            if (!result.getNextBindingId().isEmpty()) list(sender, query.setAfterBindingId(result.getNextBindingId()), target);
        });
    }

    private int unlink(CommandSender sender, String selection, boolean admin) {
        if (!admin && player(sender) == null) return 0;
        Message missing = admin ? Message.IDENTITY_ADMIN_BINDING_NOT_FOUND : Message.IDENTITY_BINDING_NOT_OWNED;
        var request = IdentityUnlink.newBuilder();
        try { request.setBindingId(UUID.fromString(selection).toString()); }
        catch (IllegalArgumentException invalid) {
            send(sender, missing, NamedTextColor.RED);
            return 0;
        }
        if (admin) request.setOperator(true);
        else request.setOwnerId(((Player) sender).getUniqueId().toString());
        return submit(sender, IdentityCommand.newBuilder().setUnlink(request), result -> {
            if (result.getOutcome() == IdentityResult.Outcome.NOT_FOUND) {
                send(sender, missing, NamedTextColor.RED);
                return;
            }
            var binding = result.getBindings(0);
            String platform = IdentityBindingGuidance.platformName(binding.getExternal().getPlatform());
            if (admin) send(sender, Message.IDENTITY_ADMIN_BINDING_UNLINKED, NamedTextColor.GREEN, platform, binding.getPlayer().getName());
            else send(sender, Message.IDENTITY_BINDING_UNLINKED, NamedTextColor.GREEN, platform);
        });
    }

    private void row(CommandSender sender, IdentityBindingView binding, boolean admin) {
        Language language = language(sender);
        var external = binding.getExternal();
        String account = external.getDisplayName().isBlank() ? languages.t(language, Message.IDENTITY_BINDING_UNNAMED) : external.getDisplayName();
        String platform = IdentityBindingGuidance.platformName(external.getPlatform());
        String command = admin ? "/bindadmin unlink " + binding.getBindingId() + " confirm" : "/bind unlink " + binding.getBindingId();
        String date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(language.locale())
                .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(binding.getLinkedAtMs()));
        Component label = Component.text(platform + " · " + account, NamedTextColor.WHITE)
                .hoverEvent(HoverEvent.showText(Component.text(languages.t(language, Message.IDENTITY_BINDING_INFO, date, binding.getBindingId()))));
        if (admin) label = Component.text(languages.t(language, Message.IDENTITY_ADMIN_BINDING_OWNER,
                binding.getPlayer().getName(), binding.getPlayer().getId()) + " · ").append(label);
        Component action = Component.text(languages.t(language, Message.IDENTITY_UNLINK_ACTION), NamedTextColor.YELLOW)
                .clickEvent(ClickEvent.suggestCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text(languages.t(language, Message.IDENTITY_UNLINK_HINT))));
        sender.sendMessage(label.appendSpace().append(sender instanceof Player ? action : Component.text(command)));
    }

    private int submit(CommandSender sender, IdentityCommand.Builder request, Consumer<IdentityResult> render) {
        BooleanSupplier authorized = sender instanceof Player player ? authentication.apply(player) : () -> true;
        if (!authorized.getAsBoolean()) {
            send(sender, Message.IDENTITY_UNAVAILABLE, NamedTextColor.RED);
            return 0;
        }
        requests.identity(request.build(), authorized).whenComplete((result, error) -> {
            if (!plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!authorized.getAsBoolean()) return;
                if (error == null) render.accept(result);
                else send(sender, Message.IDENTITY_UNAVAILABLE, NamedTextColor.RED);
            });
        });
        return Command.SINGLE_SUCCESS;
    }

    private Player player(CommandSender sender) {
        if (sender instanceof Player player) return player;
        send(sender, Message.PLAYER_ONLY, NamedTextColor.RED);
        return null;
    }

    private Language language(CommandSender sender) {
        return sender instanceof Player player ? languages.language(player) : Language.DEFAULT;
    }

    private void send(CommandSender sender, Message message, NamedTextColor color, Object... arguments) {
        sender.sendMessage(Component.text(languages.t(language(sender), message, arguments), color));
    }
}
