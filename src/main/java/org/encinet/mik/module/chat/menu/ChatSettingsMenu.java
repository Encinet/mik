package org.encinet.mik.module.chat.menu;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.encinet.mik.module.chat.ChatDelayOption;
import org.encinet.mik.module.chat.ChatMentionSetting;
import org.encinet.mik.module.chat.ChatSettingsStore;
import org.encinet.mik.module.chat.mention.MentionService;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.menu.FloatingMenuDefinition;
import org.encinet.mik.module.menu.FloatingMenuLayouts;
import org.encinet.mik.module.menu.FloatingMenus;

import java.util.List;

public final class ChatSettingsMenu {

    private final LanguageService languageService;
    private final MentionService mentionService;
    private final ChatSettingsStore settingsStore;

    public ChatSettingsMenu(LanguageService languageService, MentionService mentionService,
                            ChatSettingsStore settingsStore) {
        this.languageService = languageService;
        this.mentionService = mentionService;
        this.settingsStore = settingsStore;
    }

    public void open(Player player) {
        ChatSettingsStore.ChatSettings settings = settingsStore.get(player.getUniqueId());
        FloatingMenuDefinition.Builder menu = FloatingMenuDefinition.screen("chat-settings")
                .layout(FloatingMenuLayouts.menu(
                        FloatingMenuLayouts.heading("mentions-heading"),
                        FloatingMenuLayouts.actions("mentions", 4),
                        FloatingMenuLayouts.heading("delay-heading"),
                        FloatingMenuLayouts.actions("delay", 4),
                        FloatingMenuLayouts.navigation("navigation")));
        menu.information("mentions-heading",
                        Component.text(languageService.t(player, Message.MENTION_MENU_TITLE),
                                NamedTextColor.GOLD))
                .region("mentions-heading");
        menu.information("delay-heading",
                        Component.text(languageService.t(player, Message.CHAT_DELAY_CURRENT,
                                        languageService.t(player, delayMessage(settings.delay()))),
                                NamedTextColor.GOLD))
                .region("delay-heading");
        for (ChatMentionSetting setting : ChatMentionSetting.values()) {
            boolean enabled = mentionEnabled(settings, setting);
            MentionSettingView view = MentionSettingView.of(setting);
            menu.toggle("mention:" + setting.name().toLowerCase(java.util.Locale.ROOT),
                            enabled, view.enabledMaterial(), view.disabledMaterial(),
                            toggleLabel(player, view.label(), enabled))
                    .region("mentions")
                    .primary((p, handle) -> {
                        settingsStore.toggleMention(p.getUniqueId(), setting);
                        open(p);
                    });
        }
        ChatDelayOption[] delays = ChatDelayOption.values();
        Message[] labels = {Message.CHAT_DELAY_OFF, Message.CHAT_DELAY_3S,
                Message.CHAT_DELAY_5S, Message.CHAT_DELAY_7S};
        Material[] materials = {Material.GRAY_DYE, Material.LIME_DYE, Material.YELLOW_DYE, Material.ORANGE_DYE};
        for (int i = 0; i < delays.length; i++) {
            ChatDelayOption delay = delays[i];
            boolean selected = settings.delay() == delay;
            menu.choice("delay:" + delay.id(), selected, materials[i],
                            Component.text(languageService.t(player, labels[i]),
                                    selected ? NamedTextColor.GREEN : NamedTextColor.GRAY))
                    .region("delay")
                    .primary((p, handle) -> {
                        settingsStore.setDelay(p.getUniqueId(), delay);
                        open(p);
                    });
        }
        menu.back(
                Component.text(languageService.t(player, Message.BACK_TO_MAIN),
                                NamedTextColor.GREEN))
                .region("navigation");
        FloatingMenus.present(player, menu.build());
    }

    public List<Component> summary(Player player) {
        return List.of(
                Component.text(mentionService.summary(player), NamedTextColor.GRAY),
                Component.text(languageService.t(player, Message.CHAT_DELAY_CURRENT,
                        languageService.t(player, delayMessage(
                                settingsStore.get(player.getUniqueId()).delay()))), NamedTextColor.GRAY)
        );
    }

    private Message delayMessage(ChatDelayOption delay) {
        return switch (delay) {
            case OFF -> Message.CHAT_DELAY_OFF;
            case THREE_SECONDS -> Message.CHAT_DELAY_3S;
            case FIVE_SECONDS -> Message.CHAT_DELAY_5S;
            case SEVEN_SECONDS -> Message.CHAT_DELAY_7S;
        };
    }

    private Component toggleLabel(Player player, Message label, boolean enabled) {
        return Component.text(languageService.t(player, label),
                        enabled ? NamedTextColor.GREEN : NamedTextColor.GRAY)
                .append(Component.newline())
                .append(Component.text(languageService.t(player,
                                enabled ? Message.CURRENT_ON : Message.CURRENT_OFF),
                        enabled ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY));
    }

    private boolean mentionEnabled(ChatSettingsStore.ChatSettings settings, ChatMentionSetting setting) {
        return switch (setting) {
            case ALERTS -> settings.mentionAlerts();
            case SOUND -> settings.mentionSound();
            case ACTION_BAR -> settings.mentionActionBar();
            case MUTE_WHILE_AFK -> settings.mentionMuteWhileAfk();
        };
    }

    private record MentionSettingView(Message label,
                                      Material enabledMaterial, Material disabledMaterial) {
        static MentionSettingView of(ChatMentionSetting setting) {
            return switch (setting) {
                case ALERTS -> new MentionSettingView(Message.MENTION_ALERTS,
                        Material.LIME_DYE, Material.GRAY_DYE);
                case SOUND -> new MentionSettingView(Message.MENTION_SOUND,
                        Material.NOTE_BLOCK, Material.GRAY_DYE);
                case ACTION_BAR -> new MentionSettingView(Message.MENTION_ACTION_BAR,
                        Material.PAPER, Material.GRAY_DYE);
                case MUTE_WHILE_AFK -> new MentionSettingView(Message.MENTION_MUTE_AFK,
                        Material.CLOCK, Material.GRAY_DYE);
            };
        }
    }
}
