package org.encinet.mik.shared.i18n;

import org.bukkit.entity.Player;

@FunctionalInterface
public interface LanguageChangeListener {

    void onLanguageChanged(Player player);
}
