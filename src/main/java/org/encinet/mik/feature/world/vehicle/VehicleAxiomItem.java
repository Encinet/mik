package org.encinet.mik.feature.world.vehicle;

import com.mojang.math.Transformation;
import com.mojang.serialization.DynamicOps;
import io.papermc.paper.adventure.PaperAdventure;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.MinecraftServer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.inventory.ItemStack;
import org.encinet.mik.shared.i18n.Message;
import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;

/** Reads Axiom's picked display item without spawning its entity tree. */
final class VehicleAxiomItem {
    static List<VehicleDefinition.Part> parts(ItemStack item) {
        var stack = CraftItemStack.asNMSCopy(item);
        var data = stack.get(DataComponents.CUSTOM_DATA);
        if (stack.isEmpty() || data == null) throw new VehicleImportException(Message.VEHICLE_MODEL_HAND_REQUIRED);
        return parts(data.copyTag(), MinecraftServer.getServer().registryAccess().createSerializationContext(NbtOps.INSTANCE));
    }

    static List<VehicleDefinition.Part> parts(CompoundTag customData, DynamicOps<Tag> ops) {
        try { return decode(customData, ops); }
        catch (VehicleImportException exception) { throw exception; }
        catch (IllegalArgumentException exception) { throw new VehicleImportException(Message.VEHICLE_MODEL_HAND_INVALID); }
    }

    private static List<VehicleDefinition.Part> decode(CompoundTag customData, DynamicOps<Tag> ops) {
        CompoundTag root = customData.getCompoundOrEmpty("Axiom").getCompoundOrEmpty("EntityPlacer");
        if (root.isEmpty()) throw new VehicleImportException(Message.VEHICLE_MODEL_HAND_REQUIRED);
        List<VehicleDefinition.Part> parts = new ArrayList<>();
        var pending = new ArrayDeque<CompoundTag>();
        pending.add(root);
        int members = 0;
        while (!pending.isEmpty()) {
            CompoundTag entity = pending.removeFirst();
            if (++members > 256) throw new VehicleImportException(Message.VEHICLE_GROUP_LIMIT);
            String type = entity.getStringOr("id", "");
            VehicleDefinition.PartKind kind = switch (type) {
                case "minecraft:block_display" -> VehicleDefinition.PartKind.BLOCK;
                case "minecraft:item_display" -> VehicleDefinition.PartKind.ITEM;
                case "minecraft:text_display" -> VehicleDefinition.PartKind.TEXT;
                default -> throw new VehicleImportException(Message.VEHICLE_MODEL_HAND_INVALID);
            };
            if (parts.size() >= 128) throw new VehicleImportException(Message.VEHICLE_GROUP_LIMIT);
            parts.add(part(entity, kind, ops));
            Tag passengers = entity.get("Passengers");
            if (passengers != null) {
                if (!(passengers instanceof ListTag list)) throw new VehicleImportException(Message.VEHICLE_MODEL_HAND_INVALID);
                if (members + pending.size() + list.size() > 256) throw new VehicleImportException(Message.VEHICLE_GROUP_LIMIT);
                for (Tag passenger : list) {
                    if (!(passenger instanceof CompoundTag child)) throw new VehicleImportException(Message.VEHICLE_MODEL_HAND_INVALID);
                    pending.addLast(child);
                }
            }
        }
        return List.copyOf(parts);
    }

    private static VehicleDefinition.Part part(CompoundTag entity, VehicleDefinition.PartKind kind, DynamicOps<Tag> ops) {
        String payload = switch (kind) {
            case BLOCK -> blockState(entity.getCompoundOrEmpty("block_state"));
            case ITEM -> {
                var item = net.minecraft.world.item.ItemStack.CODEC.parse(ops, entity.getCompoundOrEmpty("item"))
                        .getOrThrow(IllegalArgumentException::new);
                yield Base64.getEncoder().encodeToString(CraftItemStack.asBukkitCopy(item).serializeAsBytes());
            }
            case TEXT -> {
                Tag text = entity.get("text");
                var component = text == null ? net.kyori.adventure.text.Component.empty()
                        : PaperAdventure.asAdventure(ComponentSerialization.CODEC.parse(ops, text).getOrThrow(IllegalArgumentException::new));
                yield GsonComponentSerializer.gson().serialize(component);
            }
        };
        CompoundTag brightness = entity.getCompoundOrEmpty("brightness");
        return new VehicleDefinition.Part(kind, payload, transform(entity), VehicleDefinition.Animation.BODY,
                entity.getStringOr("billboard", "fixed").toUpperCase(Locale.ROOT),
                entity.getStringOr("item_display", "none").toUpperCase(Locale.ROOT),
                entity.getStringOr("alignment", "center").toUpperCase(Locale.ROOT),
                brightness.getIntOr("block", -1), brightness.getIntOr("sky", -1),
                entity.getIntOr("background", 0x40000000), entity.getIntOr("line_width", 200),
                entity.getByteOr("text_opacity", (byte) -1), entity.getBooleanOr("shadow", false), entity.getBooleanOr("see_through", false));
    }

    private static Matrix4f transform(CompoundTag entity) {
        ListTag rotation = entity.getListOrEmpty("Rotation");
        if (!rotation.isEmpty() && (rotation.size() != 2 || !(rotation.get(0) instanceof NumericTag) || !(rotation.get(1) instanceof NumericTag)))
            throw new VehicleImportException(Message.VEHICLE_MODEL_HAND_INVALID);
        float yaw = rotation.isEmpty() ? 0 : ((NumericTag) rotation.get(0)).floatValue();
        float pitch = rotation.isEmpty() ? 0 : ((NumericTag) rotation.get(1)).floatValue();
        Matrix4f matrix = new Matrix4f().rotateY((float) -Math.toRadians(yaw)).rotateX((float) Math.toRadians(pitch));
        Tag transformation = entity.get("transformation");
        // Axiom bakes group offsets into each display; parent matrices must not be applied again.
        if (transformation != null) matrix.mul(Transformation.EXTENDED_CODEC.parse(NbtOps.INSTANCE, transformation)
                .getOrThrow(IllegalArgumentException::new).getMatrix());
        return matrix;
    }

    private static String blockState(CompoundTag state) {
        String name = state.getStringOr("Name", "");
        if (!name.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw new VehicleImportException(Message.VEHICLE_MODEL_HAND_INVALID);
        CompoundTag properties = state.getCompoundOrEmpty("Properties");
        if (properties.isEmpty()) return name;
        StringJoiner joined = new StringJoiner(",", name + "[", "]");
        for (String key : properties.keySet().stream().sorted().toList()) {
            String value = properties.getStringOr(key, "");
            if (!key.matches("[a-z0-9_]+") || !value.matches("[a-z0-9_]+"))
                throw new VehicleImportException(Message.VEHICLE_MODEL_HAND_INVALID);
            joined.add(key + "=" + value);
        }
        return joined.toString();
    }
}
