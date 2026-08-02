package org.encinet.mik.module.presentation;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AxiomGizmoServiceTest {
    @Test
    void encodesAxiomsVanillaUuidCollectionPayload() {
        UUID first = UUID.fromString("01234567-89ab-cdef-fedc-ba9876543210");
        UUID second = UUID.fromString("11111111-2222-3333-4444-555555555555");

        byte[] encoded = AxiomGizmoService.encode(List.of(first, second));
        ByteBuffer payload = ByteBuffer.wrap(encoded);

        assertEquals(2, Byte.toUnsignedInt(payload.get()));
        assertEquals(first, new UUID(payload.getLong(), payload.getLong()));
        assertEquals(second, new UUID(payload.getLong(), payload.getLong()));
        assertEquals(0, payload.remaining());
    }

    @Test
    void encodesAnEmptyReplacementSet() {
        assertArrayEquals(new byte[]{0}, AxiomGizmoService.encode(List.of()));
    }

    @Test
    void combinesIndependentProducerContributions() {
        UUID menu = UUID.randomUUID();
        UUID firstAfk = UUID.randomUUID();
        UUID secondAfk = UUID.randomUUID();

        assertEquals(Set.of(menu, firstAfk, secondAfk), AxiomGizmoService.merge(List.of(
                Set.of(menu), Set.of(firstAfk, secondAfk), Set.of(menu))));
    }
}
