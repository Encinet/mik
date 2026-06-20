package org.encinet.mik.module.menu;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public final class MenuNavigation {

    public enum ChildMenu {
        HOME,
        ANNOUNCEMENTS
    }

    private final Map<UUID, ChildMenu> mainMenuReturns = new HashMap<>();
    private Consumer<Player> mainMenuOpener;

    public void setMainMenuOpener(Consumer<Player> mainMenuOpener) {
        this.mainMenuOpener = mainMenuOpener;
    }

    public void openMainMenu(Player player) {
        if (mainMenuOpener != null) {
            mainMenuOpener.accept(player);
        } else {
            player.closeInventory();
        }
    }

    public void markMainMenuChild(Player player, ChildMenu childMenu) {
        mainMenuReturns.put(player.getUniqueId(), childMenu);
    }

    public boolean shouldReturnToMainMenu(Player player, ChildMenu childMenu) {
        return mainMenuReturns.get(player.getUniqueId()) == childMenu;
    }

    public boolean returnToMainMenuIfNeeded(Player player, ChildMenu childMenu) {
        if (!shouldReturnToMainMenu(player, childMenu)) {
            return false;
        }
        mainMenuReturns.remove(player.getUniqueId());
        if (mainMenuOpener != null) {
            mainMenuOpener.accept(player);
        } else {
            player.closeInventory();
        }
        return true;
    }

    public void clearMainMenuReturn(Player player, ChildMenu childMenu) {
        if (shouldReturnToMainMenu(player, childMenu)) {
            mainMenuReturns.remove(player.getUniqueId());
        }
    }
}
