package org.encinet.mik.feature.entertainment.music.ui;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.ui.*;

/** A bounded group's complete member cards paginate as atomic blocks, including all local actions. */
final class MusicGroupContent {
    private MusicGroupContent() { }

    static UiNode member(UUID id, Component name, Component status, Component position, Component coordinates,
                         UiNode visit, List<UiNode> actions) {
        var title = name.append(Component.text(" · ")).append(status);
        var card = MusicWorkspace.column("member-card")
                .child(Ui.text(title).key("name").textWidth(180).tooltip(title).build())
                .child(MusicWorkspaceComposition.deviceLocation(position, coordinates, visit))
                .child(MusicWorkspace.tools("member-actions").children(List.copyOf(actions)).build()).build();
        var key = UiKey.item(id.toString());
        return withSummaries(new UiContainer(key, card.layout(), card.children(), card.style(), card.sizing()));
    }

    static UiNode members(List<UiNode> source, Component empty, MusicSourcePosition position, Consumer<MusicSourcePosition> read) {
        List<UiNode> members = List.copyOf(source);
        if (members.isEmpty()) return Ui.text(empty).key("empty").singleLine().build();
        return Ui.document().key("group-members").blocks(members).readingBody()
                .position(new UiReadingPosition(position.anchor(), 0, position.index()),
                        next -> read.accept(new MusicSourcePosition(next.block().orElse(UiKey.AUTO), next.index())))
                .sizing(new UiSizing(UiSize.ZERO, new UiSize(640, 320))).build();
    }

    private static UiNode withSummaries(UiNode node) {
        if (node instanceof UiElement element && element.content() instanceof UiContent.Text) return element.withSummaryLines(1);
        if (node instanceof UiContainer container) return new UiContainer(container.key(), container.layout(), container.children().stream()
                .map(child -> new UiChild(child.scope(), withSummaries(child.node()), child.linear(), child.grid())).toList(), container.style(), container.sizing());
        return node;
    }
}
