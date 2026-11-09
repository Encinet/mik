package org.encinet.mik.feature.world.vehicle;

import org.encinet.mik.feature.world.vehicle.VehicleEditorRoute.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VehicleEditorRouteTest {
    @Test void aToolReturnsToItsObjectAndTheObjectReturnsToItsSourcePosition() {
        var target = new ComponentRef(Section.PARTS, 14);
        var route = new Transform(target, VehicleEditorTransform.Mode.ROTATE);
        assertEquals(new Inspector(target), route.back());
        assertEquals(new Components(Section.PARTS, 14), route.back().back());
        assertEquals(Workspace.INSTANCE, route.back().back().back());
        assertEquals(new Library(0), Workspace.INSTANCE.back());
        assertEquals(14, route.index());
        assertEquals(new Library(3), new Model("car", 3).back());
    }

    @Test void pagingAndComponentIdentityCannotBeConfusedAndUnsupportedToolsCannotBeOpened() {
        assertThrows(IllegalArgumentException.class, () -> new ComponentRef(Section.PARTS, -1));
        assertThrows(IllegalArgumentException.class, () -> new Components(Section.PARTS, -1));
        assertThrows(IllegalArgumentException.class, () -> new Position(new ComponentRef(Section.PARTS, 0), true));
        assertThrows(IllegalArgumentException.class, () -> new Transform(new ComponentRef(Section.SEATS, 0), VehicleEditorTransform.Mode.ROTATE));
        assertThrows(IllegalArgumentException.class, () -> new Transform(new ComponentRef(Section.PARTS, 0), VehicleEditorTransform.Mode.SIZE));
        assertDoesNotThrow(() -> new Position(new ComponentRef(Section.SEATS, 0), true));
        assertDoesNotThrow(() -> new Transform(new ComponentRef(Section.COLLIDERS, 0), VehicleEditorTransform.Mode.SIZE));
    }
}
