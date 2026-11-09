package org.encinet.mik.shared.menu.runtime;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;
import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class MenuDisclosurePublicationTest {
    @Test void foldingRevokesBodyActionsAndClosesItsCompanionBeforeReadingOldContent() {
        var rendered = new AtomicInteger(); var closed = new AtomicInteger(); var redraw = new AtomicInteger();
        var callback = new AtomicReference<Runnable>();
        var panel = new MenuPopoverPanel("disclosure",UiKey.named("identity-source"),Component.text("Close"),_ -> redraw.incrementAndGet());
        panel.initialize(refresh -> {
            callback.set(refresh);
            return new MenuPanel() {
                public MenuScene render() { rendered.incrementAndGet(); return MenuScene.screen("identity-input")
                        .content(Ui.text(Component.text("Current draft")).build()).build(); }
                public void close() { closed.incrementAndGet(); }
            };
        });
        var layout = new MenuLayoutState(); var open = layout.measure(scene(true),1,FloatingMenuFieldOfView.DEFAULT,1);
        assertNotNull(panel.compose(open.definition(),open.frame()).popover());
        assertNotNull(entry(open.definition(),"identity-source"));
        var folded = layout.measure(scene(false),1,FloatingMenuFieldOfView.DEFAULT,1);
        assertNull(entry(folded.definition(),"identity-source"));
        assertNull(panel.compose(folded.definition(),folded.frame()).popover());
        callback.get().run(); panel.close();
        assertEquals(1,rendered.get()); assertEquals(1,closed.get()); assertEquals(0,redraw.get());
        assertNotNull(entry(folded.definition(),"toggle")); assertNotNull(entry(folded.definition(),"save"));
    }

    @Test void reversingExpansionUsesTheCurrentPoseWithoutRecompilingOrRestartingOnRefresh() {
        var layout = new MenuLayoutState();
        var closed = layout.measure(scene(false),1,FloatingMenuFieldOfView.DEFAULT,1);
        var open = layout.measure(scene(true),1,FloatingMenuFieldOfView.DEFAULT,1);
        var end = layout.measure(scene(false),1,FloatingMenuFieldOfView.DEFAULT,1);
        String path = element(closed.frame(),"save").path();
        var from = closed.poses().get(path); var to = open.poses().get(path);
        assertNotEquals(from,to); assertEquals(from,end.poses().get(path));
        var motion = new MenuPoseTransition(); motion.snap(from,0);
        assertEquals(from,motion.advance(to,1,10,FloatingMenuEasing.CUBIC_OUT,MenuPoseTransition.Path.LINEAR));
        var current = motion.sample(4); assertNotEquals(from,current); assertNotEquals(to,current);
        assertEquals(current,motion.advance(from,4,10,FloatingMenuEasing.CUBIC_OUT,MenuPoseTransition.Path.LINEAR));
        motion.advance(from,5,10,FloatingMenuEasing.CUBIC_OUT,MenuPoseTransition.Path.LINEAR);
        assertEquals(from,motion.sample(14),"A same-target refresh does not extend the transition");
        assertEquals(to,motion.advance(to,15,0,FloatingMenuEasing.CUBIC_OUT,MenuPoseTransition.Path.LINEAR),"Reduced motion adopts the same final layout");
        assertTrue(MenuLayoutMotion.envelope(java.util.List.of(new MenuLayoutMotion.Move(current,from,
                end.sizes().get(path),.05,MenuPoseTransition.Path.LINEAR))).isPresent());
        assertEquals(closed.frame().size(),end.frame().size());
        assertDisjoint(open.frame()); assertDisjoint(end.frame());
    }

    private static MenuScene scene(boolean expanded) {
        var group = UiTemplates.disclosure(UiKey.named("identity"),Ui.text(Component.text("Name and alias")).key("summary").build(),
                Ui.button(expanded ? UiIcons.CHEVRON_UP : UiIcons.CHEVRON_DOWN,Component.text(expanded ? "Collapse" : "Expand"))
                        .key("toggle").selected(expanded).primary((_,_) -> {}).build(),expanded,
                () -> Ui.column().child(Ui.button(Component.text("Edit alias")).key("identity-source").primary((_,_) -> {}).build())
                        .child(Ui.text(Component.text("Current name\nCurrent alias")).key("value").build()).build());
        return MenuScene.screen("disclosure").withoutTitle().content(UiTemplates.window("settings",Ui.column().child(group)
                .child(Ui.button(Component.text("Save")).key("save").primary((_,_) -> {}).build()).build())).build();
    }
}
