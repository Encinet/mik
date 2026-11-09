package org.encinet.mik.feature.community.communication;

import org.bukkit.entity.Player;
import org.encinet.mik.shared.menu.MenuPanel;

/** Cached announcement content for embedded hosts; rendering does not mark anything as read. */
public interface AnnouncementHighlights {
    MenuPanel latestPanel(Player player);

    /** Equality-comparable token, unchanged until the published announcements change. */
    Object revision();
}
