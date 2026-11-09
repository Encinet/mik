package org.encinet.mik.feature.community.chat;

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
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.cacheddata.CachedDataManager;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.*;
import org.bukkit.scheduler.BukkitScheduler;
import org.encinet.mik.feature.community.chat.bridge.SocialChatPublishReport;
import org.encinet.mik.feature.community.chat.bridge.ChatParticipants;
import org.encinet.mik.feature.community.chat.game.GameChatController;
import org.encinet.mik.feature.community.chat.settings.ChatSettingsStore;
import org.encinet.mik.feature.community.chat.game.GameMentionNotifier;
import org.encinet.mik.feature.community.chat.mention.PlayerMentionIndex;
import org.encinet.mik.feature.community.chat.mention.PlayerMentions;
import org.encinet.mik.feature.community.chat.model.*;
import org.encinet.mik.feature.player.guidance.TipTestScope;
import org.encinet.mik.feature.player.profile.PlayerNameTagRenderer;
import org.encinet.mik.feature.player.profile.PlayerSourceRenderer;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class PlayerChatDeliveryTest {
    @Test void gameAndWebUseTheSameResolvedIdentityAndDelivery() throws Exception {
        try (Fixture f = new Fixture()) {
            Player alice = f.game("Alice");
            Player bob = f.game("Bob");
            for (ChatOrigin.Player origin : List.of(new ChatOrigin.Minecraft(alice.getUniqueId()), new ChatOrigin.Web(alice.getUniqueId()))) {
                ChatMessage message = f.message(origin, ChatConversation.minecraftPublic(), "hello");
                assertEquals("<gold>[Member] </gold>", message.submission().sender().prefix());
                assertEquals("<green> [Tag]</green>", message.submission().sender().suffix());
                f.namePrefix = "<red>[Changed] </red>";
                f.chat.messages().publish(ChatDelivery.managed(message, null, null));
                f.namePrefix = "<gold>[Member] </gold>";
                assertSame(message, f.social.getLast());
            }
            assertEquals(List.of("[Member] Alice [Tag] » hello", "[Web] [Member] Alice [Tag] » hello"), f.received.get(bob.getUniqueId()));
            assertEquals(f.received.get(bob.getUniqueId()), f.console);
            assertEquals(2, f.social.size());
        }
    }

    @Test void gameAndOfflineWebNamesUseTheAcceptedLuckPermsColorSnapshot() throws Exception {
        try (Fixture f = new Fixture()) {
            Player alice = f.game("Alice");
            f.game("Bob");
            UUID webOnly = f.account("WebOnly");
            for (ChatOrigin.Player origin : List.of(new ChatOrigin.Minecraft(alice.getUniqueId()), new ChatOrigin.Web(webOnly))) {
                f.usernameColor = "<yellow>";
                ChatMessage message = f.message(origin, ChatConversation.minecraftPublic(), "hello");
                assertEquals(NamedTextColor.YELLOW.value(), message.submission().sender().usernameColor());
                f.usernameColor = "<white>";
                f.chat.messages().publish(ChatDelivery.managed(message, null, null));
                Component rendered = f.consoleComponents.getLast();
                assertEquals(NamedTextColor.YELLOW, namedText(rendered, f.names.get(origin.playerId())).color());
                assertEquals(NamedTextColor.GOLD, namedText(rendered, "[Member] ").color());
            }
            assertEquals(NamedTextColor.WHITE.value(), f.message(new ChatOrigin.Minecraft(alice.getUniqueId()),
                    ChatConversation.minecraftPublic(), "member").submission().sender().usernameColor());
        }
    }

    @Test void privateAndStaffMessagesKeepNormalIdentitiesWithoutPublicSideEffects() throws Exception {
        try (Fixture f = new Fixture()) {
            UUID alice = f.account("Alice");
            Player bob = f.game("Bob");
            f.chat.messages().publish(ChatDelivery.managed(f.message(new ChatOrigin.Web(alice), ChatConversation.minecraftStaff(), "staff"), null, null));
            f.chat.messages().publish(ChatDelivery.managed(f.message(new ChatOrigin.Web(alice),
                    ChatConversation.minecraftPrivate(alice, bob.getUniqueId()), "private"), ChatSender.minecraft(bob.getUniqueId(), "Bob"), null));
            assertEquals(List.of("[Staff] [Web] [Member] Alice [Tag] » staff",
                    "[MSG] [Web] [Member] Alice [Tag] -> [Member] Bob [Tag] » private"), f.received.get(bob.getUniqueId()));
            assertTrue(f.console.isEmpty());
            assertTrue(f.social.isEmpty());
        }
    }

    @Test void bedrockMarksOnlyGeyserGameMessagesEvenWhenTheSameAccountUsesTheWeb() throws Exception {
        try (Fixture f = new Fixture()) {
            Player alice = f.game("Alice");
            Player bob = f.game("Bob");
            UUID sender = alice.getUniqueId();
            f.bedrock.add(sender);
            f.bedrock.add(bob.getUniqueId());
            f.chat.messages().publish(ChatDelivery.managed(f.message(new ChatOrigin.Minecraft(sender),
                    ChatConversation.minecraftPublic(), "game"), null, null));
            f.chat.messages().publish(ChatDelivery.managed(f.message(new ChatOrigin.Web(sender),
                    ChatConversation.minecraftPublic(), "web"), null, null));
            f.chat.messages().publish(ChatDelivery.managed(f.message(new ChatOrigin.Minecraft(sender),
                    ChatConversation.minecraftStaff(), "staff"), null, null));
            f.chat.messages().publish(ChatDelivery.managed(f.message(new ChatOrigin.Minecraft(sender),
                    ChatConversation.minecraftPrivate(sender, bob.getUniqueId()), "private"), ChatSender.minecraft(bob.getUniqueId(), "Bob"), null));
            f.players.remove(sender);
            f.chat.messages().publish(ChatDelivery.managed(f.message(new ChatOrigin.Web(sender),
                    ChatConversation.minecraftPublic(), "web only"), null, null));

            assertEquals(List.of(
                    "[Bedrock] [Member] Alice [Tag] » game",
                    "[Web] [Member] Alice [Tag] » web",
                    "[Staff] [Bedrock] [Member] Alice [Tag] » staff",
                    "[MSG] [Bedrock] [Member] Alice [Tag] -> [Member] Bob [Tag] » private",
                    "[Web] [Member] Alice [Tag] » web only"), f.received.get(bob.getUniqueId()));
            assertFalse(f.geyserQueries.contains(bob.getUniqueId()), "A recipient's live device is not the message source");
        }
    }

    @Test void externalSourceTravelsWithTheMessageAndNeverInheritsTheBoundPlayersGameClient() throws Exception {
        try (Fixture f = new Fixture()) {
            Player alice = f.game("Alice");
            Player bob = f.game("Bob");
            f.bedrock.add(alice.getUniqueId());
            for (String platform : List.of("QQ", "Matrix", "IRC")) {
                var origin = new ChatOrigin.Social(platform.toLowerCase(Locale.ROOT), platform, "room", "event");
                var sender = new ChatSender("Alice", Optional.of(alice.getUniqueId()), Optional.empty(),
                        "Alice", "<gold>[Member] </gold>", "<green> [Tag]</green>", 0xFFFFFF);
                f.chat.messages().accept(new ChatSubmission(ChatMessageId.random(), origin, sender,
                        ChatConversation.minecraftPublic(), "external", ChatReferences.empty(), Instant.EPOCH,
                        ChatProcessingContext.external()));
            }
            var unbound = new ChatSender("Guest", Optional.empty(), Optional.empty(), "", "", "", 0xFFFFFF);
            f.chat.messages().accept(new ChatSubmission(ChatMessageId.random(),
                    new ChatOrigin.Social("matrix", "Matrix", "room", "unbound"), unbound,
                    ChatConversation.minecraftPublic(), "guest", ChatReferences.empty(), Instant.EPOCH,
                    ChatProcessingContext.external()));

            assertEquals(List.of("[QQ] [Member] Alice [Tag] » external",
                    "[Matrix] [Member] Alice [Tag] » external",
                    "[IRC] [Member] Alice [Tag] » external", "[Matrix] Guest » guest"),
                    f.received.get(bob.getUniqueId()));
            assertEquals(f.received.get(bob.getUniqueId()), f.console);
            assertTrue(f.geyserQueries.isEmpty());
            assertEquals(4, f.social.size());
            assertThrows(IllegalArgumentException.class, () -> f.chat.messages().accept(new ChatSubmission(
                    ChatMessageId.random(), new ChatOrigin.Social("matrix", "Matrix", "room", "bad"), unbound,
                    ChatConversation.minecraftStaff(), "restricted", ChatReferences.empty(), Instant.EPOCH,
                    ChatProcessingContext.external())));
            assertEquals(4, f.social.size());
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
            assertTrue(f.social.isEmpty(), "A reduced game audience must not become a public social broadcast");
            assertTrue(f.console.isEmpty(), "Paper owns the native event delivery");
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
            assertTrue(f.social.isEmpty());

            AsyncChatEvent accepted = event(alice, Set.of(alice, bob), "original");
            f.chat.game().onChatRoute(accepted);
            accepted.message(Component.text("accepted"));
            f.chat.game().onChatCommit(accepted);
            assertEquals(1, f.published.size());
            assertEquals("accepted", f.published.getFirst().content().plainText());
            assertEquals(f.published, f.social);
        }
    }

    @Test void repeatsWorkAcrossPlayerConnectionsAndTextCapabilitiesStayRestricted() throws Exception {
        try (Fixture f = new Fixture()) {
            UUID alice = f.account("Alice");
            Player bob = f.game("Bob");
            f.chat.messages().publish(ChatDelivery.managed(f.message(new ChatOrigin.Web(alice), ChatConversation.minecraftPublic(), "hello"), null, null));
            f.chat.messages().publish(ChatDelivery.managed(f.message(new ChatOrigin.Minecraft(bob.getUniqueId()), ChatConversation.minecraftPublic(), "hello"), null, null));
            assertEquals("[Member] Bob [Tag] » hello [+1]", f.received.get(bob.getUniqueId()).getLast());

            ChatMessage nativeMessage = f.message(new ChatOrigin.Minecraft(alice), ChatConversation.minecraftPublic(), "<red>@Bob @all %i github.com/encinet/mik");
            ChatMessage webMessage = f.message(new ChatOrigin.Web(alice), ChatConversation.minecraftPublic(), nativeMessage.submission().sourceText());
            assertEquals(nativeMessage.content(), webMessage.content());
            assertEquals(nativeMessage.effects(), webMessage.effects());
            assertTrue(webMessage.content().plainText().contains("<red>"));
            assertTrue(webMessage.content().plainText().contains("@all %i"));
            assertEquals(1, webMessage.content().nodes().stream().filter(ChatNode.PlayerMention.class::isInstance).count());
            assertEquals(1, webMessage.content().nodes().stream().filter(ChatNode.Link.class::isInstance).count());
        }
    }

    @Test void customRepeatClickRunsOnTheMainThreadAndStopsWorkingAfterShutdown() throws Exception {
        try (Fixture f = new Fixture()) {
            Player alice = f.game("Alice");
            Player bob = f.game("Bob");
            for (Player sender : List.of(alice, bob)) {
                f.chat.messages().publish(ChatDelivery.managed(f.message(new ChatOrigin.Minecraft(sender.getUniqueId()),
                        ChatConversation.minecraftPublic(), "hello"), null, null));
            }
            f.published.clear(); f.publishedViewers.clear();
            Key identifier = repeatIdentifier(f.consoleComponents.getLast());
            f.primaryThread = false;
            f.click(alice, identifier);
            assertTrue(f.published.isEmpty());
            assertEquals(1, f.scheduled.size());
            f.primaryThread = true;
            f.scheduled.removeFirst().run();
            ChatSubmission repeated = f.published.getLast().submission();
            assertEquals("hello", repeated.sourceText());
            assertEquals(alice.getUniqueId(), repeated.sender().minecraftId().orElseThrow());
            assertEquals(ChatChannel.PUBLIC, repeated.conversation().channel());

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
                f.chat.messages().publish(ChatDelivery.managed(f.message(new ChatOrigin.Minecraft(sender.getUniqueId()),
                                ChatConversation.minecraftPrivate(sender.getUniqueId(), target.getUniqueId()), "secret"), ChatSender.minecraft(target.getUniqueId(), target.getName()), null));
            }
            f.published.clear(); f.publishedViewers.clear();
            Key identifier = repeatIdentifier(f.receivedComponents.get(alice.getUniqueId()).getLast());
            f.click(eve, identifier);
            assertTrue(f.published.isEmpty());
            f.click(alice, identifier);
            assertEquals(ChatConversation.minecraftPrivate(alice.getUniqueId(), bob.getUniqueId()),
                    f.published.getLast().submission().conversation());
            assertEquals("secret", f.published.getLast().submission().sourceText());
            assertEquals(Set.of(alice.getUniqueId(), bob.getUniqueId()), f.publishedViewers.getLast());
            assertTrue(f.received.get(eve.getUniqueId()).isEmpty());
        }
    }

    @Test void customStaffRepeatClickRechecksPermissionBeforeSending() throws Exception {
        try (Fixture f = new Fixture()) {
            Player alice = f.game("Alice");
            Player bob = f.game("Bob");
            for (Player sender : List.of(alice, bob)) {
                f.chat.messages().publish(ChatDelivery.managed(f.message(new ChatOrigin.Minecraft(sender.getUniqueId()),
                        ChatConversation.minecraftStaff(), "staff"), null, null));
            }
            f.published.clear(); f.publishedViewers.clear();
            Key identifier = repeatIdentifier(f.receivedComponents.get(alice.getUniqueId()).getLast());
            f.staffDenied.add(alice.getUniqueId());
            f.click(alice, identifier);
            assertTrue(f.published.isEmpty());
            assertEquals(Message.CHAT_STAFF_PERMISSION_MISSING.name(), f.received.get(alice.getUniqueId()).getLast());

            f.staffDenied.clear();
            f.click(alice, identifier);
            assertEquals(ChatChannel.STAFF, f.published.getLast().submission().conversation().channel());
            assertEquals("staff", f.published.getLast().submission().sourceText());
            assertTrue(f.social.isEmpty());
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
        final Set<UUID> bedrock = new HashSet<>();
        final Set<UUID> staffDenied = new HashSet<>();
        final List<UUID> geyserQueries = new ArrayList<>();
        final Map<UUID, List<String>> received = new HashMap<>();
        final Map<UUID, List<Component>> receivedComponents = new HashMap<>();
        final List<Runnable> scheduled = new ArrayList<>();
        boolean primaryThread = true;
        final List<String> console = new ArrayList<>();
        final List<Component> consoleComponents = new ArrayList<>();
        final List<ChatMessage> social = new ArrayList<>();
        final List<ChatMessage> published = new ArrayList<>();
        final List<Set<UUID>> publishedViewers = new ArrayList<>();
        final TipTestScope tips = new TipTestScope();
        final Field serverField = Bukkit.class.getDeclaredField("server");
        final Object previousServer;
        final ChatModule chat;
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
            UserManager users = proxy(UserManager.class, (_, _, _) -> user);
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
            BukkitScheduler scheduler = proxy(BukkitScheduler.class, (_, m, a) -> {
                assertEquals("runTask", m.getName());
                scheduled.add((Runnable) a[1]);
                return null;
            });
            Server server = proxy(Server.class, (_, m, a) -> switch (m.getName()) {
                case "isPrimaryThread" -> primaryThread;
                case "getScheduler" -> scheduler;
                case "getPlayer" -> players.get(a[0]);
                case "getOfflinePlayer" -> proxy(OfflinePlayer.class, (_, method, _) -> switch (method.getName()) {
                    case "getUniqueId" -> a[0];
                    case "getName" -> names.get(a[0]);
                    default -> throw new AssertionError(method.getName());
                });
                case "getOnlinePlayers" -> players.values();
                case "getServicesManager" -> services;
                case "getPluginManager" -> plugins;
                case "getConsoleSender" -> output;
                default -> throw new AssertionError(m.getName());
            });
            serverField.setAccessible(true); previousServer = serverField.get(null); serverField.set(null, server);
            LanguageService language = new LanguageService(null) {
                @Override public Language language(Player player) { return Language.DEFAULT; }
                @Override public String t(Player player, Message message, Object... args) { return t(Language.DEFAULT, message, args); }
                @Override public String t(Language lang, Message message, Object... args) {
                    return switch (message) {
                        case CHAT_PRIVATE_LABEL -> "MSG";
                        case PLAYER_BEDROCK_LABEL -> "Bedrock";
                        case PLAYER_WEB_LABEL -> "Web";
                        case CHAT_STAFF_LABEL -> "Staff";
                        default -> message.name();
                    };
                }
            };
            PlayerNameTagRenderer tags = new PlayerNameTagRenderer(plugin); tags.enable();
            PlayerSourceRenderer sources = new PlayerSourceRenderer(id -> {
                geyserQueries.add(id);
                return bedrock.contains(id);
            }, language);
            chat = new ChatModule(plugin, new GameMentionNotifier(plugin, null, language, null), language,
                    new ChatSettingsStore(null), tags, sources, message -> { social.add(message); return SocialChatPublishReport.empty(); }, null, tips, (_, _) -> false);
            ChatParticipants directory = proxy(ChatParticipants.class, (_, m, a) -> switch (m.getName()) {
                case "channelPeers" -> List.of();
                case "peer" -> {
                    UUID id = (UUID) a[1];
                    yield players.containsKey(id) ? new ChatParticipant(id, names.get(id), true, 0) : null;
                }
                case "gameRecipients" -> {
                    ChatAudience audience = (ChatAudience) a[0];
                    yield players.keySet().stream().filter(id -> audience.includes(id, !staffDenied.contains(id)))
                            .collect(java.util.stream.Collectors.toUnmodifiableSet());
                }
                default -> throw new AssertionError(m.getName());
            });
            Field participantField = GameChatController.class.getDeclaredField("participants");
            participantField.setAccessible(true); participantField.set(chat.game(), directory);
            Field allowanceField = GameChatController.class.getDeclaredField("allowance");
            allowanceField.setAccessible(true); allowanceField.set(chat.game(), (org.encinet.mik.feature.community.chat.bridge.ChatSendAllowance) _ -> true);
            chat.messages().connect(directory, chat.game()::deliver, delivery -> {
                published.add(delivery.message());
                publishedViewers.add(delivery.game() instanceof ChatDelivery.Native nativeGame ? nativeGame.viewers()
                        : directory.gameRecipients(delivery.audience()));
            });
        }

        UUID account(String name) { UUID id = UUID.randomUUID(); names.put(id, name); return id; }

        Player game(String name) {
            UUID id = account(name); received.put(id, new ArrayList<>());
            receivedComponents.put(id, new ArrayList<>());
            PlayerInventory inventory = proxy(PlayerInventory.class, (_, m, _) -> switch (m.getName()) {
                case "getItem", "getItemInMainHand" -> null;
                default -> throw new AssertionError(m.getName());
            });
            Player player = proxy(Player.class, (o, m, a) -> switch (m.getName()) {
                case "getUniqueId" -> id;
                case "getName" -> name;
                case "displayName" -> Component.text(name);
                case "getInventory" -> inventory;
                case "hasPermission" -> !staffDenied.contains(id);
                case "isOnline" -> true;
                case "sendMessage" -> {
                    received.get(id).add(plain((Component) a[0]));
                    receivedComponents.get(id).add((Component) a[0]);
                    yield null;
                }
                case "hashCode" -> id.hashCode();
                case "equals" -> o == a[0];
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

        ChatMessage message(ChatOrigin.Player origin, ChatConversation conversation, String text) {
            UUID sender = origin.playerId();
            PlayerMentions mentions = PlayerMentions.of(players.entrySet().stream()
                    .map(entry -> new PlayerMentionIndex.Target(entry.getKey(), names.get(entry.getKey()))).toList());
            ChatSender untrustedTags = new ChatSender(names.get(sender), Optional.of(sender), Optional.empty(), names.get(sender), "[Admin] ", "", 0xFF5555);
            return chat.messages().process(new ChatSubmission(ChatMessageId.random(), origin, untrustedTags, conversation,
                    text, ChatReferences.empty(), Instant.EPOCH, ChatProcessingContext.playerText(mentions)));
        }

        @Override public void close() throws IllegalAccessException { tips.close(); serverField.set(null, previousServer); }
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
