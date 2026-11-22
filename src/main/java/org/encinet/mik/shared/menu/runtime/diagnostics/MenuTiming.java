package org.encinet.mik.shared.menu.runtime.diagnostics;

import jdk.jfr.Category;
import jdk.jfr.Enabled;
import jdk.jfr.Event;
import jdk.jfr.EventType;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;

@Name("org.encinet.mik.SpatialMenu")
@Label("Spatial Menu Work")
@Category({"MIK", "Spatial Menu"})
@Enabled(false)
@StackTrace(false)
public final class MenuTiming extends Event {
    private static final EventType TYPE = EventType.getEventType(MenuTiming.class);

    @Label("Phase") private String phase;
    @Label("Outcome") private String outcome;
    @Label("Work Items") private int count;

    public static MenuTiming start(String phase) {
        if (!TYPE.isEnabled()) return null;
        var event = new MenuTiming();
        event.phase = phase;
        event.begin();
        return event;
    }

    public static void finish(MenuTiming event) {
        if (event != null) event.commit();
    }

    public static void finish(MenuTiming event, String outcome, int count) {
        if (event == null) return;
        event.outcome = outcome;
        event.count = count;
        event.commit();
    }
}
