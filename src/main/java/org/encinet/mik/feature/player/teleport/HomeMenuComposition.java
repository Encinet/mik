package org.encinet.mik.feature.player.teleport;

import java.util.List;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.encinet.mik.shared.menu.FloatingMenuCommand;
import org.encinet.mik.shared.menu.FloatingMenuDecoration;
import org.encinet.mik.shared.menu.ui.*;

/** The home list owns the reading focus; destination information and tools share the right column. */
final class HomeMenuComposition {
    private HomeMenuComposition() { }

    static final UiSize VIEWPORT = new UiSize(960, 2048);

    static UiNode content(UiNode search, UiDocument homes, UiElement empty) {
        UiDocument reading = homes.blocks().isEmpty()
                ? new UiDocument(homes.key(), homes.header(), List.of(UiChild.linear(empty.key(), empty, LinearSlot.stretch())),
                        homes.gap(), homes.position(), homes.selectPosition(), homes.style(), homes.sizing(), homes.pager(), homes.bodyReading()) : homes;
        var browser = UiTemplates.window("browser", UiTemplates.focus(Ui.layout(UiLayouts.contentColumn(reading.key(),UiLength.ui(12)))
                        .child(UiTemplates.title("header").withSummaryLines(1))
                        .child(UiKey.named("search"), search, LinearSlot.fit())
                        .child(reading, LinearSlot.stretch()).build(), reading.key()));
        var inspector = Ui.layout(UiLayouts.contentColumn(UiKey.named("detail"),UiLength.ui(12))).key("inspector")
                .child(UiTemplates.window("detail", UiTemplates.information("detail").withSummaryLines(2)), LinearSlot.stretch())
                .child(UiTemplates.toolbar("home-tools", UiTemplates.menu(UiTemplates.contentActions("actions", 3, 2)
                        .withSummaryLines(1),
                        UiTemplates.navigation("global-actions").withSummaryLines(1))), LinearSlot.stretch()).build();
        return UiTemplates.focus(new UiContainer(UiKey.named("homes"),
                UiLayouts.firstFit(UiLayouts.row(UiLength.ui(16)), UiLayouts.contentColumn(UiKey.named("browser"),UiLength.ui(12))),
                List.of(UiChild.linear(UiKey.named("browser"), browser, new LinearSlot(0, LinearSlot.Alignment.START)),
                        UiChild.linear(UiKey.named("inspector"), inspector, new LinearSlot(0, LinearSlot.Alignment.START))),
                UiStyle.PLAIN, UiSizing.CONTENT), UiKey.named("browser"));
    }

    static UiDocument homes(List<String> names, String focused, Consumer<String> select,
                        UiReadingPosition position, Consumer<UiReadingPosition> browse) {
        var items = new java.util.ArrayList<UiNode>();
        for (String name : names) items.add(Ui.button(Component.text(name, NamedTextColor.YELLOW))
                .key(UiKey.item("home:" + name)).summaryLines(1).selected(name.equals(focused))
                .alignment(FloatingMenuDecoration.Alignment.LEFT)
                .primary((_, _) -> select.accept(name)).build());
        return new UiDocument(UiKey.named("home-list"), Ui.column().build(),
                items.stream().map(item -> UiChild.linear(item.key(), item, LinearSlot.stretch())).toList(),
                UiLength.ui(8), position, browse, UiStyle.PLAIN, UiSizing.CONTENT);
    }

    static UiNode help(List<Component> lines, int page, java.util.function.IntConsumer select) {
        var items = new java.util.ArrayList<UiNode>();
        for (int index=0; index<lines.size(); index++) items.add(Ui.text(lines.get(index)).key("line:"+index)
                .textWidth(240).alignment(FloatingMenuDecoration.Alignment.LEFT).build());
        return Ui.pagedList().key("home-help").items(items).page(page,select).build();
    }

    static UiNode confirmation(Component question, Component confirm, FloatingMenuCommand apply) {
        return Ui.column().key("home-confirmation").gap(UiLength.ui(8))
                .child(Ui.text(question).key("question").alignment(FloatingMenuDecoration.Alignment.LEFT).build())
                .child(Ui.row().key("actions").gap(UiLength.ui(8))
                        .child(Ui.button(confirm).key("confirm").primary((p,h) -> {
                            h.closePopover(); apply.execute(p,h);
                        }).build()).build()).build();
    }
}
