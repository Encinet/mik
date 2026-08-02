package org.encinet.mik.module.communication.tip;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TipSelectorTest {

    private final TipMarkupParser parser = new TipMarkupParser();
    private final TipSelector selector = new TipSelector();

    @Test
    void contextualSelectionRequiresBothSceneAndTopic() {
        TipEntry home = tip("home", Set.of("home"), Set.of(TipScene.CHAT));
        TipEntry spawn = tip("spawn", Set.of("spawn"), Set.of(TipScene.CHAT));
        TipEntry rotationOnly = tip("rotation", Set.of("home"), Set.of());

        TipEntry selected = selector.select(List.of(home, spawn, rotationOnly),
                TipScene.CHAT, "home", Map.of(), 1_000L, new Random(1)).orElseThrow();

        assertEquals(home, selected);
    }

    @Test
    void aContextualTipAlsoParticipatesInPeriodicRotation() {
        TipEntry respawn = tip("respawn", Set.of("teleport"), Set.of(TipScene.RESPAWN));

        assertEquals(respawn, selector.select(List.of(respawn), TipScene.PERIODIC,
                null, Map.of(), 1_000L, new Random(1)).orElseThrow());
    }

    @Test
    void automaticScenesRespectRepeatWindowWhileManualRequestsRemainAvailable() {
        TipEntry home = tip("home", Set.of("home"), Set.of(TipScene.CHAT));
        long now = 10_000L;
        Map<String, Long> seen = Map.of(home.id(), now - 1_000L);

        assertTrue(selector.select(List.of(home), TipScene.CHAT, "home",
                seen, now, new Random(1)).isEmpty());
        assertEquals(home, selector.select(List.of(home), TipScene.MANUAL, null,
                seen, now, new Random(1)).orElseThrow());
    }

    private TipEntry tip(String id, Set<String> topics, Set<TipScene> scenes) {
        return new TipEntry(id, topics, scenes, parser.parse("Use <command>/" + id
                + "</command>"));
    }
}
