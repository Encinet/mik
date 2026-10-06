package org.encinet.mik.module.music.disc;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataType;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.music.catalog.MusicTrack;
import org.encinet.mik.module.music.catalog.AudioProperties;
import org.encinet.mik.module.music.catalog.AudioPropertiesFormatter;
import org.encinet.mik.module.music.catalog.TrackDetails;

import java.util.ArrayList;
import java.util.List;

/**
 * Creator for music disc items
 */
public class MusicDiscFactory {

    private static final Material[] DISC_TYPES = {
            Material.MUSIC_DISC_5, Material.MUSIC_DISC_11, Material.MUSIC_DISC_13,
            Material.MUSIC_DISC_BLOCKS, Material.MUSIC_DISC_CAT, Material.MUSIC_DISC_CHIRP,
            Material.MUSIC_DISC_CREATOR, Material.MUSIC_DISC_CREATOR_MUSIC_BOX,
            Material.MUSIC_DISC_FAR, Material.MUSIC_DISC_LAVA_CHICKEN, Material.MUSIC_DISC_MALL,
            Material.MUSIC_DISC_MELLOHI, Material.MUSIC_DISC_OTHERSIDE, Material.MUSIC_DISC_PIGSTEP,
            Material.MUSIC_DISC_PRECIPICE, Material.MUSIC_DISC_RELIC, Material.MUSIC_DISC_STAL,
            Material.MUSIC_DISC_STRAD, Material.MUSIC_DISC_TEARS, Material.MUSIC_DISC_WAIT,
            Material.MUSIC_DISC_WARD
    };

    private final LanguageService languageService;
    private final MusicDiscSigner signer;

    public MusicDiscFactory(LanguageService languageService, MusicDiscSigner signer) {
        this.languageService = languageService;
        this.signer = signer;
    }

    /** Creates a lightweight GUI item. Online playback snapshots are intentionally omitted. */
    public ItemStack createDisplayDisc(MusicTrack music, boolean detailed, Player player) {
        return createMusicDisc(music, detailed, languageService.language(player), false);
    }

    /** Creates a player-owned disc that can restore an online track after a restart. */
    public ItemStack createPersistentDisc(MusicTrack music, Player player) {
        return createMusicDisc(music, false, languageService.language(player), true);
    }

    /** Creates a server-owned disc using the default language. */
    public ItemStack createPersistentDisc(MusicTrack music) {
        return createMusicDisc(music, false, Language.DEFAULT, true);
    }

    private ItemStack createMusicDisc(
            MusicTrack music, boolean detailed, Language language, boolean persistent) {
        int hash = music.id().hashCode();
        TrackDetails details = music.details();
        AudioProperties audio = details.audio();
        Material discType = DISC_TYPES[Math.floorMod(hash, DISC_TYPES.length)];

        ItemStack disc = new ItemStack(discType);
        ItemMeta meta = disc.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.text(limit(details.title(), 64))
                    .color(NamedTextColor.WHITE)
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(lore(language, Message.MUSIC_FORMAT, details.format())
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));

            if (details.artist() != null) {
                lore.add(lore(language, Message.MUSIC_ARTIST, details.artist())
                        .color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            }
            if (details.originalAuthor() != null) {
                lore.add(lore(language, Message.MUSIC_ORIGINAL_AUTHOR, details.originalAuthor())
                        .color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            }
            if (details.album() != null) {
                lore.add(lore(language, Message.MUSIC_ALBUM, details.album())
                        .color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            }

            String fileSize = AudioPropertiesFormatter.fileSize(audio.fileSizeBytes());
            if (fileSize != null) {
                lore.add(lore(language, Message.MUSIC_SIZE, fileSize)
                        .color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            }
            String sampleRate = AudioPropertiesFormatter.sampleRate(audio.sampleRateHz());
            if (sampleRate != null) {
                lore.add(lore(language, Message.MUSIC_SAMPLE_RATE, sampleRate)
                        .color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            }
            String duration = AudioPropertiesFormatter.duration(audio.duration());
            if (duration != null) {
                lore.add(lore(language, Message.MUSIC_DURATION, duration)
                        .color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            }

            if (detailed) {
                lore.add(Component.text(""));
                lore.add(Component.text(languageService.t(language, Message.MUSIC_DISC_LEFT))
                        .color(NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text(languageService.t(language, Message.MUSIC_DISC_RIGHT))
                        .color(NamedTextColor.AQUA)
                        .decoration(TextDecoration.ITALIC, false));
            }

            meta.lore(lore);

            float modelValue = (float) Math.floorMod(hash, 1000);
            CustomModelDataComponent customModelData = meta.getCustomModelDataComponent();
            customModelData.setFloats(List.of(modelValue));
            meta.setCustomModelDataComponent(customModelData);

            meta.getPersistentDataContainer().set(
                    MusicDiscKeys.TRACK, PersistentDataType.STRING, music.id());
            if (persistent) {
                String snapshot = MusicDiscSnapshot.serialize(music);
                if (snapshot != null) {
                    meta.getPersistentDataContainer().set(
                            MusicDiscKeys.TRACK_DATA, PersistentDataType.STRING, snapshot);
                    meta.getPersistentDataContainer().set(
                            MusicDiscKeys.TRACK_SIGNATURE, PersistentDataType.STRING,
                            signer.sign(snapshot));
                }
            }

            disc.setItemMeta(meta);
        }

        TooltipDisplay tooltipDisplay = TooltipDisplay.tooltipDisplay()
                .addHiddenComponents(
                        DataComponentTypes.JUKEBOX_PLAYABLE, DataComponentTypes.ENCHANTMENTS,
                        DataComponentTypes.ATTRIBUTE_MODIFIERS, DataComponentTypes.UNBREAKABLE,
                        DataComponentTypes.CAN_BREAK, DataComponentTypes.CAN_PLACE_ON,
                        DataComponentTypes.STORED_ENCHANTMENTS, DataComponentTypes.DYED_COLOR,
                        DataComponentTypes.TRIM
                )
                .build();

        disc.setData(DataComponentTypes.TOOLTIP_DISPLAY, tooltipDisplay);
        disc.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        disc.unsetData(DataComponentTypes.JUKEBOX_PLAYABLE);
        return disc;
    }

    private static String limit(String value, int maximum) {
        if (value.length() <= maximum) {
            return value;
        }
        int end = maximum - 3;
        if (Character.isHighSurrogate(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, end) + "...";
    }

    private Component lore(Language language, Message message, String value) {
        return Component.text(limit(languageService.t(language, message, limit(value, 128)), 256));
    }
}
