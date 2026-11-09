package org.encinet.mik.feature.community.communication;

import static org.encinet.mik.shared.menu.ui.UiLength.ui;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.UnaryOperator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;

/** A measured source reader and an independently paged directory, with feature-owned bookmarks. */
final class AnnouncementMenu {
    private static final UiSize VIEWPORT = new UiSize(960, 2048);
    private AnnouncementMenu() { }

    static MenuScene build(AnnouncementBoard board, AnnouncementBrowserState requested, double typography,
                          boolean canReload, boolean canGoBack, BiFunction<Message, Object[], String> text,
                          Consumer<UnaryOperator<AnnouncementBrowserState>> update, Runnable reload) {
        if (!Double.isFinite(typography) || typography <= 0) throw new IllegalArgumentException("Invalid typography");
        var state = requested.normalize(board);
        var directory = state.directory(board);
        int index = state.position().announcementIndex();
        var notice = board.announcements().isEmpty() ? null : board.announcements().get(index);
        String body = notice == null ? text.apply(Message.ANNOUNCEMENT_EMPTY, new Object[0]) : notice.content();
        Consumer<UnaryOperator<AnnouncementBrowserState>> readingUpdate = change -> update.accept(current -> {
            if (current.selectedNotice() != null && !current.selectedNotice().equals(notice)) return current;
            var latest = current.normalize(board);
            return java.util.Objects.equals(latest.selectedNotice(), notice) ? change.apply(latest) : current;
        });
        UiNode document = Ui.document().key("reading-viewport")
                .blocks(java.util.List.of(Ui.text(Component.text(body, NamedTextColor.WHITE)).key("body").textWidth(320)
                        .alignment(FloatingMenuDecoration.Alignment.LEFT).build()))
                .sizing(UiSizing.width(UiLength.ZERO, ui(384 * typography)))
                .position(state.position().reading(), position -> readingUpdate.accept(value -> value.read(board, position)))
                .pager((page, select) -> readingPager(board, index, page, select, text, readingUpdate)).build();
        UiNode directoryPanel = UiTemplates.sidebar("directory", UiTemplates.reading(UiKey.named("directory"), UiTemplates.title("heading").withSummaryLines(1), UiTemplates.information("count").withSummaryLines(1),
                        UiTemplates.list("directory").withSummaryLines(1), new UiPager(UiTemplates.centeredBar(
                                UiTemplates.information("directory-page"), UiTemplates.navigation("directory-newer"),
                                UiTemplates.navigation("directory-older")), directory,
                                selected -> update.accept(value -> value.browse(board, selected)))))
                .withSizing(UiSizing.width(ui(180), ui(204)));
        UiNode reading = UiTemplates.sheet(UiTemplates.window("reading", UiTemplates.focus(Ui.layout(UiLayouts.contentColumn(document.key(),ui(12)))
                        .child(UiTemplates.heading("date"), new LinearSlot(0, LinearSlot.Alignment.START))
                        .child(document).build(), document.key())),
                UiTemplates.toolbar("reading-tools", UiTemplates.navigation("footer")));
        var menu = MenuScene.screen("announcements").appearance(FloatingMenuAppearance.DEFAULT)
                .stableAnchor().framing(FloatingMenuFraming.COMFORTABLE)
                .viewport(VIEWPORT).content(UiTemplates.split(directoryPanel, reading));
        menu.information("heading", label(text, Message.MAIN_ANNOUNCEMENTS)).region("heading");
        menu.information("count", label(text, Message.ANNOUNCEMENT_COUNT, board.announcements().size())).region("count");
        for (int row = directory.fromIndex(); row < directory.toIndex(); row++) {
            int selected = row;
            var announcement = board.announcements().get(row);
            menu.navigation("notice:" + board.entry(row).id(), Component.text(AnnouncementReading.date(announcement.timestamp()), NamedTextColor.AQUA)
                            .append(Component.newline()).append(Component.text(board.entry(row).excerpt(), NamedTextColor.WHITE)))
                    .selected(row == index).region("directory")
                    .textWidth(FloatingMenuTextWidth.COMPACT).alignment(FloatingMenuDecoration.Alignment.LEFT)
                    .primary((_, _) -> update.accept(value -> value.select(board, selected)));
        }
        if (directory.count() > 1) {
            var newer = menu.navigation("directory-newer", label(text, Message.ANNOUNCEMENT_LIST_NEWER)).region("directory-newer")
                    .primary((_, _) -> update.accept(value -> value.browse(board, value.directory(board).previous().index())));
            if (!directory.hasPrevious()) newer.disabled(label(text, Message.ANNOUNCEMENT_FIRST_PAGE));
            menu.information("directory-page", Component.text((directory.index() + 1) + " / " + directory.count(), NamedTextColor.YELLOW))
                    .region("directory-page");
            var older = menu.navigation("directory-older", label(text, Message.ANNOUNCEMENT_LIST_OLDER)).region("directory-older")
                    .primary((_, _) -> update.accept(value -> value.browse(board, value.directory(board).next().index())));
            if (!directory.hasNext()) older.disabled(label(text, Message.ANNOUNCEMENT_LAST_PAGE));
        }
        if (notice != null) {
            menu.information("date", Component.text(AnnouncementReading.date(notice.timestamp()), NamedTextColor.GOLD)).region("date");
            menu.navigation("latest", label(text, Message.ANNOUNCEMENT_LATEST)).region("footer")
                    .primary((_, _) -> update.accept(value -> value.latest(board)));
        }
        if (canReload) menu.control("reload", label(text, Message.ANNOUNCEMENT_RELOAD)).region("footer")
                .primary((_, _) -> reload.run());
        menu.dismiss(label(text, canGoBack ? Message.BACK : Message.CLOSE)).region("footer").keepAccessible();
        return menu.build();
    }

    private static UiNode readingPager(AnnouncementBoard board, int index, FloatingMenuPage page, IntConsumer select,
                                       BiFunction<Message, Object[], String> text,
                                       Consumer<UnaryOperator<AnnouncementBrowserState>> update) {
        boolean newer = index > 0, older = index + 1 < board.announcements().size();
        int offset = newer ? 1 : 0;
        var navigation = new FloatingMenuPage(page.index() + offset, page.count() + offset + (older ? 1 : 0), 1);
        IntConsumer move = destination -> {
            int part = destination - offset;
            if (part < 0) update.accept(value -> value.across(board, -1));
            else if (part >= page.count()) update.accept(value -> value.across(board, 1));
            else select.accept(part);
        };
        var previousLabel = label(text, page.hasPrevious() ? Message.ANNOUNCEMENT_PREVIOUS_PAGE : Message.ANNOUNCEMENT_NEWER);
        var nextLabel = label(text, page.hasNext() ? Message.ANNOUNCEMENT_NEXT_PAGE : Message.ANNOUNCEMENT_OLDER);
        var previous = Ui.button(UiIcons.CHEVRON_LEFT, previousLabel).key("previous")
                .primary((_, _) -> move.accept(navigation.index() - 1));
        var next = Ui.button(UiIcons.CHEVRON_RIGHT, nextLabel).key("next")
                .primary((_, _) -> move.accept(navigation.index() + 1));
        if (!navigation.hasPrevious()) previous.disabled(label(text, Message.ANNOUNCEMENT_FIRST_PAGE));
        if (!navigation.hasNext()) next.disabled(label(text, Message.ANNOUNCEMENT_LAST_PAGE));
        var controls = UiTemplates.centeredBar(Ui.text(label(text, Message.ANNOUNCEMENT_PAGE, page.index() + 1, page.count()))
                        .key("reading-page").singleLine().build(), previous.build(), next.build());
        return new UiPager(controls, navigation, move);
    }

    private static Component label(BiFunction<Message, Object[], String> text, Message message, Object... args) {
        return Component.text(text.apply(message, args), NamedTextColor.WHITE);
    }
}
