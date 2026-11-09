package org.encinet.mik.shared.menu.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.encinet.mik.shared.menu.FloatingMenuDecoration;
import org.encinet.mik.shared.menu.FloatingMenuSize;
import org.encinet.mik.shared.menu.ui.UiRotation;
import org.encinet.mik.shared.menu.ui.UiVector3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MenuExitVisualsTest {
    private static final MenuSurfaceAttachment.Surface BOUNDS = new MenuSurfaceAttachment.Surface(new UiVector3(0,64,3),
            UiRotation.IDENTITY,new FloatingMenuSize(2,1),.25,.28);

    private static MenuVisualSnapshot part(int id) {
        return new MenuVisualSnapshot(id,UUID.randomUUID(),new Location(null,0,64,3),
                new MenuVisualSnapshot.Text(Component.text("Retired label"),0x99000000,2,1,120,.25f,
                        FloatingMenuDecoration.Alignment.LEFT,false,.8,.6,UiRotation.IDENTITY));
    }

    @Test void removedDrawingsFadeOnTheOwnerClockAndExpireWithoutOwningInput() {
        var exits = new MenuExitVisuals();
        var part = part(1);
        assertTrue(exits.retain(List.of(part),BOUNDS,10,4,2047));
        assertEquals(java.util.Set.of(part.uuid()),exits.uuids());
        var destroyed = new ArrayList<Integer>();
        var samples = new ArrayList<Double>();
        java.util.function.Consumer<List<MenuVisualSnapshot>> destroy = parts -> parts.forEach(value -> destroyed.add(value.entityId()));
        for (long tick : new long[]{10,12,12}) exits.advance(tick,2047,false,_ -> true,destroy,(_,remaining) -> samples.add(remaining));
        assertEquals(List.of(1.0,.125,.125),samples);
        assertTrue(destroyed.isEmpty());
        exits.advance(14,2047,false,_ -> fail("Expired drawings must not sample the world"),destroy,(_,_) -> fail("Expired drawing"));
        assertEquals(List.of(1),destroyed);
        assertEquals(0,exits.size());
        exits.clear(destroy);
        assertEquals(List.of(1),destroyed);
    }

    @Test void budgetPressureOcclusionAndReducedMotionDisposeTheCopyImmediately() {
        for (int reason = 0; reason < 3; reason++) {
            var exits = new MenuExitVisuals();
            assertTrue(exits.retain(List.of(part(1)),BOUNDS,0,4,2000));
            var removed = new ArrayList<MenuVisualSnapshot>();
            int variant = reason;
            exits.advance(1,reason == 0 ? 2048 : 2000,reason == 1,_ -> variant != 2,removed::addAll,
                    (_,_) -> fail("An unsafe or unwanted copy must not draw"));
            assertEquals(1,removed.size()); assertEquals(0,exits.size());
        }
        var exits = new MenuExitVisuals();
        var parts = java.util.stream.IntStream.range(0,MenuExitVisuals.MAX_ENTITIES).mapToObj(MenuExitVisualsTest::part).toList();
        assertTrue(exits.retain(parts,BOUNDS,0,100,1900));
        assertFalse(exits.retain(List.of(part(200)),BOUNDS,0,4,1900));
        var removed = new ArrayList<MenuVisualSnapshot>();
        exits.advance(MenuExitVisuals.MAX_TICKS,1900,false,_ -> true,removed::addAll,(_,_) -> fail("Bounded lifetime"));
        assertEquals(MenuExitVisuals.MAX_ENTITIES,removed.size());
    }

    @Test void clearingRetiresEveryCopyEvenWhenOnePacketSendFails() {
        var exits = new MenuExitVisuals();
        assertTrue(exits.retain(List.of(part(1)),BOUNDS,0,4,100));
        assertTrue(exits.retain(List.of(part(2)),BOUNDS,0,4,100));
        var retired = new ArrayList<Integer>();
        assertThrows(IllegalStateException.class,() -> exits.clear(parts -> {
            retired.add(parts.getFirst().entityId());
            throw new IllegalStateException("Packet transport unavailable");
        }));
        assertEquals(List.of(1,2),retired);
        assertEquals(0,exits.size());
        exits.clear(_ -> fail("Already retired"));
    }

    @Test void snapshotsDefendTheirLocationAndPreserveTheActualPublishedPaint() {
        var snapshot = part(1);
        snapshot.location().add(100,100,100);
        assertEquals(64,snapshot.location().getY());
        var renderer = mock(VirtualMenuEntityRenderer.class);
        snapshot.paint().render(renderer,null,1,.5);
        verify(renderer).text(null,1,Component.text("Retired label"),0x99000000,2f,1f,120,.25f,
                FloatingMenuDecoration.Alignment.LEFT,false,.4,.6,UiRotation.IDENTITY);
    }
}
