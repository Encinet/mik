package org.encinet.mik.feature.community.chat.game;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.connection.PlayerGameConnection;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.chat.SignedMessage;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.api.BinaryTagHolder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.cacheddata.CachedDataManager;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.*;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.encinet.mik.feature.community.chat.ChatModule;
import org.encinet.mik.feature.community.chat.bridge.ChatRecordCodec;
import org.encinet.mik.feature.community.chat.settings.ChatDelayOption;
import org.encinet.mik.feature.community.chat.settings.ChatMentionSetting;
import org.encinet.mik.feature.community.chat.settings.ChatSettingsStore;
import org.encinet.mik.feature.community.chat.model.*;
import org.encinet.mik.feature.player.guidance.TipTestScope;
import org.encinet.mik.feature.player.profile.PlayerNameTagRenderer;
import org.encinet.mik.feature.player.profile.PlayerSourceRenderer;
import org.encinet.mik.protocol.ChatBody;
import org.encinet.mik.protocol.ChatPart;
import org.encinet.mik.protocol.ChatTextRun;
import org.encinet.mik.protocol.ChatDestination;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.i18n.RichArg;
import org.encinet.mik.test.ChatTestMessages;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlayerChatDeliveryTest {
    @Test void nativeGameAndWebUseTheSameIdentityPresentation() throws Exception {
        try (Fixture f = new Fixture()) {
            Player alice = f.game("Alice");
            Player bob = f.game("Bob");
            var event = event(alice, Set.of(alice, bob), "hello");
            f.chat.game().onChatRoute(event);
            f.namePrefix = "<red>[Changed] </red>";
            assertEquals("[Member] Alice [Tag] » hello", plain(event.renderer().render(alice, alice.displayName(), event.message(), bob)));
            f.chat.game().onChatCommit(event);
            assertEquals(ChatSender.minecraft(alice.getUniqueId(), "Alice"), f.published.getFirst().sender());

            f.namePrefix = "<gold>[Member] </gold>";
            f.web(alice.getUniqueId(), ChatChannel.PUBLIC, null, "hello");
            assertEquals(List.of("[Web] [Member] Alice [Tag] » hello"), f.received.get(bob.getUniqueId()));
            assertEquals(f.received.get(bob.getUniqueId()), f.console);
        }
    }

    @Test void nativeAsyncChatOnlyUsesPreparedGameStateForInputAndPresentation() throws Exception {
        try (Fixture f = new Fixture()) {
            Player alice = f.game("Alice");
            Player bob = f.game("Bob");
            UUID author = alice.getUniqueId();
            UUID viewer = bob.getUniqueId();
            var diamond = mock(ItemStack.class);
            var hover = HoverEvent.showItem(Key.key("minecraft:diamond"), 2);
            when(diamond.getType()).thenReturn(Material.DIAMOND);
            when(diamond.effectiveName()).thenReturn(Component.text("Diamond"));
            when(diamond.getAmount()).thenReturn(2);
            when(diamond.asHoverEvent()).thenReturn(hover);
            f.heldItems.put(author, diamond);
            f.languages.put(viewer, Language.ZH_CN);
            f.bedrock.add(author);
            f.samplers.forEach(Runnable::run);

            f.heldItems.clear();
            f.bedrock.clear();
            f.staffDenied.add(author);
            f.languages.clear();
            var event = event(alice, Set.of(alice, bob), "<bold>see</bold> [item]");
            f.primaryThread = false;
            f.chat.game().onChatRoute(event);
            f.chat.game().onChatCommit(event);
            var line = event.renderer().render(alice, Component.text("Alice"), event.message(), bob);
            assertEquals("[基岩] [Member] Alice [Tag] » see [Diamond x2]", plain(line));
            assertEquals(TextDecoration.State.TRUE, namedText(event.message(), "see").decoration(TextDecoration.BOLD));
            assertEquals(hover, namedText(line, "[Diamond x2]").hoverEvent());

            f.primaryThread = true;
            f.scheduled.removeFirst().run();
            var post = ChatRecordCodec.publication(f.published.getFirst(), Optional.empty(), false).getPost();
            assertEquals("[Diamond x2]", post.getPartsList().stream().filter(part -> part.getResolved().hasDescription())
                    .findFirst().orElseThrow().getResolved().getDescription().getName());
        }
    }

    @Test void offlineWebIdentityReadsTheAcceptedLuckPermsColor() throws Exception {
        try (Fixture f = new Fixture()) {
            UUID webOnly = f.account("WebOnly");
            f.game("Bob");
            f.usernameColor = "<yellow>";
            f.web(webOnly, ChatChannel.PUBLIC, null, "hello");
            Component rendered = f.consoleComponents.getLast();
            assertEquals(NamedTextColor.YELLOW, namedText(rendered, f.names.get(webOnly)).color());
            assertEquals(NamedTextColor.GOLD, namedText(rendered, "[Member] ").color());
        }
    }

    @Test void privateAndStaffMessagesKeepNormalIdentitiesWithoutPublicSideEffects() throws Exception {
        try (Fixture f = new Fixture()) {
            UUID alice = f.account("Alice");
            Player bob = f.game("Bob");
            Player other = f.game("Other");
            f.staffDenied.add(other.getUniqueId());
            f.web(alice, ChatChannel.STAFF, null, "staff");
            f.web(alice, ChatChannel.PRIVATE, ChatSender.minecraft(bob.getUniqueId(), "Bob"), "private");
            assertEquals(List.of("[Staff] [Web] [Member] Alice [Tag] » staff",
                    "[MSG] [Web] Alice -> Bob » private"), f.received.get(bob.getUniqueId()));
            assertTrue(f.received.get(other.getUniqueId()).isEmpty());
            assertTrue(f.console.isEmpty());
        }
    }

    @Test void bedrockMarksOnlyGeyserGameMessagesEvenWhenTheSameAccountUsesTheWeb() throws Exception {
        try (Fixture f = new Fixture()) {
            Player alice = f.game("Alice");
            Player bob = f.game("Bob");
            UUID sender = alice.getUniqueId();
            f.bedrock.add(sender);
            f.bedrock.add(bob.getUniqueId());
            f.chat.game().sendTemporaryPublicCommand(alice, "game");
            f.web(sender, ChatChannel.PUBLIC, null, "web");
            f.chat.game().sendStaffCommand(alice, "staff");
            f.chat.game().sendTemporaryPrivateCommand(alice, "Bob", "private");
            f.players.remove(sender);
            f.web(sender, ChatChannel.PUBLIC, null, "web only");

            assertEquals(List.of(
                    "[Bedrock] [Member] Alice [Tag] » game",
                    "[Web] [Member] Alice [Tag] » web",
                    "[Staff] [Bedrock] [Member] Alice [Tag] » staff",
                    "[MSG] [Bedrock] Alice -> Bob » private",
                    "[Web] [Member] Alice [Tag] » web only"), f.received.get(bob.getUniqueId()));
            assertFalse(f.geyserQueries.contains(bob.getUniqueId()), "A recipient's live device is not the message source");
        }
    }

    @Test void externalSourceTravelsWithTheMessageAndNeverInheritsTheBoundPlayersGameClient() throws Exception {
        try (Fixture f = new Fixture()) {
            Player alice = f.game("Alice");
            Player bob = f.game("Bob");
            f.bedrock.add(alice.getUniqueId());
            var colors = Map.of("QQ", 0x12B7F5, "Matrix", 0x0DBD8B, "irc", 0xFFFFFF);
            for (var platform : List.of("QQ", "Matrix", "irc")) {
                var sender = ChatSender.minecraft(alice.getUniqueId(), "Alice");
                f.deliverRouted(platform, sender, ChatDestination.Room.PUBLIC, "external");
                assertEquals(colors.get(platform), namedText(f.consoleComponents.getLast(),
                        "[" + platform + "]").color().value());
            }
            var unbound = new ChatSender("Guest", Optional.empty(), Optional.empty(), "");
            f.deliverRouted(
                    "Matrix", unbound,
                    ChatDestination.Room.PUBLIC, "guest");

            assertEquals(List.of("[QQ] [Member] Alice [Tag] » external",
                    "[Matrix] [Member] Alice [Tag] » external",
                    "[irc] [Member] Alice [Tag] » external", "[Matrix] Guest » guest"),
                    f.received.get(bob.getUniqueId()));
            assertEquals(f.received.get(bob.getUniqueId()), f.console);
            assertTrue(f.geyserQueries.isEmpty());
            assertThrows(IllegalArgumentException.class, () -> f.deliverRouted(
                    "Matrix", unbound,
                    ChatDestination.Room.STAFF, "restricted"));
        }
    }

    @Test void gameEventsKeepTheirSourceWhenAnotherPluginEditsTheBodyAndNarrowsTheAudience() throws Exception {
        try (Fixture f = new Fixture()) {
            Player alice = f.game("Alice");
            Player bob = f.game("Bob");
            f.bedrock.add(alice.getUniqueId());
            AsyncChatEvent event = event(alice, Set.of(alice, bob), "original");
            f.chat.game().onChatRoute(event);
            event.message(Component.text("filtered"));
            event.viewers().remove(bob);
            Component rendered = event.renderer().render(alice, alice.displayName(), event.message(), alice);
            assertEquals("[Bedrock] [Member] Alice [Tag] » filtered", plain(rendered));
            f.chat.game().onChatCommit(event);

            assertTrue(f.published.isEmpty(), "A restricted final audience must not reach the web output");
            assertTrue(f.console.isEmpty(), "Paper owns the native event delivery");
        }
    }

    @Test void gameWikiLinkStaysAtomicWhenAnotherPluginAppendsPunctuation() throws Exception {
        try (Fixture f = new Fixture()) {
            Player alice = f.game("Alice");
            Player bob = f.game("Bob");
            var event = event(alice, Set.of(alice, bob), "看 zh.minecraft.wiki/w/%E9%92%BB%E7%9F%B3");
            f.chat.game().onChatRoute(event);
            event.message(event.message().append(Component.text("。@Bob")));
            assertEquals("看 [Minecraft Wiki: 钻石]。@Bob", plain(event.message()));
            f.chat.game().onChatCommit(event);

            var publication = ChatRecordCodec.publication(f.published.getFirst(), Optional.empty(), false);
            var body = publication.getPost();
            assertEquals(3, body.getPartsCount());
            var reference = body.getParts(1).getResolved().getReference();
            var link = reference.getLink();
            assertEquals("https://zh.minecraft.wiki/w/%E9%92%BB%E7%9F%B3", link.getUri());
            assertTrue(link.getAutomatic());
            assertEquals(List.of("Minecraft Wiki: 钻石"),
                    reference.getRunsList().stream().map(run -> run.getText()).toList());
            assertEquals("。@Bob", body.getParts(2).getResolved().getText().getText());
            assertFalse(body.getParts(2).hasPlayerMention());
        }
    }

    @Test void publicGameEventSubmitsOnlyStandaloneOfflineMentionCandidates() throws Exception {
        try (Fixture f = new Fixture()) {
            Player alice = f.game("Alice");
            Player viewer = f.game("Viewer");
            var event = event(alice, Set.of(alice, viewer),
                    "prefix<bold>@Bob</bold> @Bobé @Bob@server https://example.org/@Bob @bob:example.org 你好@<bold>Bo</bold>b");
            f.chat.game().onChatRoute(event);
            assertEquals(TextDecoration.State.TRUE, namedText(event.message(), "Bo").decoration(TextDecoration.BOLD));
            f.chat.game().onChatCommit(event);

            var message = f.published.getFirst();
            assertTrue(ChatMentions.fromContent(message.content()).players().isEmpty());
            var body = ChatRecordCodec.publication(message, Optional.empty(), false).getPost();
            var mentions = body.getPartsList().stream().filter(part -> part.hasPlayerMention())
                    .map(part -> part.getPlayerMention()).toList();
            assertEquals(1, mentions.size());
            assertEquals("Bob", mentions.getFirst().getPlayerName());
            assertEquals(List.of("@", "Bo", "b"), mentions.getFirst().getRunsList().stream().map(ChatTextRun::getText).toList());
            assertTrue(mentions.getFirst().getRuns(1).getStyle().getBold());
        }
    }

    @Test void cancelledGameEventsNeverPublishAndAcceptedEventsCommitTheFinalBody() throws Exception {
        try (Fixture f = new Fixture()) {
            Player alice = f.game("Alice");
            Player bob = f.game("Bob");
            AsyncChatEvent cancelled = event(alice, Set.of(alice, bob), "cancelled");
            f.chat.game().onChatRoute(cancelled);
            cancelled.setCancelled(true);
            f.chat.game().onChatCommit(cancelled);
            assertTrue(f.published.isEmpty());

            AsyncChatEvent accepted = event(alice, Set.of(alice, bob), "original");
            f.chat.game().onChatRoute(accepted);
            accepted.message(Component.text("accepted"));
            f.chat.game().onChatCommit(accepted);
            assertEquals(1, f.published.size());
            var committed = f.published.getFirst();
            assertEquals("accepted", committed.content().plainText());
            assertEquals("accepted", ChatRecordCodec
                    .publication(committed, Optional.empty(), false).getPost().getParts(0).getResolved().getText().getText());
        }
    }

    @Test void repeatsWorkAcrossPlayerConnections() throws Exception {
        try (Fixture f = new Fixture()) {
            UUID alice = f.account("Alice");
            Player bob = f.game("Bob");
            f.web(alice, ChatChannel.PUBLIC, null, "hello");
            f.chat.game().sendTemporaryPublicCommand(bob, "hello");
            assertEquals("[Member] Bob [Tag] » hello [+1]", f.received.get(bob.getUniqueId()).getLast());
        }
    }

    @Test void onlyRecipientsOfTheEventOrItsSharedCopyAreMentionable() throws Exception {
        try (Fixture f = new Fixture()) {
            Player alice = f.game("Alice");
            Player excluded = f.game("Excluded");
            f.settings.toggleMention(excluded.getUniqueId(), ChatMentionSetting.SOUND);
            UUID reader = f.account("Reader");
            f.directory.replaceWebPeers(List.of(
                    new GameChatDirectory.WebPeer(reader, "Reader", 1, false),
                    new GameChatDirectory.WebPeer(excluded.getUniqueId(), "Excluded", 1, false)));
            var shared = event(alice, Set.of(alice, excluded), "@Excluded @Reader");
            f.chat.game().onChatRoute(shared);
            f.chat.game().onChatCommit(shared);
            assertEquals(Set.of(excluded.getUniqueId(), reader),
                    ChatMentions.fromContent(f.published.getFirst().content()).players());

            var event = event(alice, Set.of(alice), "@Excluded @Reader @Offline");
            f.chat.game().onChatRoute(event);
            for (var component : event.message().iterable(net.kyori.adventure.text.ComponentIteratorType.DEPTH_FIRST))
                assertNull(component.clickEvent());
            f.chat.game().onChatCommit(event);
            assertEquals(1, f.published.size());
        }
    }

    @Test void aNarrowedNativeMessageCannotOfferAnUnrestrictedRepeat() throws Exception {
        try (Fixture f = new Fixture()) {
            var alice = f.game("Alice");
            var bob = f.game("Bob");
            f.chat.game().sendTemporaryPublicCommand(bob, "same");
            var narrowed = event(alice, Set.of(alice, bob), "same");
            f.chat.game().onChatRoute(narrowed);
            narrowed.viewers().remove(bob);
            f.chat.game().onChatCommit(narrowed);
            var line = narrowed.renderer().render(alice, alice.displayName(), narrowed.message(), alice);
            assertEquals("[Member] Alice [Tag] » same", plain(line));
            assertEquals(1, f.published.size());

            var publicEvent = event(alice, Set.of(alice, bob), "same");
            f.chat.game().onChatRoute(publicEvent);
            f.chat.game().onChatCommit(publicEvent);
            line = publicEvent.renderer().render(alice, alice.displayName(), publicEvent.message(), bob);
            assertNotNull(repeatIdentifier(line), "A restricted event does not change the public repeat chain");
        }
    }

    @Test void gameRosterKeepsSamplingPastTheIpcPlayerLimit() throws Exception {
        try (Fixture f = new Fixture()) {
            var alice = f.game("Alice");
            Player last = alice;
            for (int i = 0; i < 256; i++) last = f.game("Player" + i);
            f.samplers.getFirst().run();
            f.primaryThread = false;
            assertEquals(257, f.directory.gamePeers().size());
            assertNotNull(f.directory.peer(alice.getUniqueId(), last.getUniqueId()));
            assertTrue(f.published.isEmpty());
        }
    }

    @Test void delayedRestrictedEventsStayInTheirOriginalGameAudience() throws Exception {
        try (Fixture f = new Fixture()) {
            Player alice = f.game("Alice");
            Player bob = f.game("Bob");
            f.settings.setDelay(alice.getUniqueId(), ChatDelayOption.THREE_SECONDS);
            var event = event(alice, Set.of(alice), "local only");
            f.chat.game().onChatRoute(event);
            assertTrue(event.isCancelled());
            f.chat.game().onChatCommit(event);
            f.delayed.getFirst().run();
            assertEquals("[Member] Alice [Tag] » local only", f.received.get(alice.getUniqueId()).getLast());
            assertTrue(f.received.get(bob.getUniqueId()).isEmpty());
            assertTrue(f.published.isEmpty());
            assertTrue(f.console.isEmpty());
        }
    }

    @Test void delayedCommandsConsumeOneSendSlotBeforeTheyEnterTheQueue() throws Exception {
        try (Fixture f = new Fixture()) {
            var sender = f.game("Sender");
            f.settings.setDelay(sender.getUniqueId(), ChatDelayOption.THREE_SECONDS);
            for (int i = 0; i < 4; i++) f.chat.game().sendTemporaryPublicCommand(sender, "draft " + i);
            assertEquals(3, f.delayed.size(), "A rejected draft must not take up a delayed task");
            assertEquals(Message.CHAT_RATE_LIMITED.name(), f.received.get(sender.getUniqueId()).getLast());

            f.delayed.forEach(Runnable::run);
            assertEquals(List.of("draft 0", "draft 1", "draft 2"),
                    f.published.stream().map(message -> message.content().plainText()).toList());
        }
    }

    @Test void aFinalNativeAudienceNeverMovesToAnEqualButNewGameConnection() throws Exception {
        try (Fixture f = new Fixture()) {
            var sender = f.game("Sender");
            var oldViewer = f.game("Viewer");
            f.samplers.forEach(Runnable::run);
            var event = event(sender, Set.of(sender, oldViewer), "at the old session");
            f.primaryThread = false;
            f.chat.game().onChatRoute(event);
            f.chat.game().onChatCommit(event);
            f.primaryThread = true;

            var newViewer = f.game(oldViewer.getUniqueId(), oldViewer.getName());
            assertEquals(oldViewer, newViewer);
            assertNotSame(oldViewer, newViewer);
            f.scheduled.removeFirst().run();
            assertTrue(f.published.isEmpty(), "The final event audience did not include the new connection");
        }
    }

    @Test void customRepeatClickRunsOnTheMainThreadAndStopsWorkingAfterShutdown() throws Exception {
        try (Fixture f = new Fixture()) {
            Player alice = f.game("Alice");
            Player bob = f.game("Bob");
            for (Player sender : List.of(alice, bob)) {
                f.chat.game().sendTemporaryPublicCommand(sender, "hello");
            }
            f.published.clear();
            Key identifier = repeatIdentifier(f.consoleComponents.getLast());
            f.primaryThread = false;
            f.click(alice, identifier);
            assertTrue(f.published.isEmpty());
            assertEquals(1, f.scheduled.size());
            f.primaryThread = true;
            f.scheduled.removeFirst().run();
            ChatMessage repeated = f.published.getLast();
            assertEquals("hello", repeated.content().plainText());
            assertEquals(alice.getUniqueId(), repeated.sender().minecraftId().orElseThrow());
            assertEquals(ChatChannel.PUBLIC, repeated.channel());

            f.primaryThread = false;
            f.click(alice, identifier);
            f.primaryThread = true;
            f.chat.disable();
            f.scheduled.removeFirst().run();
            f.click(alice, identifier);
            assertEquals(1, f.published.size());
        }
    }

    @Test void customPrivateRepeatClickOnlySendsToTheOriginalConversationPartner() throws Exception {
        try (Fixture f = new Fixture()) {
            Player alice = f.game("Alice");
            Player bob = f.game("Bob");
            Player eve = f.game("Eve");
            for (Player sender : List.of(alice, bob)) {
                Player target = sender == alice ? bob : alice;
                f.chat.game().sendTemporaryPrivateCommand(sender, target.getName(), "secret");
            }
            f.published.clear();
            Key identifier = repeatIdentifier(f.receivedComponents.get(alice.getUniqueId()).getLast());
            f.click(eve, identifier);
            assertTrue(f.published.isEmpty());
            f.click(alice, identifier);
            assertEquals(Set.of(alice.getUniqueId(), bob.getUniqueId()), f.published.getLast().audience().participants());
            assertEquals("secret", f.published.getLast().content().plainText());
            assertTrue(f.received.get(eve.getUniqueId()).isEmpty());
        }
    }

    @Test void customStaffRepeatClickRechecksPermissionBeforeSending() throws Exception {
        try (Fixture f = new Fixture()) {
            Player alice = f.game("Alice");
            Player bob = f.game("Bob");
            for (Player sender : List.of(alice, bob)) {
                f.chat.game().sendStaffCommand(sender, "staff");
            }
            f.published.clear();
            Key identifier = repeatIdentifier(f.receivedComponents.get(alice.getUniqueId()).getLast());
            f.staffDenied.add(alice.getUniqueId());
            f.click(alice, identifier);
            assertTrue(f.published.isEmpty());
            assertEquals(Message.CHAT_STAFF_PERMISSION_MISSING.name(), f.received.get(alice.getUniqueId()).getLast());

            f.staffDenied.clear();
            f.click(alice, identifier);
            assertEquals(ChatChannel.STAFF, f.published.getLast().channel());
            assertEquals("staff", f.published.getLast().content().plainText());
        }
    }

    private static Key repeatIdentifier(Component message) {
        ClickEvent<?> click = namedText(message, " [+1]").clickEvent();
        assertEquals(ClickEvent.Action.CUSTOM, click.action());
        return ((ClickEvent.Payload.Custom) click.payload()).key();
    }

    private static final class Fixture implements AutoCloseable {
        final Map<UUID, String> names = new HashMap<>();
        final Map<UUID, Player> players = new HashMap<>();
        final Map<UUID, ItemStack> heldItems = new HashMap<>();
        final Map<UUID, Language> languages = new HashMap<>();
        final Set<UUID> bedrock = new HashSet<>();
        final Set<UUID> staffDenied = new HashSet<>();
        final List<UUID> geyserQueries = new ArrayList<>();
        final Map<UUID, List<String>> received = new HashMap<>();
        final Map<UUID, List<Component>> receivedComponents = new HashMap<>();
        final List<Runnable> scheduled = new ArrayList<>();
        boolean primaryThread = true;
        final List<String> console = new ArrayList<>();
        final List<Component> consoleComponents = new ArrayList<>();
        final List<ChatMessage> published = new ArrayList<>();
        final TipTestScope tips = new TipTestScope();
        final Field serverField = Bukkit.class.getDeclaredField("server");
        final Object previousServer;
        final ChatModule chat;
        final GameChatDirectory directory = new GameChatDirectory(_ -> true, (_, _) -> true);
        final ChatSettingsStore settings = new ChatSettingsStore(null);
        final List<Runnable> delayed = new ArrayList<>();
        final List<Runnable> samplers = new ArrayList<>();
        String namePrefix = "<gold>[Member] </gold>";
        String usernameColor = "<white>";

        Fixture() throws Exception {
            Plugin plugin = proxy(Plugin.class, (_, m, _) -> switch (m.getName()) {
                case "namespace", "getName" -> "mik";
                case "getServer" -> Bukkit.getServer();
                case "getLogger" -> Logger.getAnonymousLogger();
                case "isEnabled" -> true;
                default -> throw new AssertionError(m.getName());
            });
            CachedMetaData meta = proxy(CachedMetaData.class, (_, m, a) -> switch (m.getName()) {
                case "getPrefix" -> namePrefix;
                case "getSuffix" -> "<green> [Tag]</green>";
                case "getMetaValue" -> {
                    assertEquals("username-color", a[0]);
                    yield usernameColor;
                }
                default -> throw new AssertionError(m.getName());
            });
            CachedDataManager cache = proxy(CachedDataManager.class, (_, _, _) -> meta);
            User user = proxy(User.class, (_, _, _) -> cache);
            UserManager users = proxy(UserManager.class, (_, _, _) -> {
                assertTrue(primaryThread, "Async chat must not resolve LuckPerms presentation");
                return user;
            });
            LuckPerms luckPerms = proxy(LuckPerms.class, (_, _, _) -> users);
            ServicesManager services = proxy(ServicesManager.class, (_, _, _) -> new RegisteredServiceProvider<>(LuckPerms.class, luckPerms, ServicePriority.Normal, plugin));
            PluginManager plugins = proxy(PluginManager.class, (_, _, _) -> null);
            ConsoleCommandSender output = proxy(ConsoleCommandSender.class, (_, m, a) -> {
                if (m.getName().equals("sendMessage")) {
                    consoleComponents.add((Component) a[0]);
                    console.add(plain((Component) a[0]));
                    return null;
                }
                throw new AssertionError(m.getName());
            });
            BukkitTask task = proxy(BukkitTask.class, (o, m, a) -> switch (m.getName()) {
                case "cancel" -> null;
                case "equals" -> o == a[0];
                case "hashCode" -> System.identityHashCode(o);
                default -> throw new AssertionError(m.getName());
            });
            BukkitScheduler scheduler = proxy(BukkitScheduler.class, (_, m, a) -> switch (m.getName()) {
                case "runTask" -> { scheduled.add((Runnable) a[1]); yield task; }
                case "runTaskLater" -> { delayed.add((Runnable) a[1]); yield task; }
                case "runTaskTimer" -> { samplers.add((Runnable) a[1]); yield task; }
                default -> throw new AssertionError(m.getName());
            });
            Server server = proxy(Server.class, (_, m, a) -> switch (m.getName()) {
                case "isPrimaryThread" -> primaryThread;
                case "getScheduler" -> scheduler;
                case "getPlayer" -> {
                    assertTrue(primaryThread, "Async chat must not look up Bukkit players");
                    yield players.get(a[0]);
                }
                case "getOfflinePlayer" -> {
                    assertTrue(primaryThread, "Async chat must not look up Bukkit players");
                    yield proxy(OfflinePlayer.class, (_, method, _) -> switch (method.getName()) {
                        case "getUniqueId" -> a[0];
                        case "getName" -> names.get(a[0]);
                        default -> throw new AssertionError(method.getName());
                    });
                }
                case "getOnlinePlayers" -> {
                    assertTrue(primaryThread, "Async chat must not sample Bukkit players");
                    yield players.values();
                }
                case "getServicesManager" -> services;
                case "getPluginManager" -> plugins;
                case "getConsoleSender" -> output;
                default -> throw new AssertionError(m.getName());
            });
            serverField.setAccessible(true); previousServer = serverField.get(null); serverField.set(null, server);
            LanguageService language = new LanguageService(null) {
                @Override public Language language(Player player) {
                    assertTrue(primaryThread, "Async chat must not resolve the live player language");
                    return languages.getOrDefault(player.getUniqueId(), Language.EN_US);
                }
                @Override public String t(Player player, Message message, Object... args) { return t(language(player), message, args); }
                @Override public Component rich(Player player, Message message, NamedTextColor color, RichArg... args) {
                    assertTrue(primaryThread);
                    return Component.text(message.name(), color);
                }
                @Override public String t(Language lang, Message message, Object... args) {
                    return switch (message) {
                        case CHAT_PRIVATE_LABEL -> "MSG";
                        case PLAYER_BEDROCK_LABEL -> lang == Language.ZH_CN ? "基岩" : "Bedrock";
                        case PLAYER_WEB_LABEL -> "Web";
                        case CHAT_STAFF_LABEL -> "Staff";
                        default -> message.name();
                    };
                }
            };
            PlayerNameTagRenderer tags = new PlayerNameTagRenderer(plugin); tags.enable();
            PlayerSourceRenderer sources = new PlayerSourceRenderer(id -> {
                assertTrue(primaryThread, "Async chat must not query the game client");
                geyserQueries.add(id);
                return bedrock.contains(id);
            }, language);
            chat = new ChatModule(plugin, directory, _ -> () -> true, new GameMentionNotifier(null, language, settings),
                    language, settings, tags, sources, tips, (_, _) -> false);
            chat.enable(new ChatRelay() {
                @Override public CompletableFuture<Outcome> publish(ChatMessage message, Optional<Set<UUID>> visibility,
                        boolean requireRemoteRecipient, java.util.function.BooleanSupplier authorized) {
                    published.add(message);
                    return CompletableFuture.completedFuture(Outcome.SAVED);
                }

                @Override public void publishPresence(ChatPresence notice, Optional<Set<UUID>> visibility) { }
            });
        }

        UUID account(String name) { UUID id = UUID.randomUUID(); names.put(id, name); return id; }

        Player game(String name) {
            return game(account(name), name);
        }

        Player game(UUID id, String name) {
            received.put(id, new ArrayList<>());
            receivedComponents.put(id, new ArrayList<>());
            PlayerInventory inventory = proxy(PlayerInventory.class, (_, m, _) -> switch (m.getName()) {
                case "getItem" -> null;
                case "getItemInMainHand" -> heldItems.get(id);
                default -> throw new AssertionError(m.getName());
            });
            Player player = proxy(Player.class, (_, m, a) -> switch (m.getName()) {
                case "getUniqueId" -> id;
                case "getName" -> name;
                case "displayName", "getInventory", "hasPermission", "isOnline", "isVisibleByDefault", "canSee" -> {
                    assertTrue(primaryThread, "Async chat read " + m.getName());
                    yield switch (m.getName()) {
                        case "displayName" -> Component.text(name);
                        case "getInventory" -> inventory;
                        case "hasPermission" -> !staffDenied.contains(id);
                        default -> true;
                    };
                }
                case "getLastLogin" -> 0L;
                case "sendActionBar", "playSound", "getLocation" -> {
                    assertTrue(primaryThread, "Chat effects belong to the game thread");
                    yield null;
                }
                case "getPersistentDataContainer" -> proxy(org.bukkit.persistence.PersistentDataContainer.class,
                        (_, method, args) -> {
                            if (method.getName().equals("getOrDefault")) return args[2];
                            if (method.getName().equals("set")) return null;
                            throw new AssertionError(method.getName());
                        });
                case "sendMessage" -> {
                    received.get(id).add(plain((Component) a[0]));
                    receivedComponents.get(id).add((Component) a[0]);
                    yield null;
                }
                case "hashCode" -> id.hashCode();
                case "equals" -> a[0] instanceof Player other && id.equals(other.getUniqueId());
                default -> throw new AssertionError(m.getName());
            });
            players.put(id, player); return player;
        }

        void click(Player player, Key identifier) {
            PlayerGameConnection connection = proxy(PlayerGameConnection.class, (_, m, _) -> switch (m.getName()) {
                case "isConnected" -> true;
                case "getPlayer" -> {
                    assertTrue(primaryThread, "Custom clicks must read the player on the main thread");
                    yield player;
                }
                default -> throw new AssertionError(m.getName());
            });
            chat.game().onCustomClick(new PlayerCustomClickEvent(identifier, connection) {
                @Override public BinaryTagHolder getTag() { return null; }
                @Override public DialogResponseView getDialogResponseView() { return null; }
            });
        }

        void deliverRouted(String platform, ChatSender sender, ChatDestination.Room room, String text) {
            var exported = new ChatSender(sender.displayName(), sender.minecraftId(), Optional.of(new ExternalIdentity(
                    new ExternalIdentityKey(platform, "example.org", "", "external"), sender.displayName())),
                    sender.minecraftName());
            var delivery = ChatTestMessages.routedSocial(exported, room,
                    ChatBody.newBuilder().addParts(ChatPart.newBuilder().setText(ChatTextRun.newBuilder().setText(text))));
            chat.game().deliverRouted(delivery);
        }

        void web(UUID author, ChatChannel channel, ChatSender target, String text) {
            var message = new ChatMessage(ChatMessageId.random(), ChatOrigin.Player.WEB, ChatSender.minecraft(author, names.get(author)),
                    channel, target, Instant.EPOCH, ChatContent.plain(text));
            chat.game().deliverRouted(new ChatDelivery(message, Optional.empty(), new ChatMentions(Set.of(), false)));
        }

        @Override public void close() throws IllegalAccessException {
            try {
                chat.disable();
                tips.close();
            } finally { serverField.set(null, previousServer); }
        }
    }

    private static AsyncChatEvent event(Player sender, Set<Player> viewers, String text) {
        return new AsyncChatEvent(false, sender, new HashSet<Audience>(viewers), ChatRenderer.defaultRenderer(),
                Component.text(text), Component.text(text), SignedMessage.system(text, Component.text(text)));
    }

    private static Component namedText(Component value, String name) {
        if (value instanceof TextComponent text && text.content().equals(name)) return value;
        for (Component child : value.children()) {
            Component found = namedText(child, name);
            if (found != null) return found;
        }
        return null;
    }

    private static String plain(Component value) { return PlainTextComponentSerializer.plainText().serialize(value); }
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
