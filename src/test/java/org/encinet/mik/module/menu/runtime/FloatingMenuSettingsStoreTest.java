package org.encinet.mik.module.menu.runtime;

import org.bukkit.configuration.file.YamlConfiguration;
import org.encinet.mik.module.menu.FloatingMenuPreferences;
import org.encinet.mik.module.menu.FloatingMenuScale;
import org.encinet.mik.module.menu.FloatingMenuTextScale;
import org.encinet.mik.module.menu.FloatingMenuFieldOfView;
import org.junit.jupiter.api.Test;
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

    @Test
    void bothIndependentChoicesSurviveReloadAndReset() {
        var store = loaded();
        assertEquals(FloatingMenuPreferences.DEFAULT, store.preferences(player));
        var selected = new FloatingMenuPreferences(FloatingMenuScale.MINIMUM, FloatingMenuTextScale.MAXIMUM);
        store.setPreferences(player, selected);
        var reloaded = loaded();
        assertEquals(selected, reloaded.preferences(player));
        reloaded.setPreferences(player, reloaded.preferences(player).withLayout(FloatingMenuScale.LARGE));
        assertEquals(selected.withLayout(FloatingMenuScale.LARGE), loaded().preferences(player));
        reloaded.setPreferences(player, reloaded.preferences(player).withText(FloatingMenuTextScale.SMALL));
        assertEquals(new FloatingMenuPreferences(FloatingMenuScale.LARGE, FloatingMenuTextScale.SMALL), loaded().preferences(player));
        reloaded.setPreferences(player, FloatingMenuPreferences.DEFAULT);
        assertEquals(FloatingMenuPreferences.DEFAULT, loaded().preferences(player));
    }

    @Test
    void choicesAndCachesAreIsolatedByPlayer() {
        UUID other = UUID.randomUUID();
        var store = loaded();
        var selected = new FloatingMenuPreferences(FloatingMenuScale.MAXIMUM, FloatingMenuTextScale.MINIMUM);
        store.setPreferences(player, selected);
        assertSame(store.preferences(player), store.preferences(player));
        assertEquals(FloatingMenuPreferences.DEFAULT, store.preferences(other));
        store.setPreferences(other, FloatingMenuPreferences.DEFAULT.withText(FloatingMenuTextScale.HUGE));
        store.forget(player);
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
        store.enable();
        assertEquals(new FloatingMenuPreferences(FloatingMenuScale.NORMAL, FloatingMenuTextScale.EXTRA_HUGE), store.preferences(player));
        data.set(player + ".scale", FloatingMenuScale.MAXIMUM.id());
        data.set(player + ".text-scale", "invalid");
        data.save(file());
        store.enable();
        assertEquals(new FloatingMenuPreferences(FloatingMenuScale.MAXIMUM, FloatingMenuTextScale.NORMAL), store.preferences(player));
    }

    @Test
    void preferencesRequireAnEnabledStore() {
        var store = new FloatingMenuSettingsStore(file(), Logger.getAnonymousLogger());
        assertThrows(IllegalStateException.class, () -> store.preferences(player));
        assertThrows(IllegalStateException.class, () -> store.setPreferences(player, FloatingMenuPreferences.DEFAULT));
    }

    @Test
    void exactFovPersistsAndOtherChoicesDoNotOverwriteIt() {
        var store = loaded();
        var selected = new FloatingMenuPreferences(FloatingMenuScale.MAXIMUM, FloatingMenuTextScale.HUGE,
                new FloatingMenuFieldOfView(73));
        store.setPreferences(player, selected);
        assertEquals(selected, loaded().preferences(player));
        store.setPreferences(player, selected.withText(FloatingMenuTextScale.MINIMUM));
        assertEquals(73, loaded().preferences(player).fieldOfView().degrees());
        store.setPreferences(player, store.preferences(player).withLayout(FloatingMenuScale.NORMAL));
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
        store.enable();
        assertEquals(new FloatingMenuPreferences(FloatingMenuScale.MAXIMUM, FloatingMenuTextScale.MAXIMUM), store.preferences(player));
        data.set(player + ".field-of-view", "invalid");
        data.save(file());
        store.enable();
        assertEquals(70, store.preferences(player).fieldOfView().degrees());
    }

    private File file() { return directory.resolve("menus/menu-settings.yml").toFile(); }

    private FloatingMenuSettingsStore loaded() {
        var store = new FloatingMenuSettingsStore(file(), Logger.getAnonymousLogger());
        store.enable();
        return store;
    }
}
