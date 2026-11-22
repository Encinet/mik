package org.encinet.mik.feature.player.security;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.encinet.mik.integration.mikd.RequestPort;
import org.encinet.mik.protocol.WebLoginApproval;
import org.encinet.mik.protocol.WebLoginReceipt;
import org.encinet.mik.protocol.WebLoginReceipt.Outcome;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WebLoginCommandTest {
    private static final String CODE = "123456";
    private final UUID account = UUID.randomUUID();
    private final String reference = UUID.randomUUID().toString();
    private final AtomicBoolean current = new AtomicBoolean(true);
    private final Player player = mock(Player.class);
    private final RequestPort daemon = mock(RequestPort.class);
    private final PasswordModule security = mock(PasswordModule.class);
    private final CompletableFuture<WebLoginReceipt> response = new CompletableFuture<>();
    private final List<Runnable> tasks = new ArrayList<>();
    private final List<Write> writes = new ArrayList<>();
    private final List<Runnable> approvals = new ArrayList<>();
    private WebLoginCommand command;

    @BeforeEach void setUp() {
        var plugin = mock(JavaPlugin.class);
        var server = mock(Server.class);
        var scheduler = mock(BukkitScheduler.class);
        when(plugin.isEnabled()).thenReturn(true);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        when(scheduler.runTask(any(Plugin.class), any(Runnable.class))).thenAnswer(call -> {
            tasks.add(call.getArgument(1));
            return null;
        });
        when(player.isOnline()).thenReturn(true);
        when(player.getUniqueId()).thenReturn(account);
        when(player.getName()).thenReturn("Tester");
        when(security.authentication(player)).thenReturn(current::get);
        when(security.webLoginReference(account)).thenReturn(reference);
        doAnswer(call -> { approvals.add(call.getArgument(1)); return null; })
                .when(security).confirmWebLogin(eq(player), any(Runnable.class));
        when(daemon.approveWebLogin(any(), any())).thenAnswer(call -> {
            writes.add(new Write(call.getArgument(0), call.getArgument(1)));
            return response;
        });
        var language = new LanguageService(null) {
            @Override public String t(Player viewer, Message message, Object... args) { return message.name(); }
        };
        command = new WebLoginCommand(plugin, language, security, daemon);
    }

    @ParameterizedTest @EnumSource(value = Outcome.class, names = "UNRECOGNIZED", mode = EnumSource.Mode.EXCLUDE)
    void explicitApprovalsAndRejectionsUseTheirOwnFeedback(Outcome outcome) {
        acceptDialog();
        response.complete(WebLoginReceipt.newBuilder().setOutcome(outcome).build());
        verify(player, never()).sendMessage(any(Component.class));
        tasks.getFirst().run();
        assertFeedback(switch (outcome) {
            case APPROVED -> Message.WEBLOGIN_CONFIRMED;
            case REJECTED -> Message.WEBLOGIN_REJECTED;
            case UNAVAILABLE -> Message.WEBLOGIN_UNAVAILABLE;
            case OUTCOME_UNSPECIFIED, UNRECOGNIZED -> Message.WEBLOGIN_UNCONFIRMED;
        });
    }

    @Test void losingTheReplyDoesNotReportAnInvalidCode() {
        acceptDialog();
        response.completeExceptionally(new TimeoutException("may already have committed"));
        tasks.getFirst().run();
        assertFeedback(Message.WEBLOGIN_UNCONFIRMED);
        assertEquals(1, writes.size());
    }

    @Test void preWriteFailureReportsUnavailableRatherThanACodeRejection() {
        acceptDialog();
        response.completeExceptionally(new RequestPort.NotSubmitted("socket unavailable"));
        tasks.getFirst().run();
        assertFeedback(Message.WEBLOGIN_UNAVAILABLE);
    }

    @Test void staleDialogDoesNotSubmitAnApprovalForAReplacementSession() {
        command.confirm(player, CODE);
        current.set(false);
        when(security.webLoginReference(account)).thenReturn(UUID.randomUUID().toString());
        approvals.getFirst().run();
        verifyNoInteractions(daemon);
    }

    @Test void authorizationIsStillRequiredAtTheWriteBoundaryAndForAnAlreadyQueuedReply() {
        acceptDialog();
        response.complete(WebLoginReceipt.newBuilder().setOutcome(Outcome.APPROVED).build());
        current.set(false);
        assertFalse(writes.getFirst().authorized().getAsBoolean());
        tasks.getFirst().run();
        verify(player, never()).sendMessage(any(Component.class));
    }

    private void acceptDialog() {
        command.confirm(player, CODE);
        verifyNoInteractions(daemon);
        approvals.getFirst().run();
        var request = writes.getFirst();
        assertEquals(CODE, request.approval().getCode());
        assertEquals(reference, request.approval().getLoginReference());
        assertTrue(request.authorized().getAsBoolean());
    }

    private void assertFeedback(Message expected) {
        var component = ArgumentCaptor.forClass(Component.class);
        verify(player).sendMessage(component.capture());
        assertEquals(expected.name(), PlainTextComponentSerializer.plainText().serialize(component.getValue()));
    }

    private record Write(WebLoginApproval approval, BooleanSupplier authorized) { }
}
