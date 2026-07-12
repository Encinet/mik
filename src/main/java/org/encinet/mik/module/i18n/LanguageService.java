package org.encinet.mik.module.i18n;

import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.quickwrite.fluent4j.ast.pattern.ArgumentList;
import net.quickwrite.fluent4j.container.ArgumentListBuilder;
import net.quickwrite.fluent4j.container.FluentBundle;
import net.quickwrite.fluent4j.container.FluentBundleBuilder;
import net.quickwrite.fluent4j.container.FluentResource;
import net.quickwrite.fluent4j.iterator.FluentIteratorFactory;
import net.quickwrite.fluent4j.parser.ResourceParser;
import net.quickwrite.fluent4j.parser.ResourceParserBuilder;
import net.quickwrite.fluent4j.result.StringResultFactory;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLocaleChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.module.menu.MenuBuilder;
import org.encinet.mik.module.menu.MenuItems;
import org.encinet.mik.module.menu.MenuNavigation;
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
import java.util.concurrent.ConcurrentHashMap;

public class LanguageService implements Listener {

    public static final String AUTO = "auto";

    private static final int MENU_SIZE = 18;
    private static final String ACTION_BACK_MAIN = "back:main";
    private static final String ACTION_LANGUAGE_PREFIX = "language:";

    private final JavaPlugin plugin;
    private final MenuNavigation menuNavigation;
    private final NamespacedKey actionKey;
    private final Map<UUID, String> preferences = new ConcurrentHashMap<>();
    private final Map<UUID, Language> clientLanguages = new ConcurrentHashMap<>();
    private final Map<Language, FluentBundle> bundles = new EnumMap<>(Language.class);

    private File languageFile;
    private YamlConfiguration languageData;

    public LanguageService(JavaPlugin plugin, MenuNavigation menuNavigation) {
        this.plugin = plugin;
        this.menuNavigation = menuNavigation;
        this.actionKey = new NamespacedKey(plugin, "language_action");
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

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if (!isLanguageMenuTitle(title)) return;

        event.setCancelled(true);
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;

        String action = MenuItems.readAction(item, actionKey);
        if (action == null) return;

        if (ACTION_BACK_MAIN.equals(action)) {
            menuNavigation.openMainMenu(player);
            return;
        }

        if (!action.startsWith(ACTION_LANGUAGE_PREFIX)) return;
        String value = action.substring(ACTION_LANGUAGE_PREFIX.length()).toLowerCase(Locale.ROOT);
        if (!AUTO.equals(value) && Language.fromId(value).isEmpty()) return;

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
        rememberClientLanguage(event.getPlayer().getUniqueId(), event.locale());
    }

    public void openMenu(Player player) {
        Language language = language(player);
        MenuBuilder builder = MenuBuilder.create(MENU_SIZE, Component.text(t(language, Message.LANGUAGE_MENU_TITLE), MenuItems.TITLE_COLOR))
                .item(0, preferenceItem(player, AUTO, Material.COMPASS));
        int slot = 2;
        for (Language option : Language.values()) {
            builder.item(slot++, preferenceItem(player, option.id(), languageMaterial(option)));
        }
        builder.item(17, MenuItems.action(Material.ARROW,
                Component.text(t(language, Message.BACK_TO_MAIN), NamedTextColor.GREEN),
                List.of(Component.text(t(language, Message.BACK_TO_MAIN_LORE), NamedTextColor.GRAY)),
                actionKey, ACTION_BACK_MAIN)).open(player);
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

    private void rememberClientLanguage(UUID playerId, Locale locale) {
        Language.fromLocale(locale).ifPresent(language -> {
            Language previous = clientLanguages.put(playerId, language);
            if (previous == language && language.id().equals(languageData.getString(playerId + ".client-language"))) {
                return;
            }
            languageData.set(playerId + ".client-language", language.id());
            try {
                languageData.save(languageFile);
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to save client language for " + playerId + ": " + e.getMessage());
            }
        });
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
                .orElseGet(() -> fallbackText(message, args));
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

    private ItemStack preferenceItem(Player player, String value, Material material) {
        Language language = language(player);
        boolean selected = preference(player.getUniqueId()).equals(value);
        List<Component> lore = new ArrayList<>();
        if (AUTO.equals(value)) {
            lore.add(Component.text(t(language, Message.LANGUAGE_AUTO_LORE), NamedTextColor.GRAY));
        } else {
            lore.add(Component.text(t(language, Message.LANGUAGE_USE, languageLabel(player, value)), NamedTextColor.GRAY));
        }
        lore.add(Component.empty());
        lore.add(Component.text(selected
                ? t(language, Message.LANGUAGE_SELECTED)
                : t(language, Message.CLICK_SET), selected ? NamedTextColor.GREEN : NamedTextColor.YELLOW));

        return MenuItems.action(material,
                Component.text(languageLabel(player, value), selected ? NamedTextColor.GREEN : NamedTextColor.AQUA),
                lore, actionKey, ACTION_LANGUAGE_PREFIX + value);
    }

    private Material languageMaterial(Language language) {
        return switch (language) {
            case ZH_CN -> Material.RED_BANNER;
            case ZH_HK -> Material.MAGENTA_BANNER;
            case ZH_TW -> Material.PINK_BANNER;
            case LZH -> Material.BLACK_BANNER;
            case EN_US -> Material.BLUE_BANNER;
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
        String normalized = normalizePreference(value);
        preferences.put(playerId, normalized);
        languageData.set(playerId.toString() + ".language", normalized);
        try {
            languageData.save(languageFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save language preference for " + playerId + ": " + e.getMessage());
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

    private boolean isLanguageMenuTitle(String title) {
        return titleMatches(Message.LANGUAGE_MENU_TITLE, title);
    }
}
