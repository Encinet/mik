package org.encinet.mik.feature.server.ban;

import com.destroystokyo.paper.profile.PlayerProfile;
import io.papermc.paper.configuration.GlobalConfiguration;
import io.papermc.paper.connection.PaperPlayerConfigurationConnection;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.connection.PlayerLoginConnection;
import io.papermc.paper.event.connection.PlayerConnectionValidateLoginEvent;
import net.kyori.adventure.text.Component;
import net.minecraft.network.Connection;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BanJailedLoginAdmissionTest {
    private static final UUID PLAYER = new UUID(1, 1);
    private static final Instant NOW = Instant.parse("2026-11-23T00:00:00Z");
    private static final InetSocketAddress ADDRESS = new InetSocketAddress("127.0.0.1", 25577);
    private static final Component BAN = Component.text("Account banned");
    private final BanAdmissionChecker checker = mock(BanAdmissionChecker.class);
    private final BannedLoginAdmission prison = mock(BannedLoginAdmission.class);
    private final BanLoginListener listener;

    BanJailedLoginAdmissionTest() {
        var language = mock(LanguageService.class);
        var renderer = mock(BanMessageRenderer.class);
        when(language.languageForAddress(ADDRESS.getAddress())).thenReturn(Language.DEFAULT);
        when(renderer.banMessage(Language.DEFAULT, record())).thenReturn(BAN);
        listener = new BanLoginListener(mock(JavaPlugin.class), mock(PaperBanSynchronizer.class),
                language, renderer, checker);
        listener.bannedLoginAdmission(prison);
        when(checker.find(PLAYER, "Jailed", ADDRESS.getAddress())).thenReturn(Optional.of(direct()));
    }

    @Test
    void configurationRequiresItsOwnPreparedLandingEvenAfterInitialApproval() {
        var login = mock(PlayerLoginConnection.class);
        var profile = profile();
        when(login.getAuthenticatedProfile()).thenReturn(profile);
        when(login.getClientAddress()).thenReturn(ADDRESS);
        when(prison.canConfigure(PLAYER)).thenReturn(true);
        var initial = new PlayerConnectionValidateLoginEvent(login, null);
        listener.onPlayerConnectionValidate(initial);
        assertTrue(initial.isAllowed());

        var configuration = configuration();
        var finished = new PlayerConnectionValidateLoginEvent(configuration,
                Component.translatable("multiplayer.disconnect.banned.reason"));
        listener.onPlayerConnectionValidate(finished);
        assertEquals(BAN, finished.getKickMessage());
        when(prison.canEnter(PLAYER, configuration)).thenReturn(true);
        finished = new PlayerConnectionValidateLoginEvent(configuration, null);
        listener.onPlayerConnectionValidate(finished);
        assertTrue(finished.isAllowed());
    }

    @Test
    void keepsAddressLinkAndOtherLoginRefusals() {
        var connection = configuration();
        for (var reason : List.<Component>of(Component.text("Maintenance"),
                Component.translatable("multiplayer.disconnect.banned_ip.reason"),
                Component.translatable("multiplayer.disconnect.not_whitelisted"))) {
            var refused = new PlayerConnectionValidateLoginEvent(connection, reason);
            listener.onPlayerConnectionValidate(refused);
            assertEquals(reason, refused.getKickMessage());
        }

        var profile = connection.getProfile();
        var differentAccount = new UUID(1, 2);
        when(profile.getId()).thenReturn(differentAccount);
        when(checker.find(differentAccount, "Jailed", ADDRESS.getAddress())).thenReturn(Optional.of(direct()));
        var nameMatch = new PlayerConnectionValidateLoginEvent(connection, null);
        listener.onPlayerConnectionValidate(nameMatch);
        assertEquals(BAN, nameMatch.getKickMessage());
        verifyNoInteractions(prison);

        when(profile.getId()).thenReturn(PLAYER);
        var linked = new BanAdmissionChecker.AdmissionBan(record(), BanAdmissionChecker.MatchType.ADDRESS_LINK);
        when(prison.canEnter(PLAYER, connection)).thenReturn(true);
        when(checker.findAddressLink(PLAYER, ADDRESS.getAddress())).thenReturn(Optional.of(linked));
        var associated = new PlayerConnectionValidateLoginEvent(connection, null);
        listener.onPlayerConnectionValidate(associated);
        assertEquals(BAN, associated.getKickMessage());
    }

    @Test
    void finalRefusalDiscardsOnlyTheMatchingConnectionsCachedLegacyAllow() {
        // Connection reads the per-tick join limit from Paper's configuration at class initialization.
        var settings = new GlobalConfiguration();
        settings.misc = settings.new Misc();
        try (var global = mockStatic(GlobalConfiguration.class)) {
            global.when(GlobalConfiguration::get).thenReturn(settings);
            var connection = mock(Connection.class);
            var packets = mock(ServerConfigurationPacketListenerImpl.class);
            var paper = mock(PaperPlayerConfigurationConnection.class);
            when(connection.getPacketListener()).thenReturn(packets);
            when(packets.paperConnection()).thenReturn(paper);
            connection.legacySavedLoginEventResultOverride = Optional.empty();

            var other = mock(Connection.class);
            other.legacySavedLoginEventResultOverride = Optional.empty();
            var connections = List.of(other, connection);
            PaperProfileBanAdmission.keepFinalRefusal(connections, paper);
            assertNull(connection.legacySavedLoginEventResultOverride);
            assertEquals(Optional.empty(), other.legacySavedLoginEventResultOverride);

            Optional<net.minecraft.network.chat.Component> refusal =
                    Optional.of(net.minecraft.network.chat.Component.literal("Maintenance"));
            connection.legacySavedLoginEventResultOverride = refusal;
            PaperProfileBanAdmission.keepFinalRefusal(connections, paper);
            assertSame(refusal, connection.legacySavedLoginEventResultOverride);
        }
    }

    private static PlayerConfigurationConnection configuration() {
        var connection = mock(PlayerConfigurationConnection.class);
        var profile = profile();
        when(connection.getProfile()).thenReturn(profile);
        when(connection.getClientAddress()).thenReturn(ADDRESS);
        return connection;
    }

    private static PlayerProfile profile() {
        var profile = mock(PlayerProfile.class);
        when(profile.getId()).thenReturn(PLAYER);
        when(profile.getName()).thenReturn("Jailed");
        return profile;
    }

    private static BanAdmissionChecker.AdmissionBan direct() {
        return new BanAdmissionChecker.AdmissionBan(record(), BanAdmissionChecker.MatchType.DIRECT);
    }

    private static BanRecord record() {
        return new BanRecord(1, PLAYER, "Jailed", "jailed", "reason", "Admin",
                NOW.minusSeconds(60), NOW.minusSeconds(60), null, null, BanRecord.Origin.MIK);
    }
}
