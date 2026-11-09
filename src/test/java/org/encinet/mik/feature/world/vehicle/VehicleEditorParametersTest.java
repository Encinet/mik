package org.encinet.mik.feature.world.vehicle;

import java.util.HashSet;
import java.util.List;
import org.encinet.mik.shared.i18n.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VehicleEditorParametersTest {
    @Test void groupsCoverEachEditableFieldOnceAndOnlyExposeApplicableTypeSettings() {
        var fields = new HashSet<String>();
        for (var group : VehicleEditorParameters.Group.values()) for (String field : VehicleEditorParameters.fields(group)) assertTrue(fields.add(field));
        var expected = new HashSet<>(VehicleModelDraft.SCALARS);
        expected.addAll(VehicleModelDraft.VECTORS); expected.addAll(List.of("kind", "engine.ratios"));
        assertEquals(expected, fields);
        assertFalse(VehicleEditorParameters.groups("CAR").contains(VehicleEditorParameters.Group.BOAT));
        assertFalse(VehicleEditorParameters.groups("BOAT").contains(VehicleEditorParameters.Group.PLANE));
        assertTrue(VehicleEditorParameters.groups("PLANE").contains(VehicleEditorParameters.Group.PLANE));
        assertFalse(VehicleEditorParameters.visibleFields(VehicleEditorParameters.Group.ENGINE, "BOAT").contains("engine.ratios"));
        assertFalse(VehicleEditorParameters.visibleFields(VehicleEditorParameters.Group.BODY, "PLANE").contains("grip"));
    }

    @Test void speedFormsUseKilometersPerHourAndPersistTheSamePhysicsUnitAsCommands() {
        var draft = new VehicleModelDraft(VehicleFixtures.definition(VehicleDefinition.Kind.CAR));
        assertEquals("144", VehicleEditorParameters.display(draft, "maximum-speed"));
        assertEquals(20, Double.parseDouble(VehicleEditorParameters.stored("maximum-speed", "72")));
        for (double speed : new double[]{.1, .12345678901234, 40, 65, 80}) {
            draft.set("maximum-speed", Double.toString(speed));
            assertEquals(speed, Double.parseDouble(VehicleEditorParameters.stored("maximum-speed", VehicleEditorParameters.display(draft, "maximum-speed"))), 1.0E-12);
        }
        assertThrows(IllegalArgumentException.class, () -> VehicleEditorParameters.stored("maximum-speed", "NaN"));
    }

    @Test void detachedFormValidationRejectsOutOfRangeValuesWithoutTouchingHistoryAndAllowsIncompleteStructures() {
        var draft = new VehicleModelDraft(VehicleFixtures.definition(VehicleDefinition.Kind.CAR));
        var original = draft.validate();
        for (var field : VehicleDefinition.SCALAR_LIMITS.entrySet()) {
            assertThrows(IllegalArgumentException.class, () -> draft.check(next -> next.set(field.getKey(), Double.toString(field.getValue() * 2))));
            assertDoesNotThrow(() -> draft.check(next -> next.set(field.getKey(), Double.toString(field.getValue()))));
        }
        assertThrows(IllegalArgumentException.class, () -> draft.check(next -> next.set("inertia", "1 -1 1")));
        assertThrows(IllegalArgumentException.class, () -> draft.check(next -> next.set("center-of-mass", "0 40 0")));
        assertThrows(IllegalArgumentException.class, () -> draft.check(next -> next.set("engine.ratios", "21 2 1")));
        assertEquals(original, draft.validate()); assertEquals(0, draft.revision()); assertFalse(draft.canUndo());
        draft.remove("seats", 0);
        assertDoesNotThrow(() -> draft.check(next -> next.set("mass", "1800")));
        assertEquals(original.mass(), Double.parseDouble(draft.field("mass")));
    }

    @Test void everyFieldAndActualChoiceHasAResolvedLocalizedLabelInEveryLanguage() throws Exception {
        for (var language : Language.values()) {
            var bundle = FluentTestSupport.bundle(language);
            for (var group : VehicleEditorParameters.Group.values()) {
                for (String field : VehicleEditorParameters.fields(group)) {
                    String key = field.replace('.', '-');
                    assertNotEquals(key, FluentTestSupport.text(bundle, Message.VEHICLE_MODEL_FIELD, key), language + " / " + key);
                }
                for (var kind : VehicleDefinition.Kind.values()) {
                    String key = VehicleEditorParameters.labelKey(group, kind.name());
                    assertNotEquals(key, FluentTestSupport.text(bundle, Message.VEHICLE_MODEL_PARAMETER_GROUP, key));
                    assertNotEquals(key, FluentTestSupport.text(bundle, Message.VEHICLE_MODEL_PARAMETER_HELP, key));
                }
            }
            for (var property : VehicleEditorProperty.values()) {
                assertNotEquals(property.key(), FluentTestSupport.text(bundle, Message.VEHICLE_MODEL_FIELD, property.key()));
                for (String choice : property.choices()) {
                    String key = property.choiceKey(choice);
                    String label = FluentTestSupport.text(bundle, Message.VEHICLE_MODEL_CHOICE, key);
                    assertFalse(label.contains("{$")); assertFalse(label.isBlank());
                    if (key.length() > 8) assertNotEquals(key, label, language + " / " + key);
                }
            }
            assertNotEquals(FluentTestSupport.text(bundle, Message.VEHICLE_MODEL_CHOICE, "CENTER"),
                    FluentTestSupport.text(bundle, Message.VEHICLE_MODEL_CHOICE, "TEXT_CENTER"));
        }
    }
}
