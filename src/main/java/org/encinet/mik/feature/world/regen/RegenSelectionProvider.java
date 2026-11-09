package org.encinet.mik.feature.world.regen;

import org.bukkit.entity.Player;

public interface RegenSelectionProvider {

    RegenSelection selection(Player player) throws IncompleteSelectionException;

    final class IncompleteSelectionException extends Exception {
        @java.io.Serial
        private static final long serialVersionUID = 1L;

    }
}
