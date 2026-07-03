package org.encinet.mik.module.chat.menu;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.module.chat.ChatDelayOption;
import org.encinet.mik.module.chat.ChatMentionSetting;
import org.encinet.mik.module.chat.ChatSettingsStore;
import org.encinet.mik.module.chat.mention.MentionService;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.menu.MenuBuilder;
import org.encinet.mik.module.menu.MenuItems;
import org.encinet.mik.module.menu.MenuNavigation;

import java.util.ArrayList;
import java.util.List;

public final class ChatSettingsMenu {

    private static final int SETTINGS_MENU_SIZE = 18;
    private static final String ACTION_BACK_MAIN = "back:main";
    private static final String ACTION_MENTION_PREFIX = "mention:";
    private static final String ACTION_DELAY_PREFIX = "delay:";

    private final LanguageService languageService;
    private final MentionService mentionService;
    private final ChatSettingsStore settingsStore;
    private final MenuNavigation menuNavigation;
    private final NamespacedKey settingsActionKey;

    public ChatSettingsMenu(JavaPlugin plugin, LanguageService languageService, MentionService mentionService,
                            ChatSettingsStore settingsStore, MenuNavigation menuNavigation) {
        this.languageService = languageService;
        this.mentionService = mentionService;
        this.settingsStore = settingsStore;
        this.menuNavigation = menuNavigation;
        this.settingsActionKey = new NamespacedKey(plugin, "chat_settings_action");
    }

    public void open(Player player) {
        ChatSettingsStore.ChatSettings settings = settingsStore.get(player.getUniqueId());
        MenuBuilder.create(SETTINGS_MENU_SIZE, Component.text(languageService.t(player, Message.CHAT_SETTINGS_MENU_TITLE), MenuItems.TITLE_COLOR))
                .item(0, sectionItem(player, Material.BELL, Message.MENTION_MENU_TITLE))
                .item(2, mentionToggleItem(player, settings, ChatMentionSetting.ALERTS))
                .item(3, mentionToggleItem(player, settings, ChatMentionSetting.SOUND))
                .item(4, mentionToggleItem(player, settings, ChatMentionSetting.ACTION_BAR))
                .item(5, mentionToggleItem(player, settings, ChatMentionSetting.MUTE_WHILE_AFK))
                .item(9, sectionItem(player, Material.CLOCK, Message.CHAT_DELAY_SECTION))
                .item(11, delayItem(player, settings, ChatDelayOption.OFF, Material.GRAY_DYE, Message.CHAT_DELAY_OFF))
                .item(12, delayItem(player, settings, ChatDelayOption.THREE_SECONDS, Material.LIME_DYE, Message.CHAT_DELAY_3S))
                .item(13, delayItem(player, settings, ChatDelayOption.FIVE_SECONDS, Material.YELLOW_DYE, Message.CHAT_DELAY_5S))
                .item(14, delayItem(player, settings, ChatDelayOption.SEVEN_SECONDS, Material.ORANGE_DYE, Message.CHAT_DELAY_7S))
                .item(17, backToMainItem(player))
                .open(player);
    }

    public boolean handleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return false;

        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if (!isSettingsMenuTitle(title)) return false;

        event.setCancelled(true);
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return true;

        String action = MenuItems.readAction(item, settingsActionKey);
        if (action == null) return true;

        if (ACTION_BACK_MAIN.equals(action)) {
            menuNavigation.openMainMenu(player);
            return true;
        }
        if (action.startsWith(ACTION_MENTION_PREFIX)) {
            ChatMentionSetting setting = ChatMentionSetting.fromId(action.substring(ACTION_MENTION_PREFIX.length()));
            if (setting != null) {
                settingsStore.toggleMention(player.getUniqueId(), setting);
                open(player);
            }
            return true;
        }
        if (action.startsWith(ACTION_DELAY_PREFIX)) {
            ChatDelayOption delay = ChatDelayOption.fromInput(action.substring(ACTION_DELAY_PREFIX.length()));
            if (delay != null) {
                settingsStore.setDelay(player.getUniqueId(), delay);
                open(player);
            }
        }
        return true;
    }

    public List<Component> summary(Player player) {
        return List.of(
                Component.text(mentionService.summary(player), NamedTextColor.GRAY),
                Component.text(languageService.t(player, Message.CHAT_DELAY_CURRENT,
                        settingsStore.get(player.getUniqueId()).delay().id()), NamedTextColor.GRAY)
        );
    }

    private ItemStack sectionItem(Player player, Material material, Message title) {
        return MenuItems.item(material,
                Component.text(languageService.t(player, title), NamedTextColor.GOLD),
                List.of());
    }

    private ItemStack mentionToggleItem(Player player, ChatSettingsStore.ChatSettings settings, ChatMentionSetting setting) {
        boolean enabled = mentionEnabled(settings, setting);
        MentionSettingView view = MentionSettingView.of(setting);
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(languageService.t(player, enabled ? Message.CURRENT_ON : Message.CURRENT_OFF),
                enabled ? NamedTextColor.GREEN : NamedTextColor.GRAY));
        lore.add(Component.text(languageService.t(player, view.description()), NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(Component.text(languageService.t(player, Message.CLICK_SWITCH), NamedTextColor.YELLOW));
        return MenuItems.action(enabled ? view.enabledMaterial() : view.disabledMaterial(),
                Component.text(languageService.t(player, view.label()), enabled ? NamedTextColor.GREEN : NamedTextColor.GRAY),
                lore, settingsActionKey, ACTION_MENTION_PREFIX + setting.id());
    }

    private boolean mentionEnabled(ChatSettingsStore.ChatSettings settings, ChatMentionSetting setting) {
        return switch (setting) {
            case ALERTS -> settings.mentionAlerts();
            case SOUND -> settings.mentionSound();
            case ACTION_BAR -> settings.mentionActionBar();
            case MUTE_WHILE_AFK -> settings.mentionMuteWhileAfk();
        };
    }

    private ItemStack delayItem(Player player, ChatSettingsStore.ChatSettings settings, ChatDelayOption option,
                                Material material, Message label) {
        boolean selected = settings.delay() == option;
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(languageService.t(player, selected ? Message.CURRENT_ON : Message.CURRENT_OFF),
                selected ? NamedTextColor.GREEN : NamedTextColor.GRAY));
        lore.add(Component.text(languageService.t(player, Message.CHAT_DELAY_OPTION_DESC), NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(Component.text(languageService.t(player, Message.CLICK_SET), NamedTextColor.YELLOW));
        return MenuItems.action(material,
                Component.text(languageService.t(player, label), selected ? NamedTextColor.GREEN : NamedTextColor.GRAY),
                lore, settingsActionKey, ACTION_DELAY_PREFIX + option.id());
    }

    private ItemStack backToMainItem(Player player) {
        return MenuItems.action(Material.ARROW,
                Component.text(languageService.t(player, Message.BACK_TO_MAIN), NamedTextColor.GREEN),
                List.of(Component.text(languageService.t(player, Message.BACK_TO_MAIN_LORE), NamedTextColor.GRAY)),
                settingsActionKey, ACTION_BACK_MAIN);
    }

    private boolean isSettingsMenuTitle(String title) {
        for (Language language : Language.values()) {
            if (languageService.t(language, Message.CHAT_SETTINGS_MENU_TITLE).equals(title)) {
                return true;
            }
        }
        return false;
    }

    private record MentionSettingView(Message label, Message description,
                                      Material enabledMaterial, Material disabledMaterial) {
        static MentionSettingView of(ChatMentionSetting setting) {
            return switch (setting) {
                case ALERTS -> new MentionSettingView(Message.MENTION_ALERTS, Message.MENTION_ALERTS_DESC,
                        Material.LIME_DYE, Material.GRAY_DYE);
                case SOUND -> new MentionSettingView(Message.MENTION_SOUND, Message.MENTION_SOUND_DESC,
                        Material.NOTE_BLOCK, Material.GRAY_DYE);
                case ACTION_BAR -> new MentionSettingView(Message.MENTION_ACTION_BAR, Message.MENTION_ACTION_BAR_DESC,
                        Material.PAPER, Material.GRAY_DYE);
                case MUTE_WHILE_AFK -> new MentionSettingView(Message.MENTION_MUTE_AFK, Message.MENTION_MUTE_AFK_DESC,
                        Material.CLOCK, Material.GRAY_DYE);
            };
        }
    }
}
