package org.encinet.mik.module.i18n;

import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.quickwrite.fluent4j.ast.entry.FluentAttributeEntry;
import net.quickwrite.fluent4j.ast.entry.FluentMessage;
import net.quickwrite.fluent4j.ast.pattern.ArgumentList;
import net.quickwrite.fluent4j.container.ArgumentListBuilder;
import net.quickwrite.fluent4j.container.FluentBundle;
import net.quickwrite.fluent4j.container.FluentBundleBuilder;
import net.quickwrite.fluent4j.container.FluentResource;
import net.quickwrite.fluent4j.iterator.FluentIteratorFactory;
import net.quickwrite.fluent4j.impl.container.FluentResolverScope;
import net.quickwrite.fluent4j.parser.ResourceParser;
import net.quickwrite.fluent4j.parser.ResourceParserBuilder;
import net.quickwrite.fluent4j.result.StringResultFactory;
import net.quickwrite.fluent4j.result.ResultBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLocaleChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.module.menu.FloatingMenuDefinition;
import org.encinet.mik.module.menu.FloatingMenuFraming;
import org.encinet.mik.module.menu.FloatingMenuLayouts;
import org.encinet.mik.module.menu.FloatingMenus;
import org.encinet.mik.util.GeoUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.Optional;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class LanguageService implements Listener {

    public static final String AUTO = "auto";

    private static final String ACTION_LANGUAGE_PREFIX = "language:";
    private static final String REGION_AUTOMATIC = "automatic";
    private static final String REGION_LANGUAGES = "languages";
    private static final String REGION_NAVIGATION = "navigation";

    private final JavaPlugin plugin;
    private final Map<UUID, String> preferences = new ConcurrentHashMap<>();
    private final Map<UUID, Language> clientLanguages = new ConcurrentHashMap<>();
    private final Map<Language, FluentBundle> bundles = new EnumMap<>(Language.class);
    private final List<LanguageChangeListener> languageChangeListeners = new CopyOnWriteArrayList<>();

    private File languageFile;
    private YamlConfiguration languageData;

    public LanguageService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        loadBundles();
        languageFile = new File(plugin.getDataFolder(), "languages.yml");
        if (!languageFile.exists()) {
            try {
                if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                    plugin.getLogger().severe("Failed to create plugin data folder.");
                }
                if (!languageFile.createNewFile()) {
                    plugin.getLogger().warning("languages.yml already exists but was not visible during setup.");
                }
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to create languages.yml: " + e.getMessage());
            }
        }
        languageData = YamlConfiguration.loadConfiguration(languageFile);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("LanguageService enabled");
    }

    public void registerCommands(LifecycleEventManager<Plugin> manager) {
        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> event.registrar().register(Commands.literal("lang")
                .executes(ctx -> {
                    if (ctx.getSource().getSender() instanceof Player player) {
                        openMenu(player);
                    } else {
                        ctx.getSource().getSender().sendMessage(Component.text(t(Language.DEFAULT, Message.PLAYER_ONLY), NamedTextColor.RED));
                    }
                    return Command.SINGLE_SUCCESS;
                })
                .build(), t(Language.DEFAULT, Message.LANGUAGE_COMMAND_DESCRIPTION)));
    }

    private void selectLanguage(Player player, String value) {
        setPreference(player.getUniqueId(), value);
        player.sendMessage(Component.text(t(player, Message.LANGUAGE_SET, languageLabel(player, value)), NamedTextColor.GREEN));
        openMenu(player);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        preferences.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        rememberClientLanguage(event.getPlayer().getUniqueId(), event.getPlayer().locale());
    }

    @EventHandler
    public void onPlayerLocaleChange(PlayerLocaleChangeEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        boolean automatic = AUTO.equals(preference(playerId));
        if (rememberClientLanguage(playerId, event.locale()) && automatic) {
            notifyLanguageChanged(event.getPlayer());
        }
    }

    public void openMenu(Player player) {
        Language language = language(player);
        String currentPreference = preference(player.getUniqueId());
        FloatingMenuDefinition.Builder builder = FloatingMenuDefinition.screen(
                        "language",
                        Component.text(t(language, Message.LANGUAGE_MENU_TITLE),
                                NamedTextColor.DARK_PURPLE))
                .framing(FloatingMenuFraming.PANORAMIC)
                .layout(FloatingMenuLayouts.menu(
                        FloatingMenuLayouts.region(REGION_AUTOMATIC,
                                FloatingMenuLayouts.offset(
                                        FloatingMenuLayouts.adaptiveRow(0.0),
                                        0.0, 0.0, 0.30)),
                        FloatingMenuLayouts.region(REGION_LANGUAGES,
                                FloatingMenuLayouts.adaptiveDomeGrid(
                                        6, 0.34, 0.22, 0.48, 0.22)),
                        FloatingMenuLayouts.navigation(REGION_NAVIGATION)));
        builder.choice("automatic", AUTO.equals(currentPreference), Material.COMPASS,
                        preferenceLabel(player, AUTO))
                .region(REGION_AUTOMATIC)
                .primary((p, handle) -> selectLanguage(p, AUTO));
        for (Language option : Language.values()) {
            String value = option.id();
            builder.choice(ACTION_LANGUAGE_PREFIX + value, value.equals(currentPreference),
                            languageMaterial(option),
                            preferenceLabel(player, value))
                    .region(REGION_LANGUAGES)
                    .primary((p, handle) -> selectLanguage(p, value));
        }
        builder.back(Component.text(t(language, Message.BACK_TO_MAIN), NamedTextColor.GREEN))
                .region(REGION_NAVIGATION);
        FloatingMenus.present(player, builder.build());
    }

    public Language language(Player player) {
        String preference = preference(player.getUniqueId());
        Language manual = Language.fromId(preference).orElse(null);
        if (manual != null) {
            return manual;
        }
        Language clientLanguage = clientLanguage(player);
        if (clientLanguage != null) {
            return clientLanguage;
        }
        Language geoLanguage = geoLanguage(player);
        if (geoLanguage != null) {
            return geoLanguage;
        }
        return Language.DEFAULT;
    }

    public Language language(UUID playerId, InetAddress fallbackAddress) {
        if (playerId != null) {
            Language manual = Language.fromId(preference(playerId)).orElse(null);
            if (manual != null) {
                return manual;
            }
            Language recordedClientLanguage = recordedClientLanguage(playerId);
            if (recordedClientLanguage != null) {
                return recordedClientLanguage;
            }
        }
        return geoLanguage(fallbackAddress);
    }

    /**
     * Returns a player's persisted language preference without guessing from an address.
     *
     * <p>This is intended for authenticated external identities, where the caller has a
     * Minecraft UUID but no trustworthy client or network context. An empty result lets the
     * external platform choose its own explicit default instead of inheriting the generic
     * address fallback.</p>
     */
    public Optional<Language> preferredLanguage(UUID playerId) {
        if (playerId == null) {
            return Optional.empty();
        }
        Optional<Language> manual = Language.fromId(preference(playerId));
        if (manual.isPresent()) {
            return manual;
        }
        return Optional.ofNullable(recordedClientLanguage(playerId));
    }

    public Language languageForAddress(InetAddress address) {
        return geoLanguage(address);
    }

    private Language clientLanguage(Player player) {
        try {
            Language language = Language.fromLocale(player.locale()).orElse(null);
            if (language != null) {
                rememberClientLanguage(player.getUniqueId(), player.locale());
            }
            return language;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private boolean rememberClientLanguage(UUID playerId, Locale locale) {
        Language language = Language.fromLocale(locale).orElse(null);
        if (language == null) {
            return false;
        }

        Language previous = clientLanguages.put(playerId, language);
        if (previous == language
                && language.id().equals(languageData.getString(playerId + ".client-language"))) {
            return false;
        }
        languageData.set(playerId + ".client-language", language.id());
        try {
            languageData.save(languageFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save client language for " + playerId + ": " + e.getMessage());
        }
        return previous != language;
    }

    private Language recordedClientLanguage(UUID playerId) {
        Language cached = clientLanguages.get(playerId);
        if (cached != null) {
            return cached;
        }
        Language loaded = Language.fromId(languageData.getString(playerId + ".client-language")).orElse(null);
        if (loaded != null) {
            clientLanguages.put(playerId, loaded);
        }
        return loaded;
    }

    private Language geoLanguage(Player player) {
        InetSocketAddress address = player.getAddress();
        if (address == null) {
            return null;
        }
        InetAddress inetAddress = address.getAddress();
        if (inetAddress == null) {
            return null;
        }
        return geoLanguage(inetAddress);
    }

    private static Language geoLanguage(InetAddress address) {
        if (address == null) {
            return Language.EN_US;
        }
        return Language.fromId(GeoUtil.languageCode(address)).orElse(Language.EN_US);
    }

    public String preference(UUID playerId) {
        return preferences.computeIfAbsent(playerId, this::loadPreference);
    }

    public String languageLabel(Player player) {
        return languageLabel(player, preference(player.getUniqueId()));
    }

    public String t(Player player, Message message, Object... args) {
        return t(language(player), message, args);
    }

    public String t(Language language, Message message, Object... args) {
        FluentBundle bundle = bundles.getOrDefault(language, bundles.get(Language.DEFAULT));
        ArgumentList arguments = arguments(args);
        return bundle.resolveMessage(message.key(), arguments, StringResultFactory.construct())
                .map(Object::toString)
                .orElseGet(() -> resolveDefault(message, arguments, args));
    }

    public List<String> attributeNames(Language language, String messageId) {
        FluentBundle bundle = bundles.get(language);
        if (bundle == null) {
            return List.of();
        }
        return bundle.getMessage(messageId)
                .map(FluentMessage::getAttributes)
                .stream()
                .flatMap(Arrays::stream)
                .map(attribute -> attribute.getIdentifier().getSimpleIdentifier())
                .toList();
    }

    public Optional<String> attribute(Language language, String messageId, String attributeName) {
        FluentBundle bundle = bundles.get(language);
        if (bundle == null) {
            return Optional.empty();
        }

        Optional<FluentAttributeEntry.Attribute> attribute = bundle.getMessage(messageId)
                .flatMap(message -> message.getAttribute(attributeName));
        if (attribute.isEmpty()) {
            return Optional.empty();
        }

        ResultBuilder result = StringResultFactory.construct();
        attribute.get().resolve(new FluentResolverScope(bundle, ArgumentListBuilder.builder().build(), result), result);
        return Optional.of(result.toString());
    }

    public Optional<Component> richAttribute(Language language, String messageId, String attributeName,
                                             NamedTextColor baseColor, RichArg... richArgs) {
        FluentBundle bundle = bundles.get(language);
        if (bundle == null) {
            return Optional.empty();
        }

        Optional<FluentAttributeEntry.Attribute> attribute = bundle.getMessage(messageId)
                .flatMap(message -> message.getAttribute(attributeName));
        if (attribute.isEmpty()) {
            return Optional.empty();
        }

        ArgumentList.Builder arguments = ArgumentListBuilder.builder();
        Map<String, RichArg> tokens = new ConcurrentHashMap<>();
        for (int i = 0; i < richArgs.length; i++) {
            RichArg arg = richArgs[i];
            String token = "[[MIK_RICH_" + i + "]]";
            arguments.add(arg.name(), token);
            tokens.put(token, arg);
        }

        ResultBuilder result = StringResultFactory.construct();
        attribute.get().resolve(new FluentResolverScope(bundle, arguments.build(), result), result);
        return Optional.of(replaceRichTokens(result.toString(), baseColor, tokens));
    }

    public String format(Player player, Message message, TextArg... args) {
        return format(language(player), message, args);
    }

    public String format(Language language, Message message, TextArg... args) {
        return resolve(language, message, namedArguments(args));
    }

    public Component text(Player player, Message message, NamedTextColor color, Object... args) {
        return Component.text(t(player, message, args), color);
    }

    public Component text(Language language, Message message, NamedTextColor color, Object... args) {
        return Component.text(t(language, message, args), color);
    }

    public boolean titleMatches(Message message, String title) {
        for (Language language : Language.values()) {
            if (t(language, message).equals(title)) {
                return true;
            }
        }
        return false;
    }

    public boolean titleStartsWith(Message message, String title) {
        for (Language language : Language.values()) {
            if (title.startsWith(t(language, message))) {
                return true;
            }
        }
        return false;
    }

    public Component rich(Player player, Message message, NamedTextColor baseColor, RichArg... richArgs) {
        return rich(language(player), message, baseColor, richArgs);
    }

    public Component rich(Language language, Message message, NamedTextColor baseColor, RichArg... richArgs) {
        ArgumentList.Builder arguments = ArgumentListBuilder.builder();
        Map<String, RichArg> tokens = new ConcurrentHashMap<>();
        for (int i = 0; i < richArgs.length; i++) {
            RichArg arg = richArgs[i];
            String token = "[[MIK_RICH_" + i + "]]";
            arguments.add(arg.name(), token);
            tokens.put(token, arg);
        }
        String rendered = resolve(language, message, arguments.build(), richArgs);
        return replaceRichTokens(rendered, baseColor, tokens);
    }

    private Component preferenceLabel(Player player, String value) {
        Language language = language(player);
        boolean selected = preference(player.getUniqueId()).equals(value);
        Component label = Component.text(languageLabel(player, value),
                selected ? NamedTextColor.GREEN : NamedTextColor.AQUA);
        if (AUTO.equals(value)) {
            label = label.append(Component.newline())
                    .append(Component.text(t(language, Message.LANGUAGE_AUTO_DESCRIPTION),
                            NamedTextColor.DARK_GRAY));
        }
        if (selected) {
            label = label.append(Component.newline())
                    .append(Component.text(t(language, Message.LANGUAGE_SELECTED),
                            NamedTextColor.GREEN));
        }
        return label;
    }

    private Material languageMaterial(Language language) {
        return switch (language) {
            case ZH_CN -> Material.RED_BANNER;
            case ZH_HK -> Material.MAGENTA_BANNER;
            case ZH_TW -> Material.PINK_BANNER;
            case LZH -> Material.BLACK_BANNER;
            case EN_US -> Material.BLUE_BANNER;
            case DE_DE -> Material.YELLOW_BANNER;
            case ES_ES -> Material.ORANGE_BANNER;
            case FR_FR -> Material.WHITE_BANNER;
            case IT_IT -> Material.LIME_BANNER;
            case JA_JP -> Material.RED_BANNER;
            case KO_KR -> Material.LIGHT_BLUE_BANNER;
            case NL_NL -> Material.ORANGE_BANNER;
            case PT_BR -> Material.GREEN_BANNER;
            case RU_RU -> Material.CYAN_BANNER;
            case TH_TH -> Material.PURPLE_BANNER;
            case UK_UA -> Material.YELLOW_BANNER;
        };
    }

    private String languageLabel(Player player, String preference) {
        Language language = language(player);
        if (AUTO.equals(preference)) {
            return t(language, Message.MAIN_LANGUAGE_AUTO);
        }
        return Language.fromId(preference)
                .map(Language::displayName)
                .orElseGet(() -> t(language, Message.MAIN_LANGUAGE_AUTO));
    }

    public void setPreference(UUID playerId, String value) {
        Player player = Bukkit.getPlayer(playerId);
        Language previousLanguage = player == null ? null : language(player);
        String normalized = normalizePreference(value);
        preferences.put(playerId, normalized);
        languageData.set(playerId.toString() + ".language", normalized);
        try {
            languageData.save(languageFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save language preference for " + playerId + ": " + e.getMessage());
        }
        if (player != null && previousLanguage != language(player)) {
            notifyLanguageChanged(player);
        }
    }

    public void addLanguageChangeListener(LanguageChangeListener listener) {
        languageChangeListeners.add(listener);
    }

    public void removeLanguageChangeListener(LanguageChangeListener listener) {
        languageChangeListeners.remove(listener);
    }

    private void notifyLanguageChanged(Player player) {
        for (LanguageChangeListener listener : languageChangeListeners) {
            listener.onLanguageChanged(player);
        }
    }

    private String loadPreference(UUID playerId) {
        return normalizePreference(languageData.getString(playerId.toString() + ".language", AUTO));
    }

    private String normalizePreference(String value) {
        if (value == null || AUTO.equalsIgnoreCase(value)) {
            return AUTO;
        }
        return Language.fromId(value).map(Language::id).orElse(AUTO);
    }

    private void loadBundles() {
        ResourceParser parser = ResourceParserBuilder.defaultParser();
        for (Language language : Language.values()) {
            String resourcePath = "lang/" + language.id() + ".ftl";
            try (InputStream input = plugin.getResource(resourcePath)) {
                if (input == null) {
                    plugin.getLogger().warning("Missing language resource: " + resourcePath);
                    continue;
                }
                String source = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                FluentResource resource = parser.parse(FluentIteratorFactory.fromString(source));
                FluentBundle bundle = FluentBundleBuilder.builder(language.locale())
                        .addResource(resource)
                        .addDefaultFunctions()
                        .build();
                bundles.put(language, bundle);
            } catch (IOException | RuntimeException e) {
                plugin.getLogger().severe("Failed to load language resource " + resourcePath + ": " + e.getMessage());
            }
        }
        if (!bundles.containsKey(Language.DEFAULT)) {
            throw new IllegalStateException("Default language bundle is not available: " + Language.DEFAULT.id());
        }
    }

    private ArgumentList arguments(Object... args) {
        ArgumentList.Builder builder = ArgumentListBuilder.builder();
        for (int i = 0; i < args.length; i++) {
            String name = "arg" + i;
            addArgument(builder, name, args[i]);
        }
        return builder.build();
    }

    private ArgumentList namedArguments(TextArg... args) {
        ArgumentList.Builder builder = ArgumentListBuilder.builder();
        for (TextArg arg : args) {
            addArgument(builder, arg.name(), arg.value());
        }
        return builder.build();
    }

    private void addArgument(ArgumentList.Builder builder, String name, Object value) {
        if (value instanceof Integer integer) {
            builder.add(name, integer.longValue());
        } else if (value instanceof Long longValue) {
            builder.add(name, longValue);
        } else if (value instanceof Float floatValue) {
            builder.add(name, floatValue.doubleValue());
        } else if (value instanceof Double doubleValue) {
            builder.add(name, doubleValue);
        } else {
            builder.add(name, String.valueOf(value));
        }
    }

    private String resolve(Language language, Message message, ArgumentList arguments) {
        FluentBundle bundle = bundles.getOrDefault(language, bundles.get(Language.DEFAULT));
        return bundle.resolveMessage(message.key(), arguments, StringResultFactory.construct())
                .map(Object::toString)
                .orElseGet(() -> fallbackText(message));
    }

    private String resolve(Language language, Message message, ArgumentList arguments, RichArg... args) {
        FluentBundle bundle = bundles.getOrDefault(language, bundles.get(Language.DEFAULT));
        return bundle.resolveMessage(message.key(), arguments, StringResultFactory.construct())
                .map(Object::toString)
                .orElseGet(() -> fallbackText(message, (Object[]) args));
    }

    private Component replaceRichTokens(String rendered, NamedTextColor baseColor, Map<String, RichArg> tokens) {
        Component text = Component.empty();
        int index = 0;
        while (index < rendered.length()) {
            String nextToken = null;
            int nextIndex = -1;
            for (String token : tokens.keySet()) {
                int found = rendered.indexOf(token, index);
                if (found >= 0 && (nextIndex < 0 || found < nextIndex)) {
                    nextIndex = found;
                    nextToken = token;
                }
            }
            if (nextToken == null) {
                text = text.append(Component.text(rendered.substring(index), baseColor));
                break;
            }
            if (nextIndex > index) {
                text = text.append(Component.text(rendered.substring(index, nextIndex), baseColor));
            }
            text = text.append(tokens.get(nextToken).component());
            index = nextIndex + nextToken.length();
        }
        return text;
    }

    private String fallbackText(Message message, Object... args) {
        StringBuilder builder = new StringBuilder(message.key());
        for (Object arg : args) {
            builder.append(' ').append(arg);
        }
        return builder.toString();
    }

    private String resolveDefault(Message message, ArgumentList arguments, Object... args) {
        FluentBundle fallback = bundles.get(Language.DEFAULT);
        if (fallback == null) {
            return fallbackText(message, args);
        }
        return fallback.resolveMessage(message.key(), arguments, StringResultFactory.construct())
                .map(Object::toString)
                .orElseGet(() -> fallbackText(message, args));
    }

    private boolean isLanguageMenuTitle(String title) {
        return titleMatches(Message.LANGUAGE_MENU_TITLE, title);
    }
}
