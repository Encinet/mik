package org.encinet.mik.feature.world.vehicle;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VehicleSettingsTest {
    @TempDir
    Path directory;

    @Test
    void missingFileUsesDefaultsAndOnlyExplicitOverridesAreNeeded() throws Exception {
        Path path = directory.resolve("vehicles.yml");
        assertEquals(VehicleSettings.DEFAULT, VehicleSettings.load(path));
        assertFalse(Files.exists(path));

        Files.writeString(path, "maximum-instances: 8\ndisabled-worlds: [museum]\n");
        VehicleSettings settings = VehicleSettings.load(path);
        assertEquals(8, settings.maximumInstances());
        assertEquals(8, settings.maximumPerOwner());
        assertEquals(VehicleSettings.DEFAULT.maximumActive(), settings.maximumActive());
        assertEquals(VehicleSettings.DEFAULT.activationDistance(), settings.activationDistance());
        assertEquals(Set.of("museum"), settings.disabledWorlds());
    }

    @Test
    void rejectsInvalidOverridesRatherThanIgnoringThem() throws Exception {
        Path path = directory.resolve("vehicles.yml");
        Files.writeString(path, "maximum-instances: 8\nmaximum-per-owner: 9\n");
        assertThrows(IllegalArgumentException.class, () -> VehicleSettings.load(path));
        Files.writeString(path, "disabled-worlds: museum\n");
        assertThrows(IllegalArgumentException.class, () -> VehicleSettings.load(path));
        Files.writeString(path, "maximum-instance: 8\n");
        assertThrows(IllegalArgumentException.class, () -> VehicleSettings.load(path));
    }
}
