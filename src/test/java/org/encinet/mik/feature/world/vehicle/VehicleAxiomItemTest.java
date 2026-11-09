package org.encinet.mik.feature.world.vehicle;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.TagParser;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VehicleAxiomItemTest {
    @Test void pickedBlockItemImportsNestedGroupWithoutApplyingParentTransformAgain() throws Exception {
        CompoundTag data = TagParser.parseCompoundFully("""
                {Axiom:{EntityPlacer:{id:"minecraft:block_display",block_state:{Name:"minecraft:oak_log",Properties:{axis:"x"}},
                  transformation:{translation:[2f,0f,0f],left_rotation:[0f,0f,0f,1f],scale:[2f,1f,1f],right_rotation:[0f,0f,0f,1f]},
                  Passengers:[{id:"minecraft:block_display",block_state:{Name:"minecraft:stone"},
                    Rotation:[90f,0f],transformation:[1f,0f,0f,3f,0f,1f,0f,0f,0f,0f,1f,0f,0f,0f,0f,1f],
                    brightness:{block:15,sky:4},billboard:"fixed",
                    Passengers:[{id:"minecraft:block_display",block_state:{Name:"minecraft:glass"}}]}]}}}
                """);
        CompoundTag original = data.copy();
        var parts = VehicleAxiomItem.parts(data, NbtOps.INSTANCE);
        assertEquals(3, parts.size());
        assertEquals("minecraft:oak_log[axis=x]", parts.getFirst().payload());
        assertTrue(new Matrix4f().translation(2, 0, 0).scale(2, 1, 1).equals(parts.getFirst().transform(), 1e-5f));
        assertTrue(new Matrix4f().rotateY((float) -Math.PI / 2).translate(3, 0, 0).equals(parts.get(1).transform(), 1e-5f));
        assertTrue(new Matrix4f().equals(parts.get(2).transform(), 1e-5f));
        assertEquals(15, parts.get(1).blockLight());
        assertEquals(4, parts.get(1).skyLight());
        assertEquals(original, data, "Import must not mutate the held item's data");
        for (var kind : VehicleDefinition.Kind.values())
            assertEquals(3, VehicleDefinition.preset("held_group", kind, parts, VehicleModel.bounds(parts)).parts().size());
    }

    @Test void ordinaryItemsUnsupportedPassengersAndMalformedTransformsRejectWholeImport() throws Exception {
        for (String source : new String[]{"{}", "{Axiom:{CustomBlockPlacer:'example:block'}}",
                "{Axiom:{EntityPlacer:{id:'minecraft:pig'}}}",
                "{Axiom:{EntityPlacer:{id:'minecraft:block_display',block_state:{Name:'minecraft:stone'},Passengers:[{id:'minecraft:pig'}]}}}",
                "{Axiom:{EntityPlacer:{id:'minecraft:block_display',block_state:{Name:'minecraft:stone'},transformation:[1f,2f]}}}",
                "{Axiom:{EntityPlacer:{id:'minecraft:block_display',block_state:{Name:'minecraft:stone'},Passengers:[1]}}}"}) {
            CompoundTag data = TagParser.parseCompoundFully(source);
            assertThrows(VehicleImportException.class, () -> VehicleAxiomItem.parts(data, NbtOps.INSTANCE), source);
        }
    }

    @Test void displayLimitIncludesNestedMembers() throws Exception {
        CompoundTag root = TagParser.parseCompoundFully("{id:'minecraft:block_display',block_state:{Name:'minecraft:stone'}}");
        ListTag passengers = new ListTag();
        for (int i = 0; i < 127; i++) passengers.add(root.copy());
        root.put("Passengers", passengers);
        CompoundTag axiom = new CompoundTag();
        axiom.put("EntityPlacer", root);
        CompoundTag data = new CompoundTag();
        data.put("Axiom", axiom);
        assertEquals(128, VehicleAxiomItem.parts(data, NbtOps.INSTANCE).size());
        passengers.add(TagParser.parseCompoundFully("{id:'minecraft:block_display',block_state:{Name:'minecraft:stone'}}"));
        assertThrows(VehicleImportException.class, () -> VehicleAxiomItem.parts(data, NbtOps.INSTANCE));
    }
}
