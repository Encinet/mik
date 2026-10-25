package org.encinet.mik.integration.geyser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GeyserOptionalLinkageTest {

    @Test
    void integrationClassesRemainLoadableWithoutTheOptionalRuntimeApi() {
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("org.geysermc.geyser.api.GeyserApi"));
        assertDoesNotThrow(() -> Class.forName(GeyserService.class.getName()));
        assertDoesNotThrow(() -> Class.forName(
                "org.encinet.mik.shared.menu.runtime.GeyserFloatingMenuPresenter"));
    }
}
