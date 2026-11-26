package org.encinet.mik.feature.community.communication;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.encinet.mik.shared.menu.FloatingMenuCommand;
import org.encinet.mik.shared.menu.FloatingMenuDecoration;
import org.encinet.mik.shared.menu.ui.*;

/** A bounded preview; the measured excerpt owns whether the full-reading action is visible. */
final class AnnouncementPreview {
    private AnnouncementPreview() { }

    static UiNode content(Announcement latest, Component title, Component readFull,
                          FloatingMenuCommand open) {
        if (latest == null || latest.content().isBlank()) return Ui.column().build();
        var body = UiKey.named("excerpt");
        return Ui.layout(UiLayouts.contentColumn(body, UiLength.ui(6)))
                .child(Ui.heading(title).key("announcement-heading").summaryLines(2)
                        .alignment(FloatingMenuDecoration.Alignment.LEFT).build(), LinearSlot.stretch())
                .child(Ui.text(Component.text(latest.date(), NamedTextColor.YELLOW))
                        .key("announcement-date").summaryLines(2)
                        .alignment(FloatingMenuDecoration.Alignment.LEFT).build(), LinearSlot.stretch())
                .child(body, Ui.excerpt(Ui.text(Component.text(latest.content(), NamedTextColor.WHITE))
                                .key("announcement-body").textWidth(112).summaryLines(8)
                                .alignment(FloatingMenuDecoration.Alignment.LEFT).build(),
                        Ui.button(readFull).key("announcement-read").role(UiRole.NAVIGATION).summaryLines(2).primary(open).build()),
                        LinearSlot.stretch()).build();
    }
}
