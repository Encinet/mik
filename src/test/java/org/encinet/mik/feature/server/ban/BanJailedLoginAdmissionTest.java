package org.encinet.mik.feature.server.ban;

import com.destroystokyo.paper.profile.PlayerProfile;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.connection.PlayerLoginConnection;
import io.papermc.paper.event.connection.PlayerConnectionValidateLoginEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.time.Instant;
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
        var finished = new PlayerConnectionValidateLoginEvent(configuration, null);
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
        var maintenance = Component.text("Maintenance");
        var refused = new PlayerConnectionValidateLoginEvent(connection, maintenance);
        listener.onPlayerConnectionValidate(refused);
        assertEquals(maintenance, refused.getKickMessage());
        verifyNoInteractions(prison);

        var linked = new BanAdmissionChecker.AdmissionBan(record(), BanAdmissionChecker.MatchType.ADDRESS_LINK);
        when(prison.canEnter(PLAYER, connection)).thenReturn(true);
        when(checker.findAddressLink(PLAYER, ADDRESS.getAddress())).thenReturn(Optional.of(linked));
        var associated = new PlayerConnectionValidateLoginEvent(connection, null);
        listener.onPlayerConnectionValidate(associated);
        assertEquals(BAN, associated.getKickMessage());
        verify(checker).findAddressLink(PLAYER, ADDRESS.getAddress());
    }

    @Test
    void onlyTheExactAccountAndTheNativeProfileBanAreEligible() {
        assertTrue(BanLoginListener.matchesAccount(direct(), PLAYER));
        assertFalse(BanLoginListener.matchesAccount(direct(), UUID.randomUUID()));
        assertTrue(PaperProfileBanAdmission.isProfileBan(Component.translatable("multiplayer.disconnect.banned.reason")));
        assertFalse(PaperProfileBanAdmission.isProfileBan(Component.translatable("multiplayer.disconnect.banned_ip.reason")));
        assertFalse(PaperProfileBanAdmission.isProfileBan(Component.translatable("multiplayer.disconnect.not_whitelisted")));
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
