package org.encinet.mik.feature.world.heads;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HeadItemsTest {
    @Test void givesTheWholeSelectionOrLeavesEveryStorageSlotAlone() {
        var inventory = mock(PlayerInventory.class);
        var head = mock(ItemStack.class);
        var nearlyFull = mock(ItemStack.class);
        var other = mock(ItemStack.class);
        var filled = mock(ItemStack.class);
        var extra = mock(ItemStack.class);
        when(head.getMaxStackSize()).thenReturn(64);
        when(inventory.getMaxStackSize()).thenReturn(32);
        when(nearlyFull.isSimilar(head)).thenReturn(true);
        when(nearlyFull.getAmount()).thenReturn(30);
        when(nearlyFull.asQuantity(32)).thenReturn(filled);
        when(head.asQuantity(3)).thenReturn(extra);
        var full = new ItemStack[]{nearlyFull, other};
        when(inventory.getStorageContents()).thenReturn(full);

        assertFalse(HeadItems.insert(inventory, head, 5));
        verify(inventory, never()).setStorageContents(any());
        assertSame(nearlyFull, full[0]);
        when(inventory.getStorageContents()).thenReturn(new ItemStack[]{nearlyFull, other, null});

        assertTrue(HeadItems.insert(inventory, head, 5));
        verify(inventory).setStorageContents(new ItemStack[]{filled, other, extra});
    }
}
