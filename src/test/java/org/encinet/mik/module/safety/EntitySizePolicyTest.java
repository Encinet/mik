package org.encinet.mik.module.safety;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntitySizePolicyTest {

    @Test
    void sizeScaleAndPassengerLimitsAreEnforcedIndependently() {
        assertTrue(EntitySizePolicy.hasOversizedSummonData(
                "slime ~ ~ ~ {Size:120000}"));
        assertTrue(EntitySizePolicy.hasOversizedSummonData(
                "slime ~ ~ ~ {attributes:[{id:\"minecraft:scale\",base:120000}]}"));
        assertTrue(EntitySizePolicy.hasOversizedSummonData(
                "pig ~ ~ ~ {Passengers:[{id:\"minecraft:slime\",Size:120000}]}"));
        assertTrue(EntitySizePolicy.hasOversizedSummonData(
                "slime ~ ~ ~ {attributes:[{id:\"minecraft:scale\",base:1,"
                        + "modifiers:[{id:\"test:huge\",amount:100,operation:\"add_value\"}]}]}"));
        assertTrue(EntitySizePolicy.hasOversizedSummonData(
                "slime ~ ~ ~ {attributes:[{id:\"minecraft:scale\",base:3,"
                        + "modifiers:[{id:\"test:large\",amount:1,"
                        + "operation:\"minecraft:add_multiplied_base\"}]}]}"));
    }

    @Test
    void ordinaryEntityDataIsAllowed() {
        assertFalse(EntitySizePolicy.hasOversizedSummonData(
                "slime ~ ~ ~ {Size:4,attributes:[{id:\"minecraft:scale\",base:2}],"
                        + "CustomName:'{\"text\":\"Size:120000\"}'}"));
        assertFalse(EntitySizePolicy.hasOversizedGiveData(
                "@s slime_spawn_egg[entity_data={id:\"minecraft:slime\",Size:4}]"));
        assertFalse(EntitySizePolicy.hasOversizedSummonData("slime ~ ~ ~"));
    }
}
