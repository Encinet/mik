package org.encinet.mik.module.world.regen;

import org.bukkit.entity.Player;

public interface RegenSelectionProvider {

    RegenSelection selection(Player player) throws IncompleteSelectionException;

    final class IncompleteSelectionException extends Exception {
    }
}
