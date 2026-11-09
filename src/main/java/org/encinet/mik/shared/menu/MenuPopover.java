package org.encinet.mik.shared.menu;

import org.encinet.mik.shared.menu.ui.*;
import net.kyori.adventure.text.Component;
import java.util.Objects;

/** An explicitly opened companion of a source control; it never owns the parent's reading frame. */
public record MenuPopover(UiKey source, Component title, UiNode content, UiNode actions, Component closeLabel,
                          FloatingMenuCommand dismissed, boolean modal) {
    private static final String PREFIX = "$popover/";

    public MenuPopover {
        Objects.requireNonNull(source); Objects.requireNonNull(title); Objects.requireNonNull(content);
        Objects.requireNonNull(closeLabel); Objects.requireNonNull(dismissed);
        UiTree.check(content);
        if (actions != null) UiTree.check(actions);
    }

    public static MenuPopover attached(UiKey source, Component title, UiNode content, Component closeLabel, FloatingMenuCommand dismissed) {
        return new MenuPopover(source, title, content, null, closeLabel, dismissed, false);
    }

    public static MenuPopover attached(UiKey source, Component title, UiNode content, Component closeLabel) {
        return attached(source, title, content, closeLabel, (_,_) -> {});
    }

    public MenuPopover exclusive() { return new MenuPopover(source, title, content, actions, closeLabel, dismissed, true); }

    public MenuPopover withActions(UiNode value) {
        return new MenuPopover(source, title, content, Objects.requireNonNull(value), closeLabel, dismissed, modal);
    }

    public boolean sameScope(MenuPopover other) { return other != null && source.equals(other.source) && dismissed == other.dismissed; }

    /** The independent reading surface is centered on its complete local footprint. */
    public UiNode contentTree() {
        var header = UiTemplates.popoverHeader(title, actions, closeLabel, (_, handle) -> handle.closePopover(this));
        UiNode body = UiTemplates.window("popover", UiTemplates.focus(Ui.layout(UiLayouts.headerBody(UiLength.ui(8))).key("popover-body")
                .child(UiKey.named("header"), header).child(UiKey.named("body"), content).build(), UiKey.named("body")));
        return Ui.layout((scope, limits) -> {
            var result = UiLayouts.stack().layout(scope, limits);
            // Attachment and collision use the complete companion box; the independent budget
            // still follows the explicitly declared body beneath the persistent header.
            return new UiLayout.Result(result.size(), result.positions(), java.util.Optional.of(
                    new UiLayout.Position(result.size().width() / 2, result.size().height() / 2)));
        }).child(body).build();
    }

    public static String scoped(String path) { return PREFIX + path; }
    public static boolean owns(String path) { return path != null && path.startsWith(PREFIX); }
}
