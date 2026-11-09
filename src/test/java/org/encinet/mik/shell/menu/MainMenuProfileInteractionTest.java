package org.encinet.mik.shell.menu;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.runtime.SceneGeometryTestSupport;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MainMenuProfileInteractionTest {
    @Test void profileFactsAndAbbreviatedNamesOnlyOfferReadingHelpWhileIdentityRemainsTheSingleAction() {
        var name = Component.text("A very long player profile display name that is abbreviated");
        var calls = new AtomicInteger();
        var profile = new MainMenuProfile(Component.text("Avatar"),name,Component.text("Community member"),List.of(
                new MainMenuProfile.Fact("location",Component.text("Location"),Component.text("A long location description ".repeat(5))),
                new MainMenuProfile.Fact("online",Component.text("Online"),Component.text("3 hours"))));
        var scene = MenuScene.screen("profile").content(profile.content(Component.text("Manage identity"),(_,_) -> calls.incrementAndGet())).build();
        var frame = SceneGeometryTestSupport.measure(scene,1);
        int controls = 0;
        for (var element : frame.elements()) {
            var input = element.node().input();
            var node = UiSceneContent.entry(element.path(),element.node());
            assertEquals(input.pointerBehavior(),node.pointerBehavior());
            if (element.node().key().value().equals("identity")) {
                controls++;
                assertEquals(UiPointerBehavior.OPERATE,node.pointerBehavior());
                node.trigger(FloatingMenuInteraction.PRIMARY).execute(null,null,FloatingMenuInteraction.PRIMARY);
            } else {
                assertNotEquals(UiPointerBehavior.OPERATE,node.pointerBehavior(),element.path());
                assertTrue(node.triggers().isEmpty());
                if (element.node().key().value().equals("player-name")) {
                    if (node.label().equals(name)) assertNull(node.tooltip());
                    else {
                        assertEquals(UiPointerBehavior.INSPECT,node.pointerBehavior());
                        assertEquals(name,node.tooltip().text());
                    }
                }
            }
        }
        assertEquals(1,controls);
        assertEquals(1,calls.get());
    }
}
