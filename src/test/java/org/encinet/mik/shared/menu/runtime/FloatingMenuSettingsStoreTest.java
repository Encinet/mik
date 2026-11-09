package org.encinet.mik.shared.menu.runtime;

import org.bukkit.configuration.file.YamlConfiguration;
import org.encinet.mik.shared.menu.FloatingMenuPreferences;
import org.encinet.mik.shared.menu.FloatingMenuScale;
import org.encinet.mik.shared.menu.FloatingMenuTextScale;
import org.encinet.mik.shared.menu.FloatingMenuFieldOfView;
import org.encinet.mik.shared.menu.ui.UiPalette;
import org.encinet.mik.shared.menu.ui.UiCustomTheme;
import org.encinet.mik.shared.menu.ui.UiColorRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FloatingMenuSettingsStoreTest {
    @TempDir Path directory;
    private final UUID player = UUID.randomUUID();
    private final java.util.List<FloatingMenuSettingsStore> stores = new java.util.ArrayList<>();
    @AfterEach void closeStores() { stores.forEach(FloatingMenuSettingsStore::close); }

    @Test void customThemePersistsEveryActualValueWithoutAnyBaseOrInheritance() {
        var custom = UiCustomTheme.copyOf(UiPalette.FOREST).withColor(UiColorRole.WINDOW, 0x123456, .37)
                .withColor(UiColorRole.BUTTON_HOVERED, 0x234567, .62);
        var selected = FloatingMenuPreferences.DEFAULT.withTheme(custom);
        var store = loaded();
        save(store, player, selected);
        assertEquals(selected, loaded().preferences(player));
        var yaml = YamlConfiguration.loadConfiguration(file());
        assertEquals("custom", yaml.getString(player + ".theme"));
        assertEquals(UiColorRole.values().length, yaml.getConfigurationSection(player + ".custom-theme").getKeys(false).size());
        assertEquals("#123456", yaml.getString(player + ".custom-theme.window.color"));
        assertEquals(.37, yaml.getDouble(player + ".custom-theme.window.opacity"));
        org.junit.jupiter.api.Assertions.assertFalse(yaml.contains(player + ".custom-theme.base"));
        assertEquals(UiPalette.DEFAULT, loaded().preferences(UUID.randomUUID()).theme());
        save(store, player, selected.withTheme(UiPalette.WARM).customizeTheme());
        assertEquals(UiCustomTheme.copyOf(UiPalette.WARM), loaded().preferences(player).theme());
    }

    @Test void incompleteCustomSnapshotsAreRejectedAsAWholeInsteadOfInheritingPresetValues() throws Exception {
        var store = loaded();
        save(store, player, FloatingMenuPreferences.DEFAULT.withTheme(UiPalette.FOREST).customizeTheme());
        var yaml = YamlConfiguration.loadConfiguration(file());
        yaml.set(player + ".custom-theme.window.opacity", null);
        yaml.save(file());
        assertEquals(UiPalette.DEFAULT, loaded().preferences(player).theme());
    }

    @Test void themeSurvivesReloadAndChangesToEveryOtherPreferenceWithoutAffectingAnotherPlayer() {
        var store = loaded();
        var selected = FloatingMenuPreferences.DEFAULT.withTheme(UiPalette.WARM)
                .withLayout(FloatingMenuScale.LARGE).withText(FloatingMenuTextScale.SMALL)
                .withFieldOfView(new FloatingMenuFieldOfView(85)).withReducedMotion(true);
        save(store, player, selected);
        var other = UUID.randomUUID();
        save(store, other, FloatingMenuPreferences.DEFAULT.withTheme(UiPalette.FOREST));
        var reloaded = loaded();
        assertEquals(selected, reloaded.preferences(player));
        assertEquals(UiPalette.FOREST, reloaded.preferences(other).theme());
        assertEquals(UiPalette.DEFAULT, reloaded.preferences(UUID.randomUUID()).theme());
        save(reloaded, player, selected.withTheme(UiPalette.DEFAULT));
        assertEquals(selected.withTheme(UiPalette.DEFAULT), loaded().preferences(player));
    }

    @Test void oldFilesAndUnknownThemesFallBackWithoutResettingOtherDimensions() throws Exception {
        var data = new YamlConfiguration();
        data.set(player + ".scale", FloatingMenuScale.LARGE.id());
        data.set(player + ".theme", "deleted-theme");
        java.nio.file.Files.createDirectories(file().toPath().getParent());
        data.save(file());
        assertEquals(UiPalette.DEFAULT, loaded().preferences(player).theme());
        assertEquals(FloatingMenuScale.LARGE, loaded().preferences(player).layout());
        data.set(player + ".theme", null);
        data.save(file());
        assertEquals(UiPalette.DEFAULT, loaded().preferences(player).theme());
    }

    @Test
    void bothIndependentChoicesSurviveReloadAndReset() {
        var store = loaded();
        assertEquals(FloatingMenuPreferences.DEFAULT, store.preferences(player));
        var selected = new FloatingMenuPreferences(FloatingMenuScale.MINIMUM, FloatingMenuTextScale.MAXIMUM);
        save(store, player, selected);
        var reloaded = loaded();
        assertEquals(selected, reloaded.preferences(player));
        save(reloaded, player, reloaded.preferences(player).withLayout(FloatingMenuScale.LARGE));
        assertEquals(selected.withLayout(FloatingMenuScale.LARGE), loaded().preferences(player));
        save(reloaded, player, reloaded.preferences(player).withText(FloatingMenuTextScale.SMALL));
        assertEquals(new FloatingMenuPreferences(FloatingMenuScale.LARGE, FloatingMenuTextScale.SMALL), loaded().preferences(player));
        save(reloaded, player, FloatingMenuPreferences.DEFAULT);
        assertEquals(FloatingMenuPreferences.DEFAULT, loaded().preferences(player));
    }

    @Test
    void choicesAndCachesAreIsolatedByPlayer() {
        UUID other = UUID.randomUUID();
        var store = loaded();
        var selected = new FloatingMenuPreferences(FloatingMenuScale.MAXIMUM, FloatingMenuTextScale.MINIMUM);
        save(store, player, selected);
        assertSame(store.preferences(player), store.preferences(player));
        assertEquals(FloatingMenuPreferences.DEFAULT, store.preferences(other));
        save(store, other, FloatingMenuPreferences.DEFAULT.withText(FloatingMenuTextScale.HUGE));
        assertEquals(selected, store.preferences(player));
        assertEquals(selected, loaded().preferences(player));
        assertEquals(FloatingMenuTextScale.HUGE, loaded().preferences(other).text());
    }

    @Test
    void malformedValuesFallBackOnlyForTheAffectedDimension() throws Exception {
        var store = loaded();
        var data = new YamlConfiguration();
        data.set(player + ".scale", "invalid");
        data.set(player + ".text-scale", FloatingMenuTextScale.EXTRA_HUGE.id());
        data.save(file());
        store = loaded();
        assertEquals(new FloatingMenuPreferences(FloatingMenuScale.NORMAL, FloatingMenuTextScale.EXTRA_HUGE), store.preferences(player));
        data.set(player + ".scale", FloatingMenuScale.MAXIMUM.id());
        data.set(player + ".text-scale", "invalid");
        data.save(file());
        store = loaded();
        assertEquals(new FloatingMenuPreferences(FloatingMenuScale.MAXIMUM, FloatingMenuTextScale.NORMAL), store.preferences(player));
    }

    @Test
    void preferencesRequireAnEnabledStore() {
        try (var store = new FloatingMenuSettingsStore(file(), Logger.getAnonymousLogger())) {
            assertThrows(IllegalStateException.class, () -> store.preferences(player));
            assertThrows(IllegalStateException.class, () -> store.setPreferences(player, FloatingMenuPreferences.DEFAULT));
        }
    }

    @Test
    void exactFovPersistsAndOtherChoicesDoNotOverwriteIt() {
        var store = loaded();
        var selected = new FloatingMenuPreferences(FloatingMenuScale.MAXIMUM, FloatingMenuTextScale.HUGE,
                new FloatingMenuFieldOfView(73));
        save(store, player, selected);
        assertEquals(selected, loaded().preferences(player));
        save(store, player, selected.withText(FloatingMenuTextScale.MINIMUM));
        assertEquals(73, loaded().preferences(player).fieldOfView().degrees());
        save(store, player, store.preferences(player).withLayout(FloatingMenuScale.NORMAL));
        assertEquals(73, loaded().preferences(player).fieldOfView().degrees());
    }

    @Test
    void invalidFovDoesNotResetValidSizeChoices() throws Exception {
        var store = loaded();
        var data = new YamlConfiguration();
        data.set(player + ".scale", FloatingMenuScale.MAXIMUM.id());
        data.set(player + ".text-scale", FloatingMenuTextScale.MAXIMUM.id());
        data.set(player + ".field-of-view", 1000);
        data.save(file());
        store = loaded();
        assertEquals(new FloatingMenuPreferences(FloatingMenuScale.MAXIMUM, FloatingMenuTextScale.MAXIMUM), store.preferences(player));
        data.set(player + ".field-of-view", "invalid");
        data.save(file());
        store = loaded();
        assertEquals(70, store.preferences(player).fieldOfView().degrees());
    }

    private void save(FloatingMenuSettingsStore store, UUID id, FloatingMenuPreferences value) {
        store.setPreferences(id, value);
        store.flush().join();
    }

    @Test void reducedMotionPersistsAndIndependentChangesPreserveIt() {
        var store = loaded();
        var selected = FloatingMenuPreferences.DEFAULT.withReducedMotion(true)
                .withLayout(FloatingMenuScale.LARGE).withText(FloatingMenuTextScale.SMALL)
                .withFieldOfView(new FloatingMenuFieldOfView(85));
        save(store, player, selected);
        assertEquals(selected, loaded().preferences(player));
        store.close();
        assertThrows(IllegalStateException.class, () -> store.preferences(player));
    }

    private File file() { return directory.resolve("menus/menu-settings.yml").toFile(); }

    private FloatingMenuSettingsStore loaded() {
        var store = new FloatingMenuSettingsStore(file(), Logger.getAnonymousLogger());
        stores.add(store);
        store.enable().join();
        return store;
    }
}
