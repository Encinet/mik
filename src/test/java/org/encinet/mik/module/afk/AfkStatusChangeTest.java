package org.encinet.mik.module.afk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AfkStatusChangeTest {

    @Test
    void distinguishesModifiedClearedAndUnchangedStatuses() {
        assertEquals(AfkModule.AfkStatusChange.MODIFIED,
                AfkModule.classifyStatusChange(null, "稍后回来"));
        assertEquals(AfkModule.AfkStatusChange.MODIFIED,
                AfkModule.classifyStatusChange("吃饭中", "稍后回来"));
        assertEquals(AfkModule.AfkStatusChange.CLEARED,
                AfkModule.classifyStatusChange("稍后回来", null));
        assertEquals(AfkModule.AfkStatusChange.UNCHANGED,
                AfkModule.classifyStatusChange("稍后回来", "稍后回来"));
        assertEquals(AfkModule.AfkStatusChange.UNCHANGED,
                AfkModule.classifyStatusChange(null, null));
    }
}
