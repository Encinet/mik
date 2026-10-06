package org.encinet.mik.module.safety;

import com.mojang.datafixers.util.Pair;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket;
import net.minecraft.network.protocol.game.ClientboundSetCursorItemPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.encinet.mik.module.safety.UnsafeItemPolicy.CheckResult;

import java.util.ArrayList;
import java.util.List;

/** Rewrites supported outbound packets to omit items rejected by the shared policy. */
final class UnsafeItemPacketSanitizer {

    private final UnsafeItemPolicy itemPolicy;

    UnsafeItemPacketSanitizer(UnsafeItemPolicy itemPolicy) {
        this.itemPolicy = itemPolicy;
    }

    Packet<?> sanitizePacket(Packet<?> packet, NetworkAudit audit) {
        if (packet instanceof ClientboundContainerSetSlotPacket slotPacket) {
            CheckResult result = itemPolicy.check(slotPacket.getItem());
            if (!result.blocked()) {
                return packet;
            }

            audit.record(result);
            return new ClientboundContainerSetSlotPacket(
                    slotPacket.getContainerId(),
                    slotPacket.getStateId(),
                    slotPacket.getSlot(),
                    net.minecraft.world.item.ItemStack.EMPTY);
        }

        if (packet instanceof ClientboundContainerSetContentPacket contentPacket) {
            List<net.minecraft.world.item.ItemStack> replacement = null;
            List<net.minecraft.world.item.ItemStack> items = contentPacket.items();

            for (int slot = 0; slot < items.size(); slot++) {
                CheckResult result = itemPolicy.check(items.get(slot));
                if (!result.blocked()) {
                    continue;
                }

                if (replacement == null) {
                    replacement = new ArrayList<>(items);
                }
                replacement.set(slot, net.minecraft.world.item.ItemStack.EMPTY);
                audit.record(result);
            }

            CheckResult cursorResult = itemPolicy.check(contentPacket.carriedItem());
            net.minecraft.world.item.ItemStack carriedItem = contentPacket.carriedItem();
            if (cursorResult.blocked()) {
                carriedItem = net.minecraft.world.item.ItemStack.EMPTY;
                audit.record(cursorResult);
            }

            if (replacement == null && !cursorResult.blocked()) {
                return packet;
            }

            return new ClientboundContainerSetContentPacket(
                    contentPacket.containerId(),
                    contentPacket.stateId(),
                    replacement == null ? items : replacement,
                    carriedItem);
        }

        if (packet instanceof ClientboundSetCursorItemPacket cursorPacket) {
            CheckResult result = itemPolicy.check(cursorPacket.contents());
            if (!result.blocked()) {
                return packet;
            }

            audit.record(result);
            return new ClientboundSetCursorItemPacket(
                    net.minecraft.world.item.ItemStack.EMPTY);
        }

        if (packet instanceof ClientboundSetPlayerInventoryPacket inventoryPacket) {
            CheckResult result = itemPolicy.check(inventoryPacket.contents());
            if (!result.blocked()) {
                return packet;
            }

            audit.record(result);
            return new ClientboundSetPlayerInventoryPacket(
                    inventoryPacket.slot(),
                    net.minecraft.world.item.ItemStack.EMPTY);
        }

        if (packet instanceof ClientboundSetEquipmentPacket equipmentPacket) {
            List<Pair<net.minecraft.world.entity.EquipmentSlot,
                    net.minecraft.world.item.ItemStack>> replacement = null;
            List<Pair<net.minecraft.world.entity.EquipmentSlot,
                    net.minecraft.world.item.ItemStack>> slots = equipmentPacket.getSlots();

            for (int index = 0; index < slots.size(); index++) {
                Pair<net.minecraft.world.entity.EquipmentSlot,
                        net.minecraft.world.item.ItemStack> slot = slots.get(index);
                CheckResult result = itemPolicy.check(slot.getSecond());
                if (!result.blocked()) {
                    continue;
                }

                if (replacement == null) {
                    replacement = new ArrayList<>(slots);
                }
                replacement.set(
                        index,
                        Pair.of(slot.getFirst(), net.minecraft.world.item.ItemStack.EMPTY));
                audit.record(result);
            }

            return replacement == null
                    ? packet
                    : new ClientboundSetEquipmentPacket(
                            equipmentPacket.getEntity(), replacement, true);
        }

        if (packet instanceof ClientboundSetEntityDataPacket entityDataPacket) {
            List<SynchedEntityData.DataValue<?>> replacement = null;
            List<SynchedEntityData.DataValue<?>> values = entityDataPacket.packedItems();

            for (int index = 0; index < values.size(); index++) {
                SynchedEntityData.DataValue<?> value = values.get(index);
                if (value.serializer() != EntityDataSerializers.ITEM_STACK
                        || !(value.value() instanceof net.minecraft.world.item.ItemStack item)) {
                    continue;
                }

                CheckResult result = itemPolicy.check(item);
                if (!result.blocked()) {
                    continue;
                }

                if (replacement == null) {
                    replacement = new ArrayList<>(values);
                }
                replacement.set(
                        index,
                        new SynchedEntityData.DataValue<>(
                                value.id(),
                                EntityDataSerializers.ITEM_STACK,
                                net.minecraft.world.item.ItemStack.EMPTY));
                audit.record(result);
            }

            return replacement == null
                    ? packet
                    : new ClientboundSetEntityDataPacket(
                            entityDataPacket.id(), replacement);
        }

        if (packet instanceof ClientboundMerchantOffersPacket merchantPacket) {
            MerchantOffers offers = merchantPacket.getOffers();
            MerchantOffers replacement = null;

            for (int index = 0; index < offers.size(); index++) {
                MerchantOffer offer = offers.get(index);
                boolean blocked = auditIfBlocked(offer.getBaseCostA(), audit);
                blocked |= auditIfBlocked(offer.getCostB(), audit);
                blocked |= auditIfBlocked(offer.getResult(), audit);

                if (blocked) {
                    if (replacement == null) {
                        replacement = new MerchantOffers();
                        replacement.addAll(offers.subList(0, index));
                    }
                } else if (replacement != null) {
                    replacement.add(offer);
                }
            }

            return replacement == null
                    ? packet
                    : new ClientboundMerchantOffersPacket(
                            merchantPacket.getContainerId(),
                            replacement,
                            merchantPacket.getVillagerLevel(),
                            merchantPacket.getVillagerXp(),
                            merchantPacket.showProgress(),
                            merchantPacket.canRestock());
        }

        if (packet instanceof ClientboundBundlePacket bundlePacket) {
            return sanitizeBundle(bundlePacket, audit);
        }

        return packet;
    }

    static boolean canContainItem(Packet<?> packet) {
        // Deliberate allowlist: never apply the item limit to chunks, registry
        // synchronization, plugin messages, or modded custom payload packets.
        return packet instanceof ClientboundContainerSetSlotPacket
                || packet instanceof ClientboundContainerSetContentPacket
                || packet instanceof ClientboundSetCursorItemPacket
                || packet instanceof ClientboundSetPlayerInventoryPacket
                || packet instanceof ClientboundSetEquipmentPacket
                || packet instanceof ClientboundSetEntityDataPacket
                || packet instanceof ClientboundMerchantOffersPacket
                || packet instanceof ClientboundBundlePacket;
    }

    private boolean auditIfBlocked(
            net.minecraft.world.item.ItemStack item,
            NetworkAudit audit
    ) {
        CheckResult result = itemPolicy.check(item);
        if (!result.blocked()) {
            return false;
        }

        audit.record(result);
        return true;
    }

    private Packet<?> sanitizeBundle(
            ClientboundBundlePacket bundle,
            NetworkAudit audit
    ) {
        List<Packet<? super ClientGamePacketListener>> replacement = new ArrayList<>();
        boolean changed = false;

        for (Packet<? super ClientGamePacketListener> packet : bundle.subPackets()) {
            Packet<?> sanitized = sanitizePacket(packet, audit);
            replacement.add(asGamePacket(sanitized));
            changed |= sanitized != packet;
        }

        return changed ? new ClientboundBundlePacket(replacement) : bundle;
    }

    @SuppressWarnings("unchecked")
    private static Packet<? super ClientGamePacketListener> asGamePacket(Packet<?> packet) {
        return (Packet<? super ClientGamePacketListener>) packet;
    }

    static final class NetworkAudit {

        private int removed;
        private long largestBytes = -1L;
        private String firstError;

        int removed() {
            return removed;
        }

        long largestBytes() {
            return largestBytes;
        }

        String firstError() {
            return firstError;
        }

        private void record(CheckResult result) {
            removed++;
            largestBytes = Math.max(largestBytes, result.bytes());
            if (firstError == null && result.error() != null) {
                firstError = result.error();
            }
        }
    }

}
