package org.encinet.mik.feature.community.communication;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.encinet.mik.shared.menu.FloatingMenuCommand;
import org.encinet.mik.shared.menu.FloatingMenuDecoration;
import org.encinet.mik.shared.menu.ui.*;

/** A bounded preview; the measured excerpt owns whether the full-reading action is visible. */
final class AnnouncementPreview {
    private AnnouncementPreview() { }

    static UiNode content(AnnouncementCatalog.Announcement latest, Component title, Component readFull,
                          FloatingMenuCommand open) {
        if (latest == null || latest.content().isBlank()) return Ui.column().build();
        return Ui.column().gap(UiLength.ui(6))
                .child(Ui.heading(title).key("announcement-heading")
                        .alignment(FloatingMenuDecoration.Alignment.LEFT).build(), LinearSlot.stretch())
                .child(Ui.text(Component.text(AnnouncementReading.date(latest.timestamp()), NamedTextColor.YELLOW))
                        .key("announcement-date").noWrap()
                        .alignment(FloatingMenuDecoration.Alignment.LEFT).build(), LinearSlot.stretch())
                .child(Ui.excerpt(Ui.text(Component.text(latest.content(), NamedTextColor.WHITE))
                                .key("announcement-body").textWidth(112).summaryLines(8)
                                .alignment(FloatingMenuDecoration.Alignment.LEFT).build(),
                        Ui.button(readFull).key("announcement-read").role(UiRole.NAVIGATION).primary(open).build()),
                        LinearSlot.stretch()).build();
    }
}
