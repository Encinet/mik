package org.encinet.mik.feature.world.plot;

import java.util.List;
import java.util.Objects;
import java.util.function.IntConsumer;
import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.FloatingMenuCommand;
import org.encinet.mik.shared.menu.ui.*;

/** Plot and board catalogues supply immutable labels before the deferred reading window is measured. */
public final class PlotCardList {
    public record Card(UiKey key, Component label, FloatingMenuCommand choose,java.util.Optional<UiElement> secondary) {
        public Card { Objects.requireNonNull(key); Objects.requireNonNull(label); Objects.requireNonNull(choose); Objects.requireNonNull(secondary); }
        public Card(UiKey key,Component label,FloatingMenuCommand choose) { this(key,label,choose,java.util.Optional.empty()); }
    }

    private PlotCardList() { }

    public static UiNode content(List<Card> source, Component empty, int page, IntConsumer read) {
        List<Card> cards = List.copyOf(source);
        return Ui.alternative(window(cards,empty,page,read,false),window(cards,empty,page,read,true));
    }

    private static UiNode window(List<Card> cards,Component empty,int page,IntConsumer read,boolean compact) {
        var template = Ui.button(Component.text(compact ? "M" : "M\nM")).maxLines(compact ? 1 : 2).build();
        boolean actions = cards.stream().anyMatch(card -> card.secondary().isPresent());
        UiNode rowTemplate = actions ? UiTemplates.actionRow(template,Ui.button(UiIcons.INFO,Component.text("Information")).build()) : template;
        var list = Ui.windowedList(cards.size(),rowTemplate,index -> {
            var card = cards.get(index);
            var primary = Ui.button(card.label()).key(card.key()).maxLines(compact ? 1 : 2).tooltip(card.label())
                    .role(UiRole.NAVIGATION).primary(card.choose()).build();
            if (card.secondary().isEmpty()) return primary;
            var row = UiTemplates.actionRow(primary,card.secondary().orElseThrow());
            return new UiContainer(card.key(),row.layout(),row.children(),row.style(),row.sizing());
        }).key("cards").page(page,read).sizing(new UiSizing(UiSize.ZERO,new UiSize(640,260)));
        if (cards.isEmpty()) list.header(Ui.text(empty).key("empty").singleLine().build());
        return Ui.windowedGrid(list.build(),2,UiLength.ui(180),UiLength.ui(12));
    }
}
