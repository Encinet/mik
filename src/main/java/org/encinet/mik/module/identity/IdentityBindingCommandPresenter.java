package org.encinet.mik.module.identity;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Renders all Minecraft-side identity command responses. */
final class IdentityBindingCommandPresenter {

    private final LanguageService languages;
    private final IdentityBindingRuntime runtime;

    IdentityBindingCommandPresenter(LanguageService languages, IdentityBindingRuntime runtime) {
        this.languages = Objects.requireNonNull(languages, "languages");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    void help(CommandSender sender) {
        sender.sendMessage(text(sender, Message.IDENTITY_HELP, NamedTextColor.AQUA));
        platforms(sender);
    }

    void adminHelp(CommandSender sender) {
        sender.sendMessage(text(sender, Message.IDENTITY_ADMIN_HELP, NamedTextColor.AQUA));
    }

    void platforms(CommandSender sender) {
        Language language = language(sender);
        String supported = runtime.platforms().stream()
                .map(platform -> platform.displayName() + " (" + platform.id() + ")")
                .reduce((left, right) -> left + " · " + right)
                .orElseGet(() -> languages.t(language, Message.IDENTITY_PLATFORM_NONE));
        sender.sendMessage(Component.text(languages.t(language,
                Message.IDENTITY_PLATFORMS, supported), NamedTextColor.GRAY));
    }

    void playerOnly(CommandSender sender) {
        sender.sendMessage(text(sender, Message.PLAYER_ONLY, NamedTextColor.RED));
    }

    void unavailable(CommandSender sender) {
        sender.sendMessage(text(sender, Message.IDENTITY_UNAVAILABLE, NamedTextColor.RED));
    }

    void storageError(CommandSender sender) {
        sender.sendMessage(text(sender, Message.IDENTITY_STORAGE_ERROR, NamedTextColor.RED));
    }

    void unsupportedPlatform(CommandSender sender, String requestedPlatform,
                             boolean showPlatforms) {
        sender.sendMessage(text(sender, Message.IDENTITY_UNSUPPORTED_PLATFORM,
                NamedTextColor.RED, requestedPlatform));
        if (showPlatforms) {
            platforms(sender);
        }
    }

    void codeIssued(CommandSender sender, IdentityPlatform platform, IdentityLinkCode issue) {
        Language language = language(sender);
        String instruction = platform.redemptionInstruction().replace("{code}", issue.code());
        Component code = Component.text(issue.code(), NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.copyToClipboard(instruction))
                .hoverEvent(HoverEvent.showText(Component.text(
                        languages.t(language, Message.IDENTITY_CODE_COPY))));
        sender.sendMessage(Component.text(languages.t(language,
                        Message.IDENTITY_CODE_ISSUED, platform.displayName()),
                        NamedTextColor.GREEN)
                .append(Component.space())
                .append(code));
        sender.sendMessage(Component.text(languages.t(language,
                        Message.IDENTITY_CODE_REDEEM, platform.displayName(), instruction),
                        NamedTextColor.AQUA)
                .clickEvent(ClickEvent.copyToClipboard(instruction))
                .hoverEvent(HoverEvent.showText(Component.text(
                        languages.t(language, Message.IDENTITY_CODE_COPY)))));
        sender.sendMessage(Component.text(languages.t(language,
                Message.IDENTITY_CODE_EXPIRY), NamedTextColor.GRAY));
        if (platform.scopeIsolated()) {
            sender.sendMessage(Component.text(languages.t(language,
                    Message.IDENTITY_SCOPE_NOTICE), NamedTextColor.YELLOW));
        }
    }

    void ownBindings(CommandSender sender, List<IdentityBinding> bindings) {
        if (bindings.isEmpty()) {
            sender.sendMessage(text(sender, Message.IDENTITY_BINDINGS_EMPTY,
                    NamedTextColor.YELLOW));
            return;
        }
        Language language = language(sender);
        sender.sendMessage(Component.text(languages.t(language,
                Message.IDENTITY_BINDINGS_TITLE), NamedTextColor.AQUA));
        bindings.forEach(binding -> sender.sendMessage(bindingLine(binding, language)));
        sender.sendMessage(Component.text(languages.t(language,
                Message.IDENTITY_BINDINGS_UNLINK_HINT), NamedTextColor.GRAY));
    }

    void codeCancelled(CommandSender sender, boolean cancelled, IdentityPlatform platform) {
        sender.sendMessage(text(sender, cancelled
                        ? Message.IDENTITY_CODE_CANCELLED
                        : Message.IDENTITY_CODE_NOT_PENDING,
                cancelled ? NamedTextColor.GREEN : NamedTextColor.YELLOW,
                platform.displayName()));
    }

    void bindingNotOwned(CommandSender sender) {
        sender.sendMessage(text(sender, Message.IDENTITY_BINDING_NOT_OWNED,
                NamedTextColor.RED));
    }

    void bindingUnlinked(CommandSender sender, String platformId, int removedCount) {
        sender.sendMessage(text(sender, Message.IDENTITY_BINDING_UNLINKED,
                NamedTextColor.GREEN, platformName(platformId), removedCount));
    }

    void adminLookup(CommandSender sender, String playerName,
                     List<IdentityBinding> bindings) {
        if (bindings.isEmpty()) {
            sender.sendMessage(text(sender, Message.IDENTITY_ADMIN_LOOKUP_EMPTY,
                    NamedTextColor.YELLOW));
            return;
        }
        Language language = language(sender);
        sender.sendMessage(Component.text(languages.t(language,
                Message.IDENTITY_ADMIN_LOOKUP_TITLE, playerName), NamedTextColor.AQUA));
        for (IdentityBinding binding : bindings) {
            sender.sendMessage(Component.text(languages.t(language,
                            Message.IDENTITY_ADMIN_BINDING_OWNER, binding.playerName(),
                            binding.playerId().toString().substring(0, 8)),
                            NamedTextColor.WHITE)
                    .append(Component.space())
                    .append(bindingLine(binding, language)));
        }
    }

    void adminBindingMissing(CommandSender sender) {
        sender.sendMessage(text(sender, Message.IDENTITY_ADMIN_BINDING_NOT_FOUND,
                NamedTextColor.RED));
    }

    void adminBindingUnlinked(
            CommandSender sender,
            String platformId,
            String playerName,
            int removedCount
    ) {
        sender.sendMessage(text(sender, Message.IDENTITY_ADMIN_BINDING_UNLINKED,
                NamedTextColor.GREEN, platformName(platformId), playerName, removedCount));
    }

    String platformName(String platformId) {
        return runtime.platform(platformId)
                .map(IdentityPlatform::displayName)
                .orElse(platformId);
    }

    private Component bindingLine(IdentityBinding binding, Language language) {
        String displayName = binding.externalDisplayName().isBlank()
                ? languages.t(language, Message.UNKNOWN)
                : binding.externalDisplayName();
        String date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                .withLocale(language.locale())
                .withZone(ZoneId.systemDefault())
                .format(binding.createdAt());
        return Component.text(platformName(binding.externalKey().platform()),
                        NamedTextColor.AQUA)
                .append(Component.text(languages.t(language,
                        Message.IDENTITY_BINDING_DETAILS, displayName,
                        scopeFingerprint(binding.externalKey()), date), NamedTextColor.GRAY));
    }

    private static String scopeFingerprint(ExternalIdentityKey key) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(key.platform().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(key.issuer().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(key.scope().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().withUpperCase().formatHex(digest.digest(), 0, 4);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private Language language(CommandSender sender) {
        return sender instanceof Player player
                ? languages.language(player)
                : Language.DEFAULT;
    }

    private Component text(
            CommandSender sender,
            Message message,
            NamedTextColor color,
            Object... arguments
    ) {
        return Component.text(languages.t(language(sender), message, arguments), color);
    }
}
