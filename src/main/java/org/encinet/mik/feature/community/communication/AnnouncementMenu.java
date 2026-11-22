package org.encinet.mik.feature.community.communication;

import java.util.List;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.menu.FloatingMenuContext;
import org.encinet.mik.shared.menu.FloatingMenuDecoration;
import org.encinet.mik.shared.menu.FloatingMenuFraming;
import org.encinet.mik.shared.menu.FloatingMenuPage;
import org.encinet.mik.shared.menu.MenuScene;
import org.encinet.mik.shared.menu.ui.Ui;
import org.encinet.mik.shared.menu.ui.UiReadingPosition;
import org.encinet.mik.shared.menu.ui.UiSize;
import org.encinet.mik.shared.menu.ui.UiSizing;
import org.encinet.mik.shared.menu.ui.UiTemplates;

final class AnnouncementMenu {
    private AnnouncementMenu() { }

    record Position(int entry, UiReadingPosition reading) {
        static final Position START = new Position(0, UiReadingPosition.START);
    }

    static MenuScene build(AnnouncementSnapshot snapshot, LanguageService language, FloatingMenuContext<Position> context) {
        var entries = snapshot.entries();
        var player = context.player();
        var position = context.state();
        Consumer<Position> move = next -> context.update(current -> current == position ? next : current);
        int index = Math.clamp(position.entry(), 0, Math.max(0, entries.size() - 1));
        var entry = entries.isEmpty() ? null : entries.get(index);
        var title = language.text(player, Message.MAIN_ANNOUNCEMENTS, NamedTextColor.GOLD);
        var body = entry == null ? language.text(player, snapshot.state() == AnnouncementSnapshot.State.UNAVAILABLE
                ? Message.ANNOUNCEMENT_UNAVAILABLE : Message.ANNOUNCEMENT_EMPTY, NamedTextColor.WHITE)
                : Component.text(entry.content(), NamedTextColor.WHITE);
        if (snapshot.state() == AnnouncementSnapshot.State.STALE) {
            title = title.append(Component.newline()).append(language.text(player, Message.ANNOUNCEMENT_STALE, NamedTextColor.YELLOW));
        }
        var heading = entry == null ? title : title.append(Component.newline())
                .append(Component.text(entry.date(), NamedTextColor.YELLOW));
        var document = Ui.document().key("update-log").readingBody()
                .header(Ui.heading(heading).key("date").build())
                .blocks(List.of(Ui.text(body).key("body").textWidth(320)
                        .alignment(FloatingMenuDecoration.Alignment.LEFT).build()))
                .sizing(new UiSizing(UiSize.ZERO, new UiSize(420, 300)))
                .position(position.reading(), reading -> move.accept(new Position(index, reading)))
                .pager((page, select) -> {
                    int offset = index > 0 ? 1 : 0;
                    var navigation = new FloatingMenuPage(page.index() + offset,
                            page.count() + offset + (index + 1 < entries.size() ? 1 : 0), 1);
                    if (navigation.count() == 1) return UiTemplates.pager(page, select);
                    return UiTemplates.pager(navigation,
                            language.text(player, page.hasPrevious() ? Message.ANNOUNCEMENT_PREVIOUS_PAGE : Message.ANNOUNCEMENT_NEWER, NamedTextColor.WHITE),
                            language.text(player, Message.ANNOUNCEMENT_PAGE, NamedTextColor.WHITE, page.index() + 1, page.count()),
                            language.text(player, page.hasNext() ? Message.ANNOUNCEMENT_NEXT_PAGE : Message.ANNOUNCEMENT_OLDER, NamedTextColor.WHITE),
                            destination -> {
                                int part = destination - offset;
                                if (part < 0) move.accept(new Position(index - 1, UiReadingPosition.START));
                                else if (part >= page.count()) move.accept(new Position(index + 1, UiReadingPosition.START));
                                else select.accept(part);
                            });
                }).build();
        var menu = MenuScene.screen("announcements").stableAnchor().framing(FloatingMenuFraming.COMFORTABLE)
                .content(UiTemplates.sheet(UiTemplates.window("updates", document),
                        UiTemplates.toolbar("navigation", UiTemplates.navigation("footer"))));
        var dismiss = context.canGoBack() ? Message.BACK : Message.CLOSE;
        menu.dismiss(language.text(player, dismiss, NamedTextColor.GREEN)).region("footer").keepAccessible();
        return menu.build();
    }
}
