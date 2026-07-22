package org.encinet.mik.module.i18n;

import org.bukkit.entity.Player;

@FunctionalInterface
public interface LanguageChangeListener {

    void onLanguageChanged(Player player);
}
