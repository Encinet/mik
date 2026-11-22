package org.encinet.mik.feature.community.chat.game;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.chat.SignedMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.encinet.mik.feature.community.chat.ChatModule;
import org.encinet.mik.feature.community.chat.settings.ChatDelayOption;
import org.encinet.mik.feature.community.chat.settings.ChatSettingsStore;
import org.encinet.mik.feature.player.guidance.TipTestScope;
import org.encinet.mik.feature.player.profile.PlayerNameTagRenderer;
import org.encinet.mik.feature.player.profile.PlayerSourceRenderer;
import org.encinet.mik.integration.geyser.BedrockPlayerDetector;
import org.encinet.mik.integration.mikd.RequestPort;
import org.encinet.mik.protocol.ChatCommand;
import org.encinet.mik.protocol.ChatPlayer;
import org.encinet.mik.protocol.ChatResult;
import org.encinet.mik.protocol.ChatWebPeer;
import org.encinet.mik.protocol.ChatWebRoster;
import org.encinet.mik.protocol.HistoryChannel;
import org.encinet.mik.protocol.HistorySaved;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PrivateWebChatTest {
    private final UUID recipientId = UUID.randomUUID();
    private final Map<UUID, Player> online = new HashMap<>();
    private final Map<Player, AtomicBoolean> sessions = new HashMap<>();
    private final Map<Player, List<String>> lines = new HashMap<>();
    private final Set<UUID> blocked = new HashSet<>();
    private final List<Write> requests = new ArrayList<>();
    private final List<Runnable> scheduled = new ArrayList<>();
    private final ConsoleCommandSender console = mock(ConsoleCommandSender.class);
    private final ChatSettingsStore settings = new ChatSettingsStore(null);
    private final TipTestScope tips = new TipTestScope();
    private MockedStatic<Bukkit> bukkit;
    private boolean primary = true;
    private ChatModule chat;
    private GameChatController game;
    private Player sender;

    @BeforeEach void setUp() {
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::isPrimaryThread).thenAnswer(_ -> primary);
        bukkit.when(Bukkit::getOnlinePlayers).thenAnswer(_ -> online.values());
        bukkit.when(() -> Bukkit.getPlayer(any(UUID.class))).thenAnswer(call -> online.get(call.getArgument(0)));
        var offlinePlayer = mock(OfflinePlayer.class);
        bukkit.when(() -> Bukkit.getOfflinePlayer(recipientId)).thenReturn(offlinePlayer);
        bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
        var scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTask(any(Plugin.class), any(Runnable.class))).thenAnswer(call -> {
            scheduled.add(call.getArgument(1));
            return mock(BukkitTask.class);
        });
        when(scheduler.runTaskLater(any(Plugin.class), any(Runnable.class), anyLong())).thenAnswer(call -> {
            scheduled.add(call.getArgument(1));
            return mock(BukkitTask.class);
        });
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        var plugin = mock(Plugin.class);
        var server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        when(server.getConsoleSender()).thenReturn(console);
        when(plugin.getName()).thenReturn("mik");
        when(plugin.namespace()).thenReturn("mik");
        when(plugin.isEnabled()).thenReturn(true);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        var language = new LanguageService(null) {
            @Override public Language language(Player player) { return Language.EN_US; }
            @Override public String t(Player player, Message message, Object... args) { return message.name(); }
            @Override public String t(Language locale, Message message, Object... args) { return message.name(); }
        };
        var daemon = mock(RequestPort.class);
        when(daemon.chat(any(ChatCommand.class), any(BooleanSupplier.class))).thenAnswer(call -> {
            var request = new Write(call.getArgument(0), call.getArgument(1), new CompletableFuture<>());
            requests.add(request);
            return request.response();
        });
        chat = new ChatModule(plugin, new GameMentionNotifier(plugin, null, language, null), language,
                settings, new PlayerNameTagRenderer(plugin),
                new PlayerSourceRenderer(mock(BedrockPlayerDetector.class), language), tips, (_, _) -> false);
        game = chat.game();
        var bridge = new PaperChatBridge(plugin, language, daemon, player -> sessions.get(player).get(),
                (id, _) -> !blocked.contains(id), _ -> { }, game::deliverRouted);
        bridge.receiveWebRoster(ChatWebRoster.newBuilder().setRevision(1)
                .addPlayers(ChatWebPeer.newBuilder().setConnections(1).setPlayer(ChatPlayer.newBuilder()
                        .setId(recipientId.toString()).setName("Reader"))).build());
        sender = player(UUID.randomUUID(), "Writer");
        chat.enable(bridge, bridge, player -> sessions.get(player)::get);
        game.switchPrivate(sender, "Reader");
        lines.get(sender).clear();
    }

    @AfterEach void tearDown() {
        try {
            if (chat != null) chat.disable();
            tips.close();
        } finally {
            if (bukkit != null) bukkit.close();
        }
    }

    @Test void commandWaitsForMatchingDurableReceiptEvenWhenAlreadyInserted() {
        game.sendTemporaryPrivateCommand(sender, "Reader", "only one copy");
        assertTrue(lines.get(sender).isEmpty(), "A local echo must not look like successful web delivery");
        var request = requests.getFirst();
        var entry = request.command().getPublish();
        assertEquals(HistoryChannel.HISTORY_PRIVATE, entry.getChannel());
        assertEquals(Set.of(sender.getUniqueId().toString(), recipientId.toString()), Set.copyOf(entry.getParticipantsList()));
        request.response().complete(saved(entry.getMessageId(), false));
        assertTrue(lines.get(sender).getFirst().endsWith("only one copy"));
        assertEquals(Message.CHAT_PRIVATE_WEB_SAVED.name(), lines.get(sender).getLast());
        assertEquals(1, requests.size());
        verifyNoInteractions(console);
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void unsuccessfulWebSubmissionDoesNotEchoRetryOrChangeTheReplyPartner(boolean definite) {
        // The new sender has no selected conversation or existing reply partner.
        var otherSender = player(UUID.randomUUID(), "AnotherWriter");
        var data = mock(PersistentDataContainer.class);
        when(data.getOrDefault(any(), eq(PersistentDataType.BYTE), anyByte())).thenReturn((byte) 0);
        when(otherSender.getPersistentDataContainer()).thenReturn(data);
        game.sendTemporaryPrivateCommand(otherSender, "Reader", "not confirmed");
        requests.getFirst().response().completeExceptionally(definite
                ? new RequestPort.NotSubmitted("before write") : new TimeoutException("lost reply"));
        assertEquals(List.of((definite ? Message.CHAT_PRIVATE_WEB_NOT_SUBMITTED : Message.CHAT_PRIVATE_WEB_UNCONFIRMED).name()),
                lines.get(otherSender));
        game.sendReplyCommand(otherSender, "must not be sent");
        assertEquals(Message.CHAT_PRIVATE_NO_REPLY_TARGET.name(), lines.get(otherSender).getLast());
        assertEquals(1, requests.size());
        verifyNoInteractions(console);
    }

    @Test void nativeChannelMarksTheSendersEchoPendingAndNeverBroadensTheFinalAudience() {
        var event = event("private draft");
        game.onChatRoute(event);
        var rendered = event.renderer().render(sender, sender.displayName(), event.message(), sender);
        assertTrue(plain(rendered).startsWith(Message.CHAT_PRIVATE_WEB_PENDING.name()));
        event.viewers().remove(sender);
        game.onChatCommit(event);
        assertTrue(requests.isEmpty());
        assertEquals(List.of(Message.CHAT_PRIVATE_WEB_NOT_SUBMITTED.name()), lines.get(sender));
        verifyNoInteractions(console);
    }

    @Test void nativeChannelPublishesTheFinalBodyAndRejectsAnUnrelatedReceipt() {
        var event = event("original");
        game.onChatRoute(event);
        event.message(Component.text("edited by another plugin"));
        game.onChatCommit(event);
        var request = requests.getFirst();
        assertEquals("edited by another plugin", request.command().getPublish().getRecord().getUser().getBody().getParts(0).getText());
        request.response().complete(saved(UUID.randomUUID().toString(), true));
        assertEquals(List.of(Message.CHAT_PRIVATE_WEB_UNCONFIRMED.name()), lines.get(sender));
        assertEquals(1, requests.size());
    }

    @Test void browserOnlyRecipientCannotSeeAConcealedAuthor() {
        when(sender.isVisibleByDefault()).thenReturn(false);
        game.sendTemporaryPrivateCommand(sender, "Reader", "must not leave Paper");
        assertTrue(requests.isEmpty());
        assertEquals(List.of(Message.CHAT_PRIVATE_WEB_NOT_SUBMITTED.name()), lines.get(sender));
        verifyNoInteractions(console);
    }

    @Test void revokedRecipientAuthorizationStopsAQueuedWebOnlyWrite() {
        game.sendTemporaryPrivateCommand(sender, "Reader", "spans revocation");
        var request = requests.getFirst();
        assertTrue(request.authorized().getAsBoolean());
        blocked.add(recipientId);
        assertFalse(request.authorized().getAsBoolean());
        request.response().completeExceptionally(new RequestPort.NotSubmitted("recipient no longer admitted"));
        assertEquals(List.of(Message.CHAT_PRIVATE_WEB_NOT_SUBMITTED.name()), lines.get(sender));
        assertEquals(1, requests.size());
    }

    @Test void gameRecipientGetsLocalDeliveryWithoutWaitingForTheSharedCopy() {
        var recipient = player(recipientId, "Reader");
        game.sendTemporaryPrivateCommand(sender, "Reader", "direct");
        assertTrue(lines.get(recipient).getFirst().endsWith("direct"));
        assertTrue(lines.get(sender).getFirst().endsWith("direct"));
        requests.getFirst().response().completeExceptionally(new RequestPort.NotSubmitted("daemon down"));
        assertEquals(1, lines.get(sender).size());
        assertEquals(1, lines.get(recipient).size());
        verifyNoInteractions(console);
    }

    @Test void neitherAnInFlightReceiptNorADelayedDraftMovesToAReplacementSession() {
        game.sendTemporaryPrivateCommand(sender, "Reader", "in flight");
        var request = requests.getFirst();
        settings.setDelay(sender.getUniqueId(), ChatDelayOption.THREE_SECONDS);
        game.sendTemporaryPrivateCommand(sender, "Reader", "queued draft");
        lines.get(sender).clear();
        primary = false;
        request.response().complete(saved(request.command().getPublish().getMessageId(), true));
        primary = true;
        sessions.get(sender).set(false);
        var replacement = player(sender.getUniqueId(), "Writer");
        assertFalse(request.authorized().getAsBoolean());
        assertEquals(2, scheduled.size());
        scheduled.forEach(Runnable::run);
        assertEquals(1, requests.size());
        assertTrue(lines.get(sender).isEmpty());
        assertTrue(lines.get(replacement).isEmpty());
    }

    private Player player(UUID id, String name) {
        var player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        when(player.getName()).thenReturn(name);
        when(player.displayName()).thenReturn(Component.text(name));
        when(player.getInventory()).thenReturn(mock(PlayerInventory.class));
        when(player.isOnline()).thenReturn(true);
        when(player.isVisibleByDefault()).thenReturn(true);
        when(player.canSee(any(Player.class))).thenReturn(true);
        lines.put(player, new ArrayList<>());
        doAnswer(call -> { lines.get(player).add(plain(call.getArgument(0))); return null; })
                .when(player).sendMessage(any(Component.class));
        online.put(id, player);
        sessions.put(player, new AtomicBoolean(true));
        return player;
    }

    private AsyncChatEvent event(String text) {
        var component = Component.text(text);
        return new AsyncChatEvent(false, sender, new HashSet<Audience>(online.values()), ChatRenderer.defaultRenderer(),
                component, component, SignedMessage.system(text, component));
    }

    private static ChatResult saved(String id, boolean inserted) {
        return ChatResult.newBuilder().setPublished(HistorySaved.newBuilder().setMessageId(id)
                .setOrderKey(UUID.randomUUID().toString()).setInserted(inserted)).build();
    }

    private static String plain(Component component) { return PlainTextComponentSerializer.plainText().serialize(component); }

    private record Write(ChatCommand command, BooleanSupplier authorized, CompletableFuture<ChatResult> response) { }
}
