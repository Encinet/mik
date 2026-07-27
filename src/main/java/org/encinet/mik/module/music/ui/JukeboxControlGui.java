package org.encinet.mik.module.music.ui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Jukebox;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.music.catalog.MusicTrack;
import org.encinet.mik.module.music.disc.MusicDiscFactory;
import org.encinet.mik.module.music.disc.MusicDiscKeys;
import org.encinet.mik.module.music.disc.MusicDiscResolver;
import org.encinet.mik.module.music.jukebox.JukeboxQueueService;
import org.encinet.mik.module.music.jukebox.JukeboxPlaybackMode;
import org.encinet.mik.module.music.jukebox.JukeboxPlaybackStatus;
import org.encinet.mik.module.music.jukebox.JukeboxSettingsStore;
import org.encinet.mik.module.music.jukebox.JukeboxSoundSettings;
import org.encinet.mik.module.music.jukebox.PlaybackStatus;

import java.util.ArrayList;
import java.util.List;

/** Renders the main-thread-confined jukebox queue and playback controls. */
public final class JukeboxControlGui {

    private static final int GUI_SIZE = 54;
    private static final int CURRENT_PLAYING_SLOT = 4;
    public static final int PREVIOUS_PAGE_SLOT = 36;
    public static final int VOLUME_SLOT = 38;
    public static final int RANGE_SLOT = 39;
    public static final int NEXT_PAGE_SLOT = 44;
    public static final int STOP_EJECT_SLOT = 50;
    private static final int[] QUEUE_SLOTS = {
            9, 10, 11, 12, 13, 14, 15, 16, 17,
            18, 19, 20, 21, 22, 23, 24, 25, 26,
            27, 28, 29, 30, 31, 32, 33, 34, 35
    };

    private final JukeboxQueueService queueService;
    private final MusicDiscFactory discFactory;
    private final MusicDiscResolver discResolver;
    private final JukeboxPlaybackStatus playbackStatus;
    private final JukeboxSettingsStore settingsStore;
    private final LanguageService languageService;

    public JukeboxControlGui(JukeboxQueueService queueService, MusicDiscFactory discFactory,
                             MusicDiscResolver discResolver,
                             JukeboxPlaybackStatus playbackStatus,
                             JukeboxSettingsStore settingsStore,
                             LanguageService languageService) {
        this.queueService = queueService;
        this.discFactory = discFactory;
        this.discResolver = discResolver;
        this.playbackStatus = playbackStatus;
        this.settingsStore = settingsStore;
        this.languageService = languageService;
    }

    public void openJukeboxControl(Player player, Jukebox jukebox) {
        openJukeboxControlPage(player, jukebox, 0);
    }

    public void openJukeboxControlPage(Player player, Jukebox jukebox, int page) {
        Inventory inventory = createJukeboxControlInventory(player, jukebox, page);
        player.openInventory(inventory);
    }

    public Location getJukebox(Inventory inventory) {
        return inventory != null && inventory.getHolder() instanceof JukeboxControlHolder holder
                ? holder.location.clone() : null;
    }

    public int getPage(Inventory inventory) {
        return inventory != null && inventory.getHolder() instanceof JukeboxControlHolder holder
                ? holder.page : 0;
    }

    public MusicTrack queueTrackAt(Inventory inventory, int rawSlot) {
        Location location = getJukebox(inventory);
        if (location == null || queueSlotIndex(rawSlot) < 0) {
            return null;
        }
        ItemStack clickedItem = inventory.getItem(rawSlot);
        String trackId = MusicDiscKeys.trackId(clickedItem);
        if (trackId == null) {
            return null;
        }
        JukeboxQueueService.JukeboxState data = queueService.findState(location);
        if (data == null) {
            return null;
        }
        List<MusicTrack> queue = data.queue();
        int index = getPage(inventory) * QUEUE_SLOTS.length + queueSlotIndex(rawSlot);
        if (index < 0 || index >= queue.size()) {
            return null;
        }
        MusicTrack track = queue.get(index);
        return track.id().equals(trackId) ? track : null;
    }

    public boolean isQueueSlot(int slot) {
        for (int queueSlot : QUEUE_SLOTS) {
            if (queueSlot == slot) {
                return true;
            }
        }
        return false;
    }

    public boolean isJukeboxControlInventory(Inventory inventory) {
        return inventory != null && inventory.getHolder() instanceof JukeboxControlHolder;
    }

    private Inventory createJukeboxControlInventory(Player player, Jukebox jukebox, int requestedPage) {
        Location location = jukebox.getLocation();
        JukeboxQueueService.JukeboxState data = queueService.state(location);
        List<MusicTrack> queue = data.queue();
        int totalPages = pageCount(queue.size());
        int page = Math.max(0, Math.min(requestedPage, totalPages - 1));
        String title = languageService.t(player, Message.MUSIC_JUKEBOX_TITLE,
                location.getBlockX(), location.getBlockY(), location.getBlockZ());

        JukeboxControlHolder holder = new JukeboxControlHolder(location, page);
        Inventory inv = Bukkit.createInventory(holder, GUI_SIZE,
                Component.text(title).color(NamedTextColor.DARK_PURPLE));
        holder.attach(inv);

        MusicTrack currentDisc = discResolver.resolve(jukebox.getRecord());
        if (currentDisc != null) {
            PlaybackStatus status = playbackStatus.status(jukebox.getBlock());
            ItemStack discItem = discFactory.createDisplayDisc(currentDisc, false, player);
            ItemMeta meta = discItem.getItemMeta();
            if (meta != null) {
                meta.displayName(Component.text(truncate(currentDisc.details().title(), 64))
                        .color(statusColor(status))
                        .decoration(TextDecoration.ITALIC, false)
                        .decoration(TextDecoration.BOLD, true));
                List<Component> lore = meta.lore() == null
                        ? new ArrayList<>() : new ArrayList<>(meta.lore());
                lore.addFirst(Component.text(languageService.t(player, Message.MUSIC_PLAYBACK_STATUS,
                                languageService.t(player, statusMessage(status))))
                        .color(statusColor(status))
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(1, Component.empty());
                meta.lore(lore);
                discItem.setItemMeta(meta);
            }
            inv.setItem(CURRENT_PLAYING_SLOT, discItem);
        } else if (jukebox.hasRecord() && !MusicDiscKeys.isCustomDisc(jukebox.getRecord())) {
            inv.setItem(CURRENT_PLAYING_SLOT, createVanillaRecordItem(player, jukebox));
        } else {
            boolean hasRecord = jukebox.hasRecord();
            inv.setItem(CURRENT_PLAYING_SLOT, createInfoItem(player, Material.BARRIER,
                    hasRecord ? Message.MUSIC_UNAVAILABLE_DISC : Message.MUSIC_CURRENT_PLAYING,
                    hasRecord ? Message.MUSIC_UNAVAILABLE_DISC_LORE : Message.MUSIC_NO_DISC));
        }

        inv.setItem(8, createMusicSelectionButton(player));

        int start = page * QUEUE_SLOTS.length;
        int end = Math.min(start + QUEUE_SLOTS.length, queue.size());
        for (int index = start; index < end; index++) {
            MusicTrack music = queue.get(index);
            ItemStack discItem = createQueueDiscItem(player, music, index + 1);
            inv.setItem(QUEUE_SLOTS[index - start], discItem);
        }
        if (queue.isEmpty()) {
            inv.setItem(22, createInfoItem(player, Material.GRAY_DYE,
                    Message.MUSIC_QUEUE_EMPTY, Message.MUSIC_QUEUE_EMPTY_LORE));
        }

        JukeboxSoundSettings settings = settingsStore.read(jukebox);
        inv.setItem(VOLUME_SLOT, createVolumeButton(player, settings));
        inv.setItem(RANGE_SLOT, createRangeButton(player, settings));
        inv.setItem(40, createPlayModeButton(player, data.playbackMode()));
        inv.setItem(42, createPlayNextButton(player, data.playbackMode()));
        if (page > 0) {
            inv.setItem(PREVIOUS_PAGE_SLOT, navigationButton(player, true));
        }
        if (page < totalPages - 1) {
            inv.setItem(NEXT_PAGE_SLOT, navigationButton(player, false));
        }

        inv.setItem(46, createAddAllButton(player));
        inv.setItem(48, createClearQueueButton(player));
        inv.setItem(49, createQueueSummary(player, queue.size(), page + 1, totalPages));
        if (jukebox.hasRecord()) {
            inv.setItem(STOP_EJECT_SLOT, createStopEjectButton(player));
        }
        inv.setItem(53, createCloseButton(player));

        return inv;
    }

    private ItemStack createQueueDiscItem(Player player, MusicTrack music, int rank) {
        ItemStack discItem = discFactory.createDisplayDisc(music, false, player);
        ItemMeta meta = discItem.getItemMeta();
        if (meta != null) {
            List<Component> lore = meta.lore() == null
                    ? new ArrayList<>() : new ArrayList<>(meta.lore());
            lore.add(Component.text(""));
            lore.add(Component.text(languageService.t(player, Message.MUSIC_QUEUE_RANK, rank))
                    .color(NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text(""));
            lore.add(Component.text(languageService.t(player, Message.MUSIC_QUEUE_PLAY))
                    .color(NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text(languageService.t(player, Message.MUSIC_QUEUE_REMOVE_ONLY))
                    .color(NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text(languageService.t(player, Message.MUSIC_QUEUE_RANK_UP_ACTION))
                    .color(NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text(languageService.t(player, Message.MUSIC_QUEUE_RANK_DOWN_ACTION))
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            discItem.setItemMeta(meta);
        }
        return discItem;
    }

    private ItemStack createVanillaRecordItem(Player player, Jukebox jukebox) {
        ItemStack record = jukebox.getRecord().asOne();
        ItemMeta meta = record.getItemMeta();
        if (meta != null) {
            List<Component> lore = meta.lore() == null
                    ? new ArrayList<>() : new ArrayList<>(meta.lore());
            PlaybackStatus status = jukebox.isPlaying()
                    ? PlaybackStatus.PLAYING : PlaybackStatus.STOPPED;
            lore.addFirst(Component.text(languageService.t(player, Message.MUSIC_PLAYBACK_STATUS,
                            languageService.t(player, statusMessage(status))))
                    .color(statusColor(status))
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(1, Component.empty());
            meta.lore(lore);
            record.setItemMeta(meta);
        }
        return record;
    }

    private ItemStack createMusicSelectionButton(Player player) {
        ItemStack button = new ItemStack(Material.MUSIC_DISC_WAIT);
        ItemMeta meta = button.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(languageService.t(player, Message.MUSIC_SELECT_MUSIC))
                    .color(NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false)
                    .decoration(TextDecoration.BOLD, true));
            meta.lore(List.of(
                    Component.text(languageService.t(player, Message.MUSIC_SELECT_MUSIC_LORE))
                            .color(NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false)
            ));
            button.setItemMeta(meta);
        }
        return button;
    }

    private ItemStack createPlayModeButton(Player player, JukeboxPlaybackMode mode) {
        ItemStack button = new ItemStack(modeMaterial(mode));
        ItemMeta meta = button.getItemMeta();

        if (meta != null) {
            String modeName = languageService.t(player, modeName(mode));
            meta.displayName(Component.text(modeName)
                    .color(modeColor(mode))
                    .decoration(TextDecoration.ITALIC, false)
                    .decoration(TextDecoration.BOLD, true));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(languageService.t(player, Message.MUSIC_CURRENT_MODE, modeName))
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text(""));

            lore.add(Component.text(languageService.t(player, modeDescription(mode)))
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));

            lore.add(Component.text(""));
            lore.add(Component.text(languageService.t(player, Message.MUSIC_CLICK_SWITCH_MODE))
                    .color(NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));

            meta.lore(lore);
            button.setItemMeta(meta);
        }

        return button;
    }

    private ItemStack createVolumeButton(Player player, JukeboxSoundSettings settings) {
        return createSettingButton(player, Material.NOTE_BLOCK,
                Message.MUSIC_JUKEBOX_VOLUME, Message.MUSIC_JUKEBOX_VOLUME_VALUE,
                settings.volumePercent(),
                JukeboxSoundSettings.VOLUME_COARSE_STEP + "%",
                JukeboxSoundSettings.VOLUME_FINE_STEP + "%");
    }

    private ItemStack createRangeButton(Player player, JukeboxSoundSettings settings) {
        return createSettingButton(player, Material.SPYGLASS,
                Message.MUSIC_JUKEBOX_RANGE, Message.MUSIC_JUKEBOX_RANGE_VALUE,
                settings.rangeBlocks(),
                Integer.toString(JukeboxSoundSettings.RANGE_COARSE_STEP),
                Integer.toString(JukeboxSoundSettings.RANGE_FINE_STEP));
    }

    private ItemStack createSettingButton(Player player, Material material, Message name,
                                          Message valueMessage, int value,
                                          String coarseStep, String fineStep) {
        ItemStack button = new ItemStack(material);
        ItemMeta meta = button.getItemMeta();
        if (meta == null) {
            return button;
        }
        meta.displayName(Component.text(languageService.t(player, name))
                .color(NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));
        meta.lore(List.of(
                Component.text(languageService.t(player, valueMessage, value))
                        .color(NamedTextColor.GOLD)
                        .decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                settingAction(player, Message.MUSIC_SETTING_INCREASE,
                        coarseStep, NamedTextColor.GREEN),
                settingAction(player, Message.MUSIC_SETTING_DECREASE,
                        coarseStep, NamedTextColor.YELLOW),
                settingAction(player, Message.MUSIC_SETTING_FINE_INCREASE,
                        fineStep, NamedTextColor.AQUA),
                settingAction(player, Message.MUSIC_SETTING_FINE_DECREASE,
                        fineStep, NamedTextColor.GRAY),
                Component.empty(),
                Component.text(languageService.t(player, Message.MUSIC_JUKEBOX_SETTINGS_MIK_ONLY))
                        .color(NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        button.setItemMeta(meta);
        return button;
    }

    private Component settingAction(Player player, Message message, String step,
                                    NamedTextColor color) {
        return Component.text(languageService.t(player, message, step))
                .color(color)
                .decoration(TextDecoration.ITALIC, false);
    }

    private ItemStack createPlayNextButton(Player player, JukeboxPlaybackMode mode) {
        boolean shuffle = mode == JukeboxPlaybackMode.SHUFFLE;
        ItemStack button = new ItemStack(shuffle ? Material.ENDER_EYE : Material.ARROW);
        ItemMeta meta = button.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.text(languageService.t(player, shuffle
                            ? Message.MUSIC_PLAY_RANDOM : Message.MUSIC_PLAY_NEXT))
                    .color(NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false)
                    .decoration(TextDecoration.BOLD, true));

            meta.lore(List.of(
                    Component.text(languageService.t(player, shuffle
                                    ? Message.MUSIC_PLAY_RANDOM_LORE : Message.MUSIC_PLAY_NEXT_LORE))
                            .color(NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false)
            ));

            button.setItemMeta(meta);
        }

        return button;
    }

    private ItemStack navigationButton(Player player, boolean previous) {
        return createInfoItem(player, Material.ARROW,
                previous ? Message.MUSIC_PREV_PAGE : Message.MUSIC_NEXT_PAGE,
                previous ? Message.MUSIC_PREV_PAGE_LORE : Message.MUSIC_NEXT_PAGE_LORE);
    }

    private ItemStack createQueueSummary(Player player, int size, int page, int totalPages) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(languageService.t(player, Message.MUSIC_QUEUE_SUMMARY, size))
                .color(NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text(languageService.t(player,
                        Message.MUSIC_QUEUE_SUMMARY_LORE, page, totalPages))
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createStopEjectButton(Player player) {
        return createInfoItem(player, Material.HOPPER,
                Message.MUSIC_STOP_EJECT, Message.MUSIC_STOP_EJECT_LORE);
    }

    private static Message statusMessage(PlaybackStatus status) {
        return switch (status) {
            case STOPPED -> Message.MUSIC_STOPPED;
            case LOADING -> Message.MUSIC_LOADING_TITLE;
            case PLAYING -> Message.MUSIC_PLAYING;
        };
    }

    private static NamedTextColor statusColor(PlaybackStatus status) {
        return switch (status) {
            case STOPPED -> NamedTextColor.YELLOW;
            case LOADING -> NamedTextColor.AQUA;
            case PLAYING -> NamedTextColor.GREEN;
        };
    }

    private static Material modeMaterial(JukeboxPlaybackMode mode) {
        return switch (mode) {
            case REPEAT_ALL -> Material.REPEATER;
            case REPEAT_ONE -> Material.MUSIC_DISC_11;
            case SHUFFLE -> Material.ENDER_EYE;
        };
    }

    private static NamedTextColor modeColor(JukeboxPlaybackMode mode) {
        return switch (mode) {
            case REPEAT_ALL -> NamedTextColor.GREEN;
            case REPEAT_ONE -> NamedTextColor.GOLD;
            case SHUFFLE -> NamedTextColor.LIGHT_PURPLE;
        };
    }

    private static Message modeName(JukeboxPlaybackMode mode) {
        return switch (mode) {
            case REPEAT_ALL -> Message.MUSIC_SEQUENTIAL_MODE;
            case REPEAT_ONE -> Message.MUSIC_REPEAT_ONE_MODE;
            case SHUFFLE -> Message.MUSIC_RANDOM_MODE;
        };
    }

    private static Message modeDescription(JukeboxPlaybackMode mode) {
        return switch (mode) {
            case REPEAT_ALL -> Message.MUSIC_SEQUENTIAL_MODE_DESC;
            case REPEAT_ONE -> Message.MUSIC_REPEAT_ONE_MODE_DESC;
            case SHUFFLE -> Message.MUSIC_RANDOM_MODE_DESC;
        };
    }

    private static String truncate(String text, int maximum) {
        if (text.length() <= maximum) {
            return text;
        }
        int end = maximum - 3;
        if (Character.isHighSurrogate(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(0, end) + "...";
    }

    private ItemStack createAddAllButton(Player player) {
        ItemStack button = new ItemStack(Material.CHEST);
        ItemMeta meta = button.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.text(languageService.t(player, Message.MUSIC_ADD_ALL))
                    .color(NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false)
                    .decoration(TextDecoration.BOLD, true));

            meta.lore(List.of(
                    Component.text(languageService.t(player, Message.MUSIC_ADD_ALL_LORE))
                            .color(NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false)
            ));

            button.setItemMeta(meta);
        }

        return button;
    }

    private ItemStack createClearQueueButton(Player player) {
        ItemStack button = new ItemStack(Material.BARRIER);
        ItemMeta meta = button.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.text(languageService.t(player, Message.MUSIC_CLEAR_QUEUE))
                    .color(NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false)
                    .decoration(TextDecoration.BOLD, true));

            meta.lore(List.of(
                    Component.text(languageService.t(player, Message.MUSIC_CLEAR_QUEUE_LORE))
                            .color(NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false)
            ));

            button.setItemMeta(meta);
        }

        return button;
    }

    private ItemStack createCloseButton(Player player) {
        ItemStack button = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta meta = button.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.text(languageService.t(player, Message.CLOSE))
                    .color(NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false));

            button.setItemMeta(meta);
        }

        return button;
    }

    private ItemStack createInfoItem(Player player, Material material, Message name, Message description) {
        return createInfoItem(player, material, name, description, new Object[0]);
    }

    private ItemStack createInfoItem(Player player, Material material, Message name,
                                     Message description, Object... args) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.text(languageService.t(player, name, args))
                    .color(NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));

            meta.lore(List.of(
                    Component.text(languageService.t(player, description, args))
                            .color(NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false)
            ));

            item.setItemMeta(meta);
        }

        return item;
    }

    public static int pageCount(int queueSize) {
        return Math.max(1, (Math.max(0, queueSize) + QUEUE_SLOTS.length - 1)
                / QUEUE_SLOTS.length);
    }

    static int queueSlotIndex(int rawSlot) {
        for (int index = 0; index < QUEUE_SLOTS.length; index++) {
            if (QUEUE_SLOTS[index] == rawSlot) {
                return index;
            }
        }
        return -1;
    }

    private static final class JukeboxControlHolder implements InventoryHolder {
        private final Location location;
        private final int page;
        private Inventory inventory;

        private JukeboxControlHolder(Location location, int page) {
            this.location = location.clone();
            this.page = page;
        }

        private void attach(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
