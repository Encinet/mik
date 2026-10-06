package org.encinet.mik.module.safety;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.papermc.paper.configuration.GlobalConfiguration;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.inventory.ItemStack;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** Applies the same item and book size limits to Bukkit events and network packets. */
final class UnsafeItemPolicy {

    static final long MAX_ITEM_SIZE_KIB = 256L;
    private static final long MAX_ITEM_SIZE_BYTES = MAX_ITEM_SIZE_KIB * 1024L;
    private static final int ITEM_BUFFER_MAX_CAPACITY =
            Math.toIntExact(MAX_ITEM_SIZE_BYTES + 1L);
    static final CheckResult SAFE = new CheckResult(false, 0L, null);

    private final RegistryAccess registryAccess;

    UnsafeItemPolicy(RegistryAccess registryAccess) {
        this.registryAccess = registryAccess;
    }

    CheckResult check(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return SAFE;
        }

        try {
            return check(CraftItemStack.unwrap(item));
        } catch (RuntimeException | StackOverflowError exception) {
            return new CheckResult(true, -1L, exception.getClass().getSimpleName());
        }
    }

    CheckResult check(net.minecraft.world.item.ItemStack item) {
        if (item == null || item.isEmpty()) {
            return SAFE;
        }
        try {
            if (EntitySizePolicy.hasOversizedEntityData(item)) {
                return new CheckResult(true, -1L, "oversized-entity-data");
            }
        } catch (RuntimeException | StackOverflowError exception) {
            return new CheckResult(true, -1L, exception.getClass().getSimpleName());
        }
        // A component-free stack encodes to only count and item type.
        if (item.getComponentsPatch().isEmpty()) {
            return SAFE;
        }

        ByteBuf bytes = PooledByteBufAllocator.DEFAULT.heapBuffer(
                128,
                ITEM_BUFFER_MAX_CAPACITY);
        try {
            RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(bytes, registryAccess);
            net.minecraft.world.item.ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, item);
            int size = bytes.readableBytes();
            return size > MAX_ITEM_SIZE_BYTES
                    ? new CheckResult(true, size, null)
                    : SAFE;
        } catch (RuntimeException | StackOverflowError exception) {
            return new CheckResult(true, -1L, exception.getClass().getSimpleName());
        } finally {
            bytes.release();
        }
    }

    BookPacketCheck checkBookPages(List<String> pages) {
        if (pages == null) {
            return new BookPacketCheck(true, -1L, 0L, 0, "missing-pages");
        }
        var bookSize = GlobalConfiguration.get().itemValidation.bookSize;
        return checkBookPages(
                pages,
                bookSize.pageMax.enabled(),
                bookSize.pageMax.enabled() ? bookSize.pageMax.intValue() : 0,
                bookSize.totalMultiplier);
    }

    static BookPacketCheck checkBookPages(
            List<String> pages,
            boolean paperLimitEnabled,
            int pageMax,
            double totalMultiplier
    ) {
        if (pages == null) {
            return new BookPacketCheck(true, -1L, 0L, 0, "missing-pages");
        }

        double multiplier = Math.clamp(totalMultiplier, 0.3D, 1.0D);
        long bytes = 0L;
        long paperAllowed = paperLimitEnabled ? pageMax : Long.MAX_VALUE;

        for (String page : pages) {
            if (page == null) {
                return new BookPacketCheck(true, -1L, 0L, pages.size(), "null-page");
            }

            int byteLength = page.getBytes(StandardCharsets.UTF_8).length;
            bytes += byteLength;
            if (!paperLimitEnabled) {
                continue;
            }

            int multiByteCharacters = 0;
            if (byteLength != page.length()) {
                for (char character : page.toCharArray()) {
                    if (character > 127) {
                        multiByteCharacters++;
                    }
                }
            }

            long pageAllowance = (long) (pageMax
                    * Math.clamp((double) page.length() / 255.0D, 0.1D, 1.0D)
                    * multiplier);
            paperAllowed += pageAllowance;
            if (multiByteCharacters > 1) {
                paperAllowed -= multiByteCharacters;
            }
        }

        long allowed = Math.min(MAX_ITEM_SIZE_BYTES, paperAllowed);
        return new BookPacketCheck(
                bytes > allowed,
                bytes,
                allowed,
                pages.size(),
                null);
    }

    record CheckResult(boolean blocked, long bytes, String error) {
    }

    record BookPacketCheck(
            boolean blocked,
            long bytes,
            long allowed,
            int pages,
            String error
    ) {
    }
}
