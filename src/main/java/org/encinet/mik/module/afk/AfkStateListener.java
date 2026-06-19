package org.encinet.mik.module.afk;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

@FunctionalInterface
public interface AfkStateListener {

    void onAfkStateChanged(Player player, @Nullable AfkState state);
}
