package org.encinet.mik.feature.community.chat.menu;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.encinet.mik.feature.community.chat.settings.ChatDelayOption;
import org.encinet.mik.feature.community.chat.settings.ChatMentionSetting;
import org.encinet.mik.feature.community.chat.settings.ChatSettingsStore;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.menu.ui.*;
import org.encinet.mik.shared.menu.*;
import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;

public final class ChatSettingsMenu {

    private final LanguageService languageService;
    private final ChatSettingsStore settingsStore;

    public ChatSettingsMenu(LanguageService languageService, ChatSettingsStore settingsStore) {
        this.languageService = languageService;
        this.settingsStore = settingsStore;
    }

    private record Browse(String group, int page) {
        private static final Browse INITIAL = new Browse("mentions", 0);
        Browse choose(String next) { return next.equals(group) ? this : new Browse(next, 0); }
    }
    private final FloatingMenuScreen<Browse> screen = new FloatingMenuScreen<>("chat-settings", this::render);

    public void open(Player player) { screen.open(player, Browse.INITIAL); }

    private MenuScene render(FloatingMenuContext<Browse> context) {
        var scene = definition(context.player(), context.state(), context::setState, () -> screen.state(context.player()).orElse(null) == context.state(), context::redraw);
        return scene.toBuilder().content(UiTemplates.withBack(scene.content(),
                Component.text(languageService.t(context.player(), Message.BACK), NamedTextColor.GREEN))).build();
    }

    public MenuPanel panel(Player player, Runnable redraw) {
        return new MenuPanel() {
            private Browse browse = Browse.INITIAL;
            private boolean closed;
            @Override public MenuScene render() {
                if (closed) return MenuScene.screen("chat-settings-closed").content(Ui.column().build()).build();
                Browse snapshot = browse;
                return definition(player, snapshot, next -> { if (!closed) { browse = next; redraw.run(); } },
                        () -> !closed && browse == snapshot, () -> { if (!closed) redraw.run(); });
            }
            @Override public void close() { closed = true; }
        };
    }

    private MenuScene definition(Player player, Browse browse, Consumer<Browse> navigate, BooleanSupplier active, Runnable redraw) {
        var settings = settingsStore.get(player.getUniqueId());
        var options = new ArrayList<UiNode>();
        if (browse.group().equals("mentions")) {
            for (var setting : ChatMentionSetting.values()) {
                boolean enabled = mentionEnabled(settings, setting);
                options.add(Ui.button(toggleLabel(player, mentionMessage(setting), enabled))
                        .key("mention:" + setting.name().toLowerCase(java.util.Locale.ROOT))
                        .role(UiRole.CHOICE).summaryLines(2).selected(enabled)
                        .primary((viewer, _) -> {
                            if (!active.getAsBoolean()) return;
                            settingsStore.toggleMention(viewer.getUniqueId(), setting); redraw.run();
                        }).build());
            }
        } else {
            for (var delay : ChatDelayOption.values()) {
                boolean selected = settings.delay() == delay;
                options.add(Ui.button(Component.text(languageService.t(player, delayMessage(delay)),
                                selected ? NamedTextColor.GREEN : NamedTextColor.WHITE))
                        .key("delay:" + delay.id()).role(UiRole.CHOICE).summaryLines(1).selected(selected)
                        .primary((viewer, _) -> {
                            if (!active.getAsBoolean()) return;
                            settingsStore.setDelay(viewer.getUniqueId(), delay); redraw.run();
                        }).build());
            }
        }
        var choices = Ui.pagedList().key("options").items(options,LinearSlot.stretch())
                .page(browse.page(), index -> { if (active.getAsBoolean()) navigate.accept(new Browse(browse.group(), index)); }).build();
        var form = Ui.form().key("chat-groups").navigationAxis(UiLayouts.Axis.VERTICAL)
                .field("mentions", Component.text(languageService.t(player, Message.MENTION_MENU_TITLE), NamedTextColor.GOLD),
                        browse.group().equals("mentions") ? choices : Ui.column().build())
                .field("delays", Component.text(languageService.t(player, Message.CHAT_DELAY_CURRENT,
                                languageService.t(player, delayMessage(settings.delay()))), NamedTextColor.AQUA),
                        browse.group().equals("delays") ? choices : Ui.column().build())
                .viewedField(browse.group(), group -> { if (active.getAsBoolean()) navigate.accept(browse.choose(group)); }).build();
        var body = UiTemplates.reading(form.key(),Ui.heading(Component.text(languageService.t(player, Message.CHAT_SETTINGS_MENU_TITLE),
                        NamedTextColor.LIGHT_PURPLE)).key("heading").summaryLines(1).build(),form);
        return MenuScene.screen("chat-settings").content(UiTemplates.window("settings", body)).stableAnchor().build();
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
                        enabled ? NamedTextColor.GREEN : NamedTextColor.WHITE)
                .append(Component.newline())
                .append(Component.text(languageService.t(player,
                                enabled ? Message.CURRENT_ON : Message.CURRENT_OFF),
                        enabled ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
    }

    private boolean mentionEnabled(ChatSettingsStore.ChatSettings settings, ChatMentionSetting setting) {
        return switch (setting) {
            case ALERTS -> settings.mentionAlerts();
            case SOUND -> settings.mentionSound();
            case ACTION_BAR -> settings.mentionActionBar();
            case MUTE_WHILE_AFK -> settings.mentionMuteWhileAfk();
        };
    }

    private static Message mentionMessage(ChatMentionSetting setting) {
        return switch (setting) {
            case ALERTS -> Message.MENTION_ALERTS;
            case SOUND -> Message.MENTION_SOUND;
            case ACTION_BAR -> Message.MENTION_ACTION_BAR;
            case MUTE_WHILE_AFK -> Message.MENTION_MUTE_AFK;
        };
    }
}
