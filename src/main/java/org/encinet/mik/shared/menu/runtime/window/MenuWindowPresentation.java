package org.encinet.mik.shared.menu.runtime.window;

import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.ui.*;

/** Visual treatment is independent of the group's ownership, focus and movement capabilities. */
final class MenuWindowPresentation {
    private MenuWindowPresentation() { }

    /** Keep the authored planes and reading focus; management controls add no enclosing surface. */
    static UiNode workspace(Component title, UiNode content, UiNode controls) {
        var sections = Ui.layout(UiLayouts.contentColumn(UiKey.named("body"),UiLength.ui(8))).key("workspace-content");
        if (!title.equals(Component.empty())) sections.child(Ui.heading(title).key("workspace-title").build());
        sections.child(UiKey.named("body"),content);
        if (!(controls instanceof UiBranch branch) || !branch.children().isEmpty()) sections.child(controls);
        return UiTemplates.focus(sections.build(),UiKey.named("body"));
    }

    /** Only an explicitly opened companion receives a host-provided frame. */
    static UiNode floating(Component title, UiNode content, UiNode controls, boolean focused, Runnable activate) {
        UiNode header = title.equals(Component.empty()) ? controls : UiTemplates.titleBar(
                Ui.button(title).key("title").singleLine().textWidth(240).selected(focused)
                        .primary((_,_) -> activate.run()).build(), controls);
        var body = Ui.layout(UiLayouts.headerBody(UiLength.ui(8))).key("window-body")
                .child(UiKey.named("header"),header).child(UiKey.named("body"),content).build();
        return Ui.surface(body).key("surface").style(UiTheme.FLOATING_WINDOW)
                .facing(UiSurfaceFacing.FORWARD).build();
    }
}
