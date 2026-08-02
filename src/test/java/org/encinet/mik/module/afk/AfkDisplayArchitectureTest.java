package org.encinet.mik.module.afk;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AfkDisplayArchitectureTest {
    @Test
    void axiomExclusionWrapsTheAfkDisplayLifetime() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/org/encinet/mik/module/afk/AfkDisplayController.java"));

        int synchronize = source.indexOf(
                "axiomGizmos.synchronize(viewer, display.entityUuid, Set.of(display.entityUuid))");
        int spawn = source.indexOf("new WrapperPlayServerSpawnEntity(");
        int destroy = source.indexOf("new WrapperPlayServerDestroyEntities(display.entityId)");
        int remove = source.indexOf("axiomGizmos.remove(viewer, display.entityUuid)");

        assertTrue(synchronize >= 0 && synchronize < spawn);
        assertTrue(destroy >= 0 && destroy < remove);
        assertTrue(source.contains("axiomGizmos.forgetViewer(viewerId)"));
    }
}
