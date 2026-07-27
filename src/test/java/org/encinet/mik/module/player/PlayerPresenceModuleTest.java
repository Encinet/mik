package org.encinet.mik.module.player;

import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerPresenceModuleTest {

    @Test
    void preservesRenamedPlayerJoinMessage() {
        Component message = Component.translatable(
                "multiplayer.player.joined.renamed",
                Component.text("NewName"),
                Component.text("OldName"));

        assertTrue(PlayerPresenceModule.isRenamedJoinMessage(message));
    }

    @Test
    void ordinaryJoinMessagesCanStillBeCustomized() {
        assertFalse(PlayerPresenceModule.isRenamedJoinMessage(
                Component.translatable("multiplayer.player.joined", Component.text("Player"))));
        assertFalse(PlayerPresenceModule.isRenamedJoinMessage(null));
    }
}
