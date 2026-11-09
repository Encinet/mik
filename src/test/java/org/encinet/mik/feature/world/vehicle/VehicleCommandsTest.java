package org.encinet.mik.feature.world.vehicle;

import com.mojang.brigadier.CommandDispatcher;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.entity.Player;
import org.encinet.mik.shared.i18n.LanguageService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VehicleCommandsTest {
    @Test void singleEntryOpensTheWorkspaceAndHasNoOperationalSubcommands() throws Exception {
        var module = mock(VehicleModule.class);
        var source = mock(CommandSourceStack.class); var player = mock(Player.class);
        when(source.getSender()).thenReturn(player); when(player.hasPermission("mik.vehicle.use")).thenReturn(true);
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        var root = new VehicleCommands(module, mock(LanguageService.class)).build(); dispatcher.getRoot().addChild(root);
        assertTrue(root.getChildren().isEmpty());
        assertEquals(1, dispatcher.execute("vehicle", source)); verify(module).openMenu(player);
        for (String removed : java.util.List.of("model", "spawn sedan", "ride", "gear D", "reload", "help"))
            assertThrows(com.mojang.brigadier.exceptions.CommandSyntaxException.class, () -> dispatcher.execute("vehicle " + removed, source));
        verifyNoMoreInteractions(module);
    }
    @Test void useOrAdminPermissionIsRequiredToSeeTheEntry() {
        var source = mock(CommandSourceStack.class); var player = mock(Player.class); when(source.getSender()).thenReturn(player);
        var root = new VehicleCommands(mock(VehicleModule.class), mock(LanguageService.class)).build();
        assertFalse(root.canUse(source)); when(player.hasPermission("mik.vehicle.admin")).thenReturn(true); assertTrue(root.canUse(source));
    }
}
