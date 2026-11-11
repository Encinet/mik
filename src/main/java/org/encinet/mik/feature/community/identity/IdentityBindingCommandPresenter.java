package org.encinet.mik.feature.community.identity;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.i18n.RichArg;
import org.encinet.mik.shared.i18n.TextArg;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

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
        entries(bindings, language).forEach(entry -> sender.sendMessage(bindingLine(entry, language, false)));
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
        for (BindingEntry entry : entries(bindings, language)) {
            IdentityBinding binding = entry.binding();
            Component row = Component.text(languages.t(language,
                            Message.IDENTITY_ADMIN_BINDING_OWNER, binding.playerName(),
                            binding.playerId().toString().substring(0, 8)),
                            NamedTextColor.WHITE)
                    .append(Component.space())
                    .append(bindingLine(entry, language, true));
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

    Map<UUID, String> selectionLabels(CommandSender sender, List<IdentityBinding> bindings) {
        return entries(bindings, language(sender)).stream().collect(Collectors.toUnmodifiableMap(
                entry -> entry.binding().bindingId(), entry -> plain(entry.label())));
    }

    String platformName(String platformId) {
        return runtime.platform(platformId)
                .map(IdentityPlatform::displayName)
                .orElse(platformId);
    }

    private Component bindingLabel(IdentityBinding binding, Language language) {
        String displayName = binding.externalDisplayName().isBlank()
                ? languages.t(language, Message.IDENTITY_BINDING_UNNAMED)
                : binding.externalDisplayName();
        boolean scoped = !binding.externalKey().scope().isEmpty();
        String community = !scoped ? "" : binding.scopeDisplayName().isBlank()
                ? languages.t(language, Message.IDENTITY_BINDING_COMMUNITY, scopeFingerprint(binding.externalKey()))
                : binding.scopeDisplayName();
        return languages.rich(language, Message.IDENTITY_BINDING_LABEL_RICH, NamedTextColor.WHITE,
                new TextArg[]{TextArg.of("kind", scoped ? "community" : "account")},
                RichArg.component("platform", Component.text(platformName(binding.externalKey().platform()), NamedTextColor.AQUA)),
                RichArg.component("account", Component.text(displayName, NamedTextColor.WHITE)),
                RichArg.component("community", Component.text(community, NamedTextColor.WHITE)));
    }

    private List<BindingEntry> entries(List<IdentityBinding> bindings, Language language) {
        List<BindingEntry> entries = bindings.stream()
                .map(binding -> new BindingEntry(binding, bindingLabel(binding, language))).toList();
        Map<String, Long> counts = entries.stream().collect(Collectors.groupingBy(
                entry -> plain(entry.label()), Collectors.counting()));
        return entries.stream().map(entry -> counts.get(plain(entry.label())) > 1
                ? new BindingEntry(entry.binding(), languages.rich(language,
                        Message.IDENTITY_BINDING_REFERENCE_RICH, NamedTextColor.WHITE,
                        RichArg.component("label", entry.label()),
                        RichArg.component("reference", Component.text(bindingReference(entry.binding(), bindings)))))
                : entry).toList();
    }

    private Component bindingLine(BindingEntry entry, Language language, boolean admin) {
        IdentityBinding binding = entry.binding();
        String date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                .withLocale(language.locale())
                .withZone(ZoneId.systemDefault())
                .format(binding.createdAt());
        Component unlink = Component.text(languages.t(language, Message.IDENTITY_UNLINK_ACTION), NamedTextColor.YELLOW)
                .decorate(TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.runCommand(unlinkCommand(binding, admin)))
                .hoverEvent(HoverEvent.showText(Component.text(languages.t(language, Message.IDENTITY_UNLINK_HINT))));
        return entry.label().hoverEvent(HoverEvent.showText(Component.text(languages.t(language,
                        Message.IDENTITY_BINDING_INFO, date, binding.bindingId().toString()), NamedTextColor.WHITE)))
                .append(Component.space()).append(unlink);
    }

    private static String bindingReference(IdentityBinding binding, List<IdentityBinding> bindings) {
        String id = binding.bindingId().toString();
        for (int length = 8; length < id.length(); length++) {
            String prefix = id.substring(0, length);
            if (bindings.stream().noneMatch(other -> !other.bindingId().equals(binding.bindingId())
                    && other.bindingId().toString().startsWith(prefix))) return prefix;
        }
        return id;
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private record BindingEntry(IdentityBinding binding, Component label) { }

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
