package org.encinet.mik.shared.menu.ui;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class UiIconTest {
    @Test void theWholeCatalogHasBoundedImmutableFramesAndStablePartIdentities() throws Exception {
        int count = 0;
        for (var field : UiIcons.class.getFields()) if (field.getType() == UiIcon.class) {
            var icon = (UiIcon) field.get(null); count++;
            assertTrue(icon.partCount() <= UiIcon.MAX_PARTS, field.getName());
            for (long tick = -2; tick < 130; tick++) {
                assertEquals(icon.partCount(), icon.sample(tick,false).parts().size());
                assertSame(icon.sample(0,true), icon.sample(tick,true));
            }
            assertThrows(UnsupportedOperationException.class, () -> icon.frames().clear());
            assertThrows(UnsupportedOperationException.class, () -> icon.frames().getFirst().parts().clear());
        }
        assertEquals(32,count);
    }

    @Test void invalidArtworkFailsBeforeItCanAllocateAnEntity() {
        var one = new UiIcon.Frame(List.of(UiIcon.Part.rectangle(5,5,4,4)));
        var two = new UiIcon.Frame(List.of(UiIcon.Part.rectangle(5,5,4,4), UiIcon.Part.rectangle(10,5,4,4)));
        assertThrows(IllegalArgumentException.class, () -> new UiIcon(List.of(one,two),2,0));
        assertThrows(IllegalArgumentException.class, () -> new UiIcon(List.of(one),1,0));
        assertThrows(IllegalArgumentException.class, () -> new UiIcon(List.of(one),2,1));
        assertThrows(IllegalArgumentException.class, () -> new UiIcon(List.of(),2,0));
        assertThrows(IllegalArgumentException.class, () -> new UiIcon.Frame(java.util.Collections.nCopies(UiIcon.MAX_PARTS+1,one.parts().getFirst())));
        assertThrows(IllegalArgumentException.class, () -> UiIcon.Part.rectangle(23,23,3,3));
        assertThrows(IllegalArgumentException.class, () -> new UiIcon.Part(1,1,10,2,45,1));
        assertThrows(IllegalArgumentException.class, () -> UiIcon.Part.line(4,4,4,4));
        assertThrows(IllegalArgumentException.class, () -> UiIcon.Part.rectangle(Double.NaN,3,2,2));
    }

    @Test void activityAndLoadingAdvanceAtTenHertzAndRetainDistinctStillShapes() {
        for (var icon : List.of(UiIcons.ACTIVITY_BARS,UiIcons.LOADING)) {
            assertSame(icon.sample(0,false),icon.sample(1,false));
            assertNotEquals(icon.sample(0,false),icon.sample(2,false));
            assertEquals(icon.sample(0,true),icon.sample(10_000,true));
        }
        assertEquals(3,UiIcons.ACTIVITY_BARS.partCount());
        assertEquals(2,UiIcons.PAUSE.partCount());
        assertEquals(8,UiIcons.LOADING.partCount());
    }
}
