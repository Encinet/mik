package org.encinet.mik.module.communication.tip;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TipSelectorTest {

    private final TipMarkupParser parser = new TipMarkupParser();
    private final TipSelector selector = new TipSelector();

    @Test
    void selectionRequiresTheRecognizedChatTopic() {
        TipEntry home = tip("home", Set.of("home"));
        TipEntry spawn = tip("spawn", Set.of("spawn"));

        TipEntry selected = selector.select(List.of(spawn, home), "home",
                Map.of(), 1_000L, 6_000L).orElseThrow();

        assertEquals(home, selected);
    }

    @Test
    void unseenTipsFollowCatalogOrderSoCoreAnswersComeFirst() {
        TipEntry core = tip("home-core", Set.of("home"));
        TipEntry advanced = tip("home-advanced", Set.of("home"));

        assertEquals(core, selector.select(List.of(core, advanced), "home",
                Map.of(), 10_000L, 6_000L).orElseThrow());
        assertEquals(advanced, selector.select(List.of(core, advanced), "home",
                Map.of(core.id(), 9_000L), 10_000L, 6_000L).orElseThrow());
    }

    @Test
    void seenTipsRespectTheRepeatWindow() {
        TipEntry home = tip("home", Set.of("home"));
        long now = 10_000L;
        Map<String, Long> seen = Map.of(home.id(), now - 1_000L);

        assertTrue(selector.select(List.of(home), "home", seen,
                now, 6_000L).isEmpty());
        assertEquals(home, selector.select(List.of(home), "home", seen,
                now + 5_000L, 6_000L).orElseThrow());
    }

    private TipEntry tip(String id, Set<String> topics) {
        return new TipEntry(id, topics, parser.parse("Use <command>/" + id
                + "</command>"));
    }
}
