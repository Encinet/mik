package org.encinet.mik.module.musicdisc;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Creator for music disc items
 */
public class MusicDiscCreator {

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

    /**
     * Create a music disc item for GUI display (with detailed info)
     */
    public ItemStack createMusicDisc(MusicFileLoader.MusicFile music) {
        return createMusicDisc(music, true);
    }

    /**
     * Create a music disc item
     * @param music The music file
     * @param detailed Whether to include detailed information in lore
     */
    public ItemStack createMusicDisc(MusicFileLoader.MusicFile music, boolean detailed) {
        int hash = music.fileName().hashCode();
        Material discType = DISC_TYPES[Math.abs(hash) % DISC_TYPES.length];

        ItemStack disc = new ItemStack(discType);
        ItemMeta meta = disc.getItemMeta();

        if (meta != null) {
            String extension = music.fileName().substring(
                    music.fileName().lastIndexOf('.') + 1).toUpperCase();

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(music.displayName())
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text(""));
            lore.add(Component.text("格式: " + extension)
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));

            if (music.fileSize() != null) {
                lore.add(Component.text("大小: " + music.fileSize())
                        .color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            }
            if (music.sampleRate() != null) {
                lore.add(Component.text("采样率: " + music.sampleRate())
                        .color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            }
            if (music.duration() != null) {
                lore.add(Component.text("时长: " + music.duration())
                        .color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            }

            if (detailed) {
                lore.add(Component.text(""));
                lore.add(Component.text("左键: 拿取唱片")
                        .color(NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("右键: 在最近的唱片机播放")
                        .color(NamedTextColor.AQUA)
                        .decoration(TextDecoration.ITALIC, false));
            }

            lore.add(Component.text(""));
            lore.add(Component.text("♪ Plasmo Voice 音乐唱片 ♪")
                    .color(NamedTextColor.DARK_PURPLE)
                    .decoration(TextDecoration.ITALIC, true));

            meta.lore(lore);

            float modelValue = (float) (Math.abs(hash) % 1000);
            CustomModelDataComponent customModelData = meta.getCustomModelDataComponent();
            customModelData.setFloats(List.of(modelValue));
            meta.setCustomModelDataComponent(customModelData);

            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            NamespacedKey identifierKey = new NamespacedKey("pv-addon-discs", "identifier");
            NamespacedKey forbidGrindstoneKey = new NamespacedKey("pv-addon-discs", "forbid_grindstone");

            pdc.set(identifierKey, PersistentDataType.STRING, "local://" + music.fileName());
            pdc.set(forbidGrindstoneKey, PersistentDataType.BYTE, (byte) 1);

            meta.addEnchant(org.bukkit.enchantments.Enchantment.MENDING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES,
                    ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_DESTROYS, ItemFlag.HIDE_PLACED_ON);

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
        return disc;
    }
}
