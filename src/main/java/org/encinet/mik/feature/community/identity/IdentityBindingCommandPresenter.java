package org.encinet.mik.feature.community.identity;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;

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
                Message.IDENTITY_PLATFORMS, supported), NamedTextColor.WHITE));
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
        sender.sendMessage(IdentityBindingGuidance.community(languages, language));
        sender.sendMessage(IdentityBindingGuidance.instruction(languages, language,
                Message.IDENTITY_CODE_REDEEM_RICH, platform, issue));
        sender.sendMessage(IdentityBindingGuidance.expiry(languages, language));
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
        bindings.forEach(binding -> sender.sendMessage(bindingLine(binding, language, false)));
        sender.sendMessage(Component.text(languages.t(language,
                Message.IDENTITY_BINDINGS_UNLINK_HINT), NamedTextColor.WHITE));
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

    void bindingUnlinked(CommandSender sender, IdentityBinding binding) {
        sender.sendMessage(text(sender, Message.IDENTITY_BINDING_UNLINKED,
                NamedTextColor.GREEN, platformName(binding.externalKey().platform())));
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
            Component row = Component.text(languages.t(language,
                            Message.IDENTITY_ADMIN_BINDING_OWNER, binding.playerName(),
                            binding.playerId().toString().substring(0, 8)),
                            NamedTextColor.WHITE)
                    .append(Component.space())
                    .append(bindingLine(binding, language, true));
            if (!(sender instanceof Player)) {
                row = row.append(Component.text(" · " + unlinkCommand(binding, true), NamedTextColor.WHITE));
            }
            sender.sendMessage(row);
        }
    }

    void adminBindingMissing(CommandSender sender) {
        sender.sendMessage(text(sender, Message.IDENTITY_ADMIN_BINDING_NOT_FOUND,
                NamedTextColor.RED));
    }

    void adminBindingUnlinked(CommandSender sender, IdentityBinding binding) {
        sender.sendMessage(text(sender, Message.IDENTITY_ADMIN_BINDING_UNLINKED,
                NamedTextColor.GREEN, platformName(binding.externalKey().platform()), binding.playerName()));
    }

    String selectionLabel(IdentityBinding binding) {
        return platformName(binding.externalKey().platform()) + " · " + binding.externalDisplayName()
                + " · " + binding.scopeDisplayName() + " · " + scopeFingerprint(binding.externalKey());
    }

    String platformName(String platformId) {
        return runtime.platform(platformId)
                .map(IdentityPlatform::displayName)
                .orElse(platformId);
    }

    Component bindingLine(IdentityBinding binding, Language language, boolean admin) {
        String displayName = binding.externalDisplayName().isBlank()
                ? languages.t(language, Message.UNKNOWN)
                : binding.externalDisplayName();
        String date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                .withLocale(language.locale())
                .withZone(ZoneId.systemDefault())
                .format(binding.createdAt());
        String scopeLabel = binding.externalKey().scope().isEmpty()
                ? languages.t(language, Message.IDENTITY_BINDING_ACCOUNT)
                : binding.scopeDisplayName();
        String origin = (scopeLabel.isBlank() ? "" : scopeLabel + " · ") + scopeFingerprint(binding.externalKey());
        Component unlink = Component.text(languages.t(language, Message.IDENTITY_UNLINK_ACTION), NamedTextColor.YELLOW)
                .clickEvent(ClickEvent.runCommand(unlinkCommand(binding, admin)))
                .hoverEvent(HoverEvent.showText(Component.text(languages.t(language, Message.IDENTITY_UNLINK_HINT))));
        return Component.text(platformName(binding.externalKey().platform()),
                        NamedTextColor.AQUA)
                .append(Component.text(languages.t(language,
                        Message.IDENTITY_BINDING_DETAILS, displayName,
                        origin, date), NamedTextColor.WHITE))
                .append(Component.space()).append(unlink);
    }

    static String unlinkCommand(IdentityBinding binding, boolean admin) {
        return admin ? "/bindadmin unlink " + binding.bindingId() + " confirm"
                : "/bind unlink " + binding.bindingId();
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
