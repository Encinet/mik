package org.encinet.mik.shared.menu.runtime;

import java.util.concurrent.atomic.AtomicLong;
import org.encinet.mik.shared.menu.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.encinet.mik.shared.menu.runtime.MenuPopoverInput.Activation.*;

class MenuPopoverInputTest {
    @Test void explicitLayersConsumeBlankClicksAndPermitVisibleParentActionsExactlyOnce() {
        var scope = new MenuPopoverInput("person-a", false);
        assertFalse(scope.allows("covered-parent", true));
        assertTrue(scope.allows("person-b", false));
        assertTrue(scope.allows(MenuPopover.scoped("action"), true));
        assertEquals(CONSUME, scope.activation(null, true, false));
        assertEquals(DISMISS, scope.activation(null, false, false));
        assertEquals(DISMISS, scope.activation("person-a", false, false));
        assertEquals(DISMISS_AND_ACT, scope.activation("person-b", false, false));
        assertEquals(ACT, scope.activation(MenuPopover.scoped("action"), true, false));

        var time = new AtomicLong();
        var gate = new MenuInputGate(time::get);
        assertTrue(gate.accept("person-b", FloatingMenuInteraction.PRIMARY));
        gate.dispatch(gate::contentChanged);
        assertFalse(gate.accept("button-now-exposed", FloatingMenuInteraction.PRIMARY));
        time.addAndGet(55_000_000);
        assertTrue(gate.accept("button-now-exposed", FloatingMenuInteraction.PRIMARY));
    }

    @Test void confirmationLocksTheParentAndHasNoImplicitBackgroundFallback() {
        var scope = new MenuPopoverInput("resign", true);
        assertFalse(scope.allows("resign", false));
        assertEquals(CONSUME, scope.activation("resign", false, false));
        assertEquals(CONSUME, scope.activation("other-parent", false, true));
        assertTrue(scope.allows(MenuPopover.scoped("cancel"), true));
        assertEquals(ACT, scope.activation(MenuPopover.scoped("cancel"), true, false));
        assertEquals(DISMISS, scope.activation(null, false, false));
    }

    @Test void wheelInputOverReadingTextAndBlankSpaceStaysInItsOwnLayer() {
        var scope = new MenuPopoverInput("identity",false);
        assertEquals(ACT,scope.activation(null,true,true),"read-only content has no button target");
        assertEquals(MenuPopoverInput.ScrollScope.POPOVER,scope.scrollScope(null,true));
        assertEquals(MenuPopoverInput.ScrollScope.POPOVER,scope.scrollScope(MenuPopover.scoped("next"),false));
        assertEquals(MenuPopoverInput.ScrollScope.PARENT,scope.scrollScope(null,false));
        assertEquals(DISMISS_AND_ACT,scope.activation(null,false,true));
        var modal = new MenuPopoverInput("delete",true);
        assertEquals(MenuPopoverInput.ScrollScope.BLOCKED,modal.scrollScope(null,false));
        assertEquals(CONSUME,modal.activation(null,false,true));
    }
}
