package org.encinet.mik.feature.community.ai;

import com.google.gson.JsonPrimitive;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.adventure.PaperAdventure;
import io.papermc.paper.adventure.providers.ClickCallbackProviderImpl;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.minecraft.nbt.TagParser;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.encinet.mik.feature.community.ai.game.GameTool;
import org.encinet.mik.feature.community.ai.game.GameToolCatalog;
import org.encinet.mik.integration.mikd.RequestPort;
import org.encinet.mik.protocol.AssistantAskLabel;
import org.encinet.mik.protocol.AssistantBlock;
import org.encinet.mik.protocol.AssistantBlocks;
import org.encinet.mik.protocol.AssistantCodeBlock;
import org.encinet.mik.protocol.AssistantCommand;
import org.encinet.mik.protocol.AssistantCommand.OperationCase;
import org.encinet.mik.protocol.AssistantFailure;
import org.encinet.mik.protocol.AssistantFailure.Reason;
import org.encinet.mik.protocol.AssistantHeading;
import org.encinet.mik.protocol.AssistantInline;
import org.encinet.mik.protocol.AssistantList;
import org.encinet.mik.protocol.AssistantListItem;
import org.encinet.mik.protocol.AssistantPending;
import org.encinet.mik.protocol.AssistantPart;
import org.encinet.mik.protocol.AssistantReply;
import org.encinet.mik.protocol.AssistantResult;
import org.encinet.mik.protocol.AssistantRow;
import org.encinet.mik.protocol.AssistantSignal;
import org.encinet.mik.protocol.AssistantSpan;
import org.encinet.mik.protocol.AssistantSpan.Style;
import org.encinet.mik.protocol.AssistantTable;
import org.encinet.mik.protocol.AssistantToolCall;
import org.encinet.mik.protocol.AssistantToolFailure;
import org.encinet.mik.protocol.AssistantToolResult;
import org.encinet.mik.shared.i18n.FluentTestSupport;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiModuleTest {
    private static final String BUILDING_ID = "0123456789abcdef0123456789abcdef";
    private static final String WEATHER = "game_read_weather";
    private static final AssistantSignal SIGNAL = AssistantSignal.getDefaultInstance();

    private final ArrayDeque<Exchange> requests = new ArrayDeque<>();
    private final Map<UUID, Player> online = new HashMap<>();
    private final Map<Player, List<Component>> inbox = new HashMap<>();
    private final Map<Player, Language> locales = new HashMap<>();
    private final Set<UUID> bedrockPlayers = new HashSet<>();
    private final Set<Player> unauthorized = new HashSet<>();
    private final Set<Player> weatherUnavailable = new HashSet<>();
    private final CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
    private MockedStatic<Bukkit> bukkit;
    private LanguageService translations;
    private AiModule module;
    private Logger logger;
    private Runnable poll;
    private long nowNanos;
    private int weatherReads;

    @BeforeEach
    void setUp() {
        var plugin = mock(JavaPlugin.class);
        logger = mock(Logger.class);
        when(plugin.getLogger()).thenReturn(logger);
        var server = mock(Server.class);
        var scheduler = mock(BukkitScheduler.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        bukkit.when(() -> Bukkit.getPlayer(any(UUID.class))).thenAnswer(call -> online.get(call.getArgument(0)));
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), anyLong(), anyLong())).thenAnswer(call -> {
            poll = call.getArgument(1);
            return mock(BukkitTask.class);
        });
        when(scheduler.callSyncMethod(eq(plugin), any())).thenAnswer(call -> {
            Callable<?> action = call.getArgument(1);
            return CompletableFuture.completedFuture(action.call());
        });
        translations = FluentTestSupport.languages();
        var languages = new LanguageService(plugin) {
            @Override
            public Language language(Player player) {
                return locales.get(player);
            }

            @Override
            public String t(Language language, Message message, Object... arguments) {
                return translations.t(language, message, arguments);
            }
        };
        var port = mock(RequestPort.class);
        when(port.assistant(any(AssistantCommand.class))).thenAnswer(call -> {
            var exchange = new Exchange(call.getArgument(0), new CompletableFuture<>());
            requests.addLast(exchange);
            return exchange.reply();
        });
        var tools = new GameToolCatalog(List.of(GameTool.read(WEATHER, "Read current weather",
                player -> !weatherUnavailable.contains(player), _ -> {
                    weatherReads++;
                    return new JsonPrimitive("snow");
                })));
        module = new AiModule(plugin, languages, port, bedrockPlayers::contains,
                player -> !unauthorized.contains(player), tools, () -> nowNanos);
        module.enable();
        dispatcher.getRoot().addChild(module.buildCommand());
    }

    @AfterEach
    void tearDown() {
        try {
            module.disable();
        } finally {
            bukkit.close();
        }
    }

    @Test
    void the_catalog_is_per_turn_and_advertising_does_not_read_game_state() throws Exception {
        var viewer = connect();
        var other = connect();
        weatherUnavailable.add(other);
        execute(viewer, "ai Is it snowing?");
        var advertised = take(OperationCase.ASK, viewer).command();
        assertEquals(1, advertised.getAsk().getGameToolsCount());
        assertEquals(WEATHER, advertised.getAsk().getGameTools(0).getName());
        execute(other, "ai How is the weather?");
        var withheld = take(OperationCase.ASK, other).command();
        assertEquals(0, withheld.getAsk().getGameToolsCount());
        assertEquals(0, weatherReads);

        weatherUnavailable.remove(other);
        assertFalse(readsGameTool(other, withheld.getTurnId()));
        assertFalse(readsGameTool(other, advertised.getTurnId()));
        assertEquals(0, weatherReads);

        var invalid = module.executeTool(toolCall(viewer, advertised.getTurnId())
                .setArgumentsJson("{\"player_uuid\":\"" + other.getUniqueId() + "\"}").build(), 1000);
        assertEquals(AssistantToolFailure.Reason.INVALID_ARGUMENTS, invalid.getFailure().getReason());
        assertEquals(0, weatherReads);
        assertEquals("\"snow\"", readGameTool(viewer, advertised.getTurnId()).getObservation().getDataJson());
        assertEquals(1, weatherReads);

        weatherUnavailable.add(viewer);
        var revoked = readGameTool(viewer, advertised.getTurnId());
        assertEquals(AssistantToolFailure.Reason.ACCESS_UNAVAILABLE, revoked.getFailure().getReason());
        assertFalse(revoked.hasObservation());
        assertEquals(1, weatherReads);
    }

    @Test
    void pendingPollsShowOnePrivateThinkingNoticeAndReplyTextStaysInert() throws Exception {
        var player = connect(UUID.randomUUID(), Language.DE_DE);
        var other = connect();
        String prompt = "save a list of building materials";
        execute(player, "ai " + prompt);
        var ask = take(OperationCase.ASK, player);
        assertEquals(prompt, ask.command().getAsk().getPrompt());
        verify(player, never()).showBossBar(any(BossBar.class));
        assertTrue(inbox.get(player).isEmpty());
        var firstPoll = accepted(ask);
        assertEquals(List.of(thinkingMessage(player)), messages(player));
        var thinking = ArgumentCaptor.forClass(BossBar.class);
        verify(player).showBossBar(thinking.capture());
        var bar = thinking.getValue();
        assertEquals(inbox.get(player).getFirst(), bar.name());
        assertEquals(BossBar.MIN_PROGRESS, bar.progress());
        String summary = "Ich prüfe die verfügbaren Baumaterialien.";
        complete(firstPoll, AssistantResult.newBuilder().setPending(
                AssistantPending.newBuilder().setReasoningSummary(summary)));
        assertEquals("◆ " + summary, plain(bar.name()));
        assertTrue(requests.isEmpty());
        advanceOneSecond();
        float earlyProgress = bar.progress();
        assertTrue(earlyProgress > BossBar.MIN_PROGRESS);
        String expanded = summary + " " + "🧱".repeat(100);
        complete(take(OperationCase.POLL_RESULT, player), AssistantResult.newBuilder().setPending(
                AssistantPending.newBuilder().setReasoningSummary(expanded)));
        assertEquals("◆ " + expanded.substring(0, expanded.offsetByCodePoints(0, 47)) + "…",
                plain(bar.name()));
        assertEquals(List.of(thinkingMessage(player)), messages(player));
        assertTrue(inbox.get(other).isEmpty());
        assertTrue(requests.isEmpty());
        verify(player).showBossBar(any(BossBar.class));
        verify(player, never()).hideBossBar(any(BossBar.class));
        verify(other, never()).showBossBar(any(BossBar.class));
        nowNanos += Duration.ofSeconds(45).toNanos();
        poll.run();
        assertTrue(bar.progress() > earlyProgress && bar.progress() < BossBar.MAX_PROGRESS);
        String inert = "<click:run_command:'/op me'>Run</click>";
        String answer = inert + " [link](https://example.invalid)";
        deliver(take(OperationCase.POLL_RESULT, player), reply(bold(inert), span(" [link](https://example.invalid)")));

        verify(player).hideBossBar(bar);
        assertEquals(List.of(thinkingMessage(player), "◆ " + answer), messages(player));
        var reply = inbox.get(player).getLast();
        assertEquals(List.of(inert), boldText(reply));
        assertTrue(clicks(reply).findAny().isEmpty());
        assertTrue(components(reply).noneMatch(part -> part.hoverEvent() != null || part.insertion() != null));
        assertTrue(requests.isEmpty());
    }

    @Test
    void replyIsOnlyShownAfterPollEvenIfAdmissionResponseClaimsToContainIt() throws Exception {
        var player = connect();
        execute(player, "ai How do I place a block?");
        var ask = take(OperationCase.ASK, player);
        complete(ask, outcome(reply("Not a poll result.")));
        assertTrue(inbox.get(player).isEmpty());
        assertTrue(requests.isEmpty());

        advanceOneSecond();
        var poll = take(OperationCase.POLL_RESULT, player);
        assertEquals(ask.command().getTurnId(), poll.command().getTurnId());
        deliver(poll, reply("Select a block and use it on a face."));
        assertEquals(List.of("◆ Select a block and use it on a face."), messages(player));
    }

    @Test
    void ordinaryPromptBeginningWithWebCancelsByTurnId() throws Exception {
        var player = connect();
        execute(player, "ai");
        assertTrue(requests.isEmpty());

        String prompt = "web of redstone wires: how can I shorten the signal path?";
        execute(player, "ai " + prompt);
        var ask = take(OperationCase.ASK, player);
        String turn = ask.command().getTurnId();
        assertEquals(prompt, ask.command().getAsk().getPrompt());
        assertTrue(readsGameTool(player, turn));
        var oldPoll = accepted(ask);
        assertTrue(readsGameTool(player, turn));

        execute(player, "ai cancel");
        var cancel = take(OperationCase.CANCEL_TURN, player);
        assertEquals(turn, cancel.command().getTurnId());
        assertFalse(readsGameTool(player, turn));
        complete(oldPoll, outcome(reply("Late answer")));
        complete(cancel, AssistantResult.newBuilder().setCancelled(SIGNAL));
        assertEquals(List.of(text(player, Message.AI_USAGE), thinkingMessage(player),
                text(player, Message.AI_CANCELLED)), messages(player));
        assertTrue(requests.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"clear", "save", "resume", "forget", "web", "cancel my plot visit"})
    void onlyBareCancelIsReservedInPromptInput(String prompt) throws Exception {
        var player = connect();
        execute(player, "ai " + prompt);
        assertEquals(prompt, take(OperationCase.ASK, player).command().getAsk().getPrompt());
        assertTrue(requests.isEmpty());
    }

    @Test
    void plainRefusalIsAReplyVisibleOnlyToTheAskingPlayer() throws Exception {
        var player = connect(UUID.randomUUID(), Language.ZH_CN);
        var other = connect();
        execute(player, "ai How do I run integration tests?");
        var ask = take(OperationCase.ASK, player);
        String refusal = "我只回答关于 Minecraft 玩法和本服使用的问题。";
        deliver(ask, reply(refusal));

        assertEquals(List.of(thinkingMessage(player), "◆ " + refusal), messages(player));
        assertTrue(clicks(inbox.get(player).getLast()).findAny().isEmpty());
        assertTrue(inbox.get(other).isEmpty());
        assertFalse(readsGameTool(player, ask.command().getTurnId()));
        assertTrue(requests.isEmpty());
    }

    @Test
    void aListAloneMakesEachInlineReferenceClickableAndConfirmsDelivery() throws Exception {
        var player = connect(UUID.randomUUID(), Language.ZH_CN);
        var other = connect();
        execute(player, "ai 附近有哪些建筑？");
        var list = AssistantList.newBuilder().setFirstNumber(7);
        String guide = "https://mcmik.top/zh-CN/wiki?section=visitor-guide";
        var buildingUrl = "https://mcmik.top/buildings/" + BUILDING_ID;
        list.addChildren(AssistantListItem.newBuilder().addBlocks(AssistantBlock.newBuilder().setParagraph(
                AssistantInline.newBuilder().addParts(part("Town hall").setUrl(buildingUrl))
                .addParts(part(" · 28 格 · "))
                .addParts(part("参观须知").setUrl(guide))
                .addParts(part(" · "))
                .addParts(part("了解路线").setAskLabel(AssistantAskLabel.getDefaultInstance())))));
        list.addChildren(AssistantListItem.newBuilder().addBlocks(paragraph(part("  "))));
        deliver(take(OperationCase.ASK, player), AssistantReply.newBuilder()
                .addBlocks(AssistantBlock.newBuilder().setList(list)));

        var reply = inbox.get(player).getLast();
        assertEquals("◆\n7. Town hall · 28 格 · 参观须知 · 了解路线", plain(reply));
        assertEquals(List.of(ClickEvent.openUrl(buildingUrl), ClickEvent.openUrl(guide)),
                clicks(reply).filter(event -> event.action() != ClickEvent.Action.CUSTOM).toList());
        assertTrue(inbox.get(other).isEmpty());
        assertTrue(requests.isEmpty());
    }

    @Test
    void boldAnswerPreservesStepsAndEnglishAndChineseWikiCitationsRemainClickable() throws Exception {
        var player = connect(UUID.randomUUID(), Language.ZH_CN);
        String english = "https://minecraft.wiki/w/Redstone_Comparator";
        String nested = "https://minecraft.wiki/w/Tutorials/Redstone_computers#Memory";
        String chinese = "https://zh.minecraft.wiki/w/%E7%BA%A2%E7%9F%B3%E6%AF%94%E8%BE%83%E5%99%A8#%E7%94%A8%E9%80%94";
        String answer = "🔧用比较器。\n\n1. 放比较器。\n2. 接红石。";
        execute(player, "ai 怎么用比较器？");
        deliver(take(OperationCase.ASK, player), reply(span("🔧用"), bold("比较器"),
                span("。\n\n1. 放比较器。\n2. 接"), bold("红石"), span("。"))
                .addBlocks(link("Redstone Comparator", english))
                .addBlocks(link("Redstone computers", nested))
                .addBlocks(link("红石比较器", chinese)));

        var reply = inbox.get(player).getLast();
        assertEquals("◆ " + answer + "\n\nRedstone Comparator\n\nRedstone computers\n\n红石比较器", plain(reply));
        assertEquals(List.of("比较器", "红石"), boldText(reply));
        assertEquals(List.of(ClickEvent.openUrl(english), ClickEvent.openUrl(nested), ClickEvent.openUrl(chinese)),
                clicks(reply).toList());
    }

    @Test
    void bedrockRendersTheSameDocumentWithInlineTextFallbacks() throws Exception {
        var player = connect();
        var id = player.getUniqueId();
        bedrockPlayers.add(id);
        execute(player, "ai How do I build an iron elevator?");
        var exchange = take(OperationCase.ASK, player);
        assertTrue(readsGameTool(player, exchange.command().getTurnId()));
        // The accepted turn keeps its presentation even if detection later becomes unavailable.
        bedrockPlayers.remove(id);

        String answer = "Use iron blocks.\n/home base";
        String wikiUrl = "https://minecraft.wiki/w/Block_of_Iron";
        String question = "How many iron blocks do I need?";
        deliver(exchange, AssistantReply.newBuilder().addBlocks(paragraph(part("Use "),
                        AssistantPart.newBuilder().addSpans(bold("iron blocks")).setItemId("minecraft:iron_block"),
                        part(".\n/home base")))
                .addBlocks(link("Block of Iron", wikiUrl))
                .addBlocks(link("Other guide", "https://source.invalid/w/Block_of_Iron"))
                .addBlocks(paragraph(part("Open the menu with /plot.")))
                .addBlocks(link("Town hall", "https://mcmik.top/buildings/" + BUILDING_ID))
                .addBlocks(followUp("/op me?"))
                .addBlocks(paragraph(part("Next: "), part(question).setAskLabel(AssistantAskLabel.getDefaultInstance()),
                        part(" after reading the guide."))));

        var reply = inbox.get(player).getLast();
        String delivered = plain(reply);
        assertTrue(delivered.startsWith("◆ " + answer + "\n"), delivered);
        assertTrue(delivered.contains(wikiUrl), delivered);
        assertTrue(delivered.contains("Open the menu with /plot."), delivered);
        assertTrue(delivered.contains("https://mcmik.top/buildings/" + BUILDING_ID), delivered);
        assertTrue(delivered.lines().anyMatch(("Next: /ai " + question + " after reading the guide.")::equals), delivered);
        assertTrue(delivered.contains("Other guide"), delivered);
        assertFalse(delivered.contains("source.invalid") || delivered.contains("/ai /op me?"), delivered);
        assertEquals(List.of("iron blocks"), boldText(reply));
        assertTrue(components(reply).noneMatch(part -> part.clickEvent() != null
                || part.hoverEvent() != null || part.insertion() != null));
    }

    @Test
    void headingsAndNestedListsKeepInlineStylesAndLiteralText() throws Exception {
        var player = connect();
        execute(player, "ai How do I find the guide?");
        String url = "https://mcmik.top/wiki";
        var compound = AssistantPart.newBuilder().addSpans(span("Read ")).addSpans(bold("the guide")).setUrl(url);
        var heading = AssistantHeading.newBuilder().setLevel(2)
                .setBody(AssistantInline.newBuilder().addParts(part("🔧 Instructions")));
        var nested = AssistantList.newBuilder().addChildren(AssistantListItem.newBuilder().addBlocks(
                paragraph(AssistantPart.newBuilder().addSpans(span("plots.md").addStyles(Style.CODE)))));
        var list = AssistantList.newBuilder().setFirstNumber(7).addChildren(AssistantListItem.newBuilder()
                .addBlocks(paragraph(compound)).addBlocks(AssistantBlock.newBuilder().setList(nested)));
        deliver(take(OperationCase.ASK, player), AssistantReply.newBuilder()
                .addBlocks(AssistantBlock.newBuilder().setHeading(heading))
                .addBlocks(AssistantBlock.newBuilder().setList(list)));

        var reply = inbox.get(player).getLast();
        assertEquals("◆ 🔧 Instructions\n\n7. Read the guide\n\n   • plots.md", plain(reply));
        assertEquals(List.of("🔧 Instructions", "the guide"), boldText(reply));
        assertEquals(List.of(ClickEvent.openUrl(url)), clicks(reply).toList());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void multilineQuotesAndLiteralBlocksRetainTheirStructure(boolean basicChat) throws Exception {
        var player = connect();
        if (basicChat) bedrockPlayers.add(player.getUniqueId());
        execute(player, "ai How should I write these steps?");
        var quote = AssistantBlocks.newBuilder()
                .addBlocks(paragraph(part("Read the first line.\nThen read the next.")))
                .addBlocks(AssistantBlock.newBuilder().setCode(
                        AssistantCodeBlock.newBuilder().setText("<tag> **literal**\n  second").setLanguage("sh")));
        deliver(take(OperationCase.ASK, player), AssistantReply.newBuilder()
                .addBlocks(AssistantBlock.newBuilder().setBlockQuote(quote)));
        assertEquals("◆\n│ Read the first line.\n│ Then read the next.\n│\n│ <tag> **literal**\n│   second",
                plain(inbox.get(player).getLast()));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void rendersRichTableChecklistAndCodeWithoutASpecialReplyMode(boolean basicChat) throws Exception {
        var player = connect();
        if (basicChat) bedrockPlayers.add(player.getUniqueId());
        execute(player, "ai Compare those layouts");
        String url = "https://mcmik.top/wiki";
        var cell = AssistantInline.newBuilder().addParts(part("Guide").setUrl(url));
        var table = AssistantTable.newBuilder().setHeader(AssistantRow.newBuilder()
                        .addCells(AssistantInline.newBuilder().addParts(part("Material"))).addCells(
                                AssistantInline.newBuilder().addParts(part("Guide"))))
                .addRows(AssistantRow.newBuilder().addCells(AssistantInline.newBuilder().addParts(
                        AssistantPart.newBuilder().addSpans(span("Glass").addStyles(Style.STRIKETHROUGH))))
                        .addCells(cell));
        var list = AssistantList.newBuilder().addChildren(AssistantListItem.newBuilder()
                .setChecked(false).addBlocks(paragraph(part("Pick a design"))));
        var code = AssistantCodeBlock.newBuilder().setText("first\nsecond").setLanguage("text");
        deliver(take(OperationCase.ASK, player), AssistantReply.newBuilder()
                .addBlocks(AssistantBlock.newBuilder().setTable(table))
                .addBlocks(AssistantBlock.newBuilder().setList(list))
                .addBlocks(AssistantBlock.newBuilder().setCode(code)));
        var reply = inbox.get(player).getLast();
        assertEquals("◆\nMaterial │ Guide\nGlass │ Guide"
                + (basicChat ? " · " + url : "") + "\n\n• ☐ Pick a design\n\nfirst\nsecond", plain(reply));
        assertTrue(components(reply).anyMatch(component -> component.decoration(TextDecoration.STRIKETHROUGH)
                == TextDecoration.State.TRUE));
        assertEquals(!basicChat, components(reply).anyMatch(component -> ClickEvent.openUrl(url).equals(component.clickEvent())));
        assertEquals(!basicChat, components(reply).anyMatch(component -> HoverEvent.showText(Component.text("text"))
                .equals(component.hoverEvent())));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "http://minecraft.wiki/w/Redstone",
            "https:minecraft.wiki/w/Redstone",
            "https://minecraft.wiki.source.invalid/w/Redstone",
            "https://minecraft.wiki@source.invalid/w/Redstone",
            "https://source.invalid@zh.minecraft.wiki/w/Redstone",
            "https://minecraft%2Ewiki/w/Redstone",
            "https://zh.minecraft.wiki./w/Redstone",
            "https://minecraft.wiki:443/w/Redstone",
            "https://zh.minecraft.wiki:8443/w/Redstone",
            "https://127.0.0.1/w/Redstone",
            "https://minecraft.wiki/w/Redstone%XY",
            "https://minecraft.wiki/w/Redstone\n",
            "https://minecraft.wiki/w/Redstone\\Uses"
    })
    void unsafeUrlsKeepTheirLabelsWithoutClickEvents(String url) throws Exception {
        var player = connect();
        execute(player, "ai How does redstone work?");
        deliver(take(OperationCase.ASK, player), AssistantReply.newBuilder()
                .addBlocks(link("Redstone", url)));

        var reply = inbox.get(player).getLast();
        assertEquals("◆ Redstone", plain(reply));
        assertTrue(clicks(reply).findAny().isEmpty());
    }

    @Test
    void reconnectRecoversTheSameTurnAndTheFollowUpCarriesItsUnconfirmedReceipt() throws Exception {
        var player = connect();
        execute(player, "ai How do I use an iron elevator?");
        var initial = take(OperationCase.ASK, player);
        String id = initial.command().getTurnId();
        loseResponse(initial);
        var earlyPoll = take(OperationCase.POLL_RESULT, player);
        assertEquals(id, earlyPoll.command().getTurnId(), "Uncertain admission is never replayed");
        complete(earlyPoll, failure(Reason.TURN_UNAVAILABLE));
        assertTrue(inbox.get(player).isEmpty());
        advanceOneSecond();
        complete(take(OperationCase.POLL_RESULT, player),
                AssistantResult.newBuilder().setPending(AssistantPending.getDefaultInstance()));
        assertEquals(List.of(thinkingMessage(player)), messages(player));
        advanceOneSecond();
        loseResponse(take(OperationCase.POLL_RESULT, player));
        String followUp = "How many iron blocks do I need?";
        var reply = take(OperationCase.POLL_RESULT, player);
        assertEquals(id, reply.command().getTurnId());
        complete(reply, outcome(reply("Stand on the iron platform.")
                .addBlocks(followUp(followUp))));
        var receipt = take(OperationCase.CONFIRM_DELIVERY, player);
        assertEquals(id, receipt.command().getTurnId());
        loseResponse(receipt);
        var retried = take(OperationCase.CONFIRM_DELIVERY, player);
        assertEquals(id, retried.command().getTurnId());
        click(player, suggestionButtons(inbox.get(player).getLast()).getFirst().clickEvent());
        var next = take(OperationCase.ASK, player);
        assertEquals(id, next.command().getAsk().getPreviousDisplayedTurn());
        assertEquals(followUp, next.command().getAsk().getPrompt());
        assertNotEquals(id, next.command().getTurnId());
        complete(retried, AssistantResult.newBuilder().setDeliveryConfirmed(SIGNAL));
        assertEquals(1, messages(player).stream().filter(thinkingMessage(player)::equals).count());
        assertEquals(1, messages(player).stream().filter(message -> message.contains("Stand on the iron platform.")).count());
        assertTrue(requests.isEmpty());
        verifyNoInteractions(logger);
    }

    @Test
    void missingReceiptDoesNotInvalidateTheDisplayedFollowUp() throws Exception {
        var player = connect();
        execute(player, "ai How do I use an iron elevator?");
        String question = "How many blocks do I need?";
        complete(accepted(take(OperationCase.ASK, player)), outcome(reply("Stand on the iron platform.")
                .addBlocks(followUp(question))));
        var receipt = take(OperationCase.CONFIRM_DELIVERY, player);
        var shown = List.copyOf(inbox.get(player));
        complete(receipt, failure(Reason.TURN_UNAVAILABLE));

        assertEquals(shown, inbox.get(player));
        click(player, suggestionButtons(shown.getLast()).getFirst().clickEvent());
        var ask = take(OperationCase.ASK, player).command().getAsk();
        assertEquals(question, ask.getPrompt());
        assertFalse(ask.hasPreviousDisplayedTurn());
        verify(logger).warning("AI turn=" + receipt.command().getTurnId() + " operation=CONFIRM"
                + " reason=TURN_UNAVAILABLE elapsed_ms=0 ipc_failures=0");
    }

    @Test
    void missingTurnAfterReconnectLogsOnlyCorrelationAndFailureMetadata() throws Exception {
        var player = connect();
        execute(player, "ai Where is my private build?");
        var ask = take(OperationCase.ASK, player);
        var turnId = ask.command().getTurnId();
        loseResponse(accepted(ask));
        verifyNoInteractions(logger);

        complete(take(OperationCase.POLL_RESULT, player), failure(Reason.TURN_UNAVAILABLE));
        verify(logger).warning("AI turn=" + turnId + " operation=POLL reason=TURN_UNAVAILABLE"
                + " elapsed_ms=1000 ipc_failures=1");
        assertEquals(List.of(thinkingMessage(player), text(player, Message.AI_REQUEST_FAILED)), messages(player));
        assertEquals(turnId, take(OperationCase.CANCEL_TURN, player).command().getTurnId());
        assertTrue(requests.isEmpty());
    }

    @Test
    void replacementSessionCannotUseOldButtonsWhileConfirmationContinues() throws Exception {
        var id = UUID.randomUUID();
        var player = connect(id, Language.EN_US);
        execute(player, "ai How do I use an iron elevator?");
        complete(accepted(take(OperationCase.ASK, player)), outcome(reply("Stand on the iron platform.")
                .addBlocks(followUp("How many blocks do I need?"))));
        var receipt = take(OperationCase.CONFIRM_DELIVERY, player);
        var button = suggestionButtons(inbox.get(player).getLast()).getFirst().clickEvent();
        quit(player);
        var replacement = connect(id, Language.EN_US);
        click(replacement, button);
        loseResponse(receipt);
        complete(take(OperationCase.CONFIRM_DELIVERY, player), AssistantResult.newBuilder().setDeliveryConfirmed(SIGNAL));
        assertTrue(requests.isEmpty());
        assertTrue(inbox.get(replacement).isEmpty());
        execute(replacement, "ai Where is an iron elevator?");
        take(OperationCase.ASK, replacement);
    }

    @Test
    void nativeFollowUpSubmitsOnlyTheSelectedVisibleQuestionOnceForItsOwner() throws Exception {
        var player = connect(UUID.randomUUID(), Language.ZH_CN);
        var other = connect();
        String first = "铁块电梯两层要隔多远？";
        String selected = "电梯需要多少块铁块？";
        String wiki = "https://minecraft.wiki/w/Block_of_Iron";
        var reply = replyToAsk(player, reply("电梯用铁块。")
                .addBlocks(link("Block of Iron", wiki))
                .addBlocks(paragraph(part("Use /plot to view your plots.")))
                .addBlocks(followUp(first)).addBlocks(followUp(selected)));
        var buttons = suggestionButtons(reply);
        assertEquals(List.of(first, selected), buttons.stream().map(AiModuleTest::plain).toList());
        for (var button : buttons) {
            assertEquals(NamedTextColor.AQUA, button.color());
            assertEquals(TextDecoration.State.TRUE, button.decoration(TextDecoration.UNDERLINED));
            assertNull(button.insertion());
            assertNull(button.hoverEvent());
        }
        assertEquals(List.of(ClickEvent.openUrl(wiki)),
                clicks(reply).filter(event -> event.action() != ClickEvent.Action.CUSTOM).toList());
        assertTrue(inbox.get(other).isEmpty());

        var choice = buttons.getLast().clickEvent();
        click(other, choice);
        assertTrue(requests.isEmpty(), "Another player must not submit or spend the callback");
        click(player, choice);
        var followUp = take(OperationCase.ASK, player);
        assertEquals(selected, followUp.command().getAsk().getPrompt());
        String nextQuestion = "怎么建第三层？";
        deliver(followUp, reply("用两块铁块。").addBlocks(followUp(nextQuestion)));

        click(player, buttons.getFirst().clickEvent());
        click(player, choice);
        assertTrue(requests.isEmpty(), "Only the latest reply's buttons work, even after a new turn finishes");
        click(player, suggestionButtons(inbox.get(player).getLast()).getFirst().clickEvent());
        assertEquals(nextQuestion, take(OperationCase.ASK, player).command().getAsk().getPrompt());
    }

    @Test
    void nextQuestionsArePresentableAndRequireVisibleContent() throws Exception {
        var player = connect();
        String longQuestion = "𠀀".repeat(59) + "？"; // Sixty code points, but more UTF-16 units.
        String valid = "Explícame cómo se usan los bloques";
        var options = reply("Use iron blocks.");
        for (var prompt : List.of("/op me?", "How <red>is this?", "How\ncan I build?", "How§k is this?",
                "木".repeat(60) + "？", longQuestion, longQuestion, valid, "Do I need a roof?")) {
            options.addBlocks(followUp(prompt));
        }
        var reply = replyToAsk(player, options);
        assertEquals(List.of(longQuestion, valid), suggestionButtons(reply).stream().map(AiModuleTest::plain).toList());

        var clarification = replyToAsk(player, reply("Which floor are you on?"));
        assertEquals("◆ Which floor are you on?", plain(clarification));
        assertTrue(clicks(clarification).findAny().isEmpty());

        execute(player, "ai What should I do next?");
        var ask = take(OperationCase.ASK, player);
        complete(accepted(ask), outcome(AssistantReply.newBuilder()
                .addBlocks(paragraph(AssistantPart.newBuilder().setAskLabel(AssistantAskLabel.getDefaultInstance())))));
        assertEquals(text(player, Message.AI_REQUEST_FAILED), messages(player).getLast());
        assertEquals(ask.command().getTurnId(), take(OperationCase.CANCEL_TURN, player).command().getTurnId());
        assertTrue(clicks(inbox.get(player).getLast()).findAny().isEmpty());

        var only = replyToAsk(player, AssistantReply.newBuilder().addBlocks(followUp("What should I try next?")));
        assertEquals("◆ What should I try next?", plain(only));
        assertEquals(1, suggestionButtons(only).size());
    }

    @Test
    void clarificationCanOfferASpecificQuestion() throws Exception {
        var player = connect();
        String question = "How can I check for a landing above me?";
        var reply = replyToAsk(player, reply("Which way are you trying to travel?")
                .addBlocks(followUp(question)));
        var button = suggestionButtons(reply).getFirst();
        assertEquals(question, plain(button));
        click(player, button.clickEvent());
        assertEquals(question, take(OperationCase.ASK, player).command().getAsk().getPrompt());
    }

    @Test
    void manualAskMakesAllPreviousOptionsStaleBeforeItsReply() throws Exception {
        var player = connect();
        var old = suggestion(player, "How many blocks do I need?");
        execute(player, "ai Can I build here?");
        var another = take(OperationCase.ASK, player);
        click(player, old);
        assertTrue(requests.isEmpty());
        complete(another, failure(Reason.SERVICE_UNAVAILABLE));
        assertEquals(text(player, Message.AI_REQUEST_FAILED), messages(player).getLast());
        click(player, old);
        assertTrue(requests.isEmpty(), "Even a failed newer turn must not restore old options");
    }

    @Test
    void followUpRechecksTheThreadConnectionAndAuthorizationWithoutSpendingTheOption() throws Exception {
        var player = connect();
        String question = "Where can I get iron?";
        var button = suggestion(player, question);
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
        click(player, button);
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);

        var id = player.getUniqueId();
        var replacement = connect(id, Language.EN_US);
        click(player, button);
        click(replacement, button);
        online.put(id, player);
        when(player.hasPermission("mik.ai.use")).thenReturn(false);
        click(player, button);
        when(player.hasPermission("mik.ai.use")).thenReturn(true);
        unauthorized.add(player);
        click(player, button);
        unauthorized.remove(player);
        assertTrue(requests.isEmpty());

        click(player, button);
        assertEquals(question, take(OperationCase.ASK, player).command().getAsk().getPrompt());
    }

    @Test
    void quitAndDisableInvalidateUnchosenOptions() throws Exception {
        var player = connect();
        var button = suggestion(player, "Where can I get iron?");
        quit(player);
        var rejoined = connect(player.getUniqueId(), Language.EN_US);
        click(rejoined, button);
        click(player, button);
        assertTrue(requests.isEmpty());

        var fresh = suggestion(rejoined, "How do I place the iron blocks?");
        module.disable();
        click(rejoined, fresh);
        assertTrue(requests.isEmpty());
    }

    @Test
    void suggestionExpiresAfterTwoMinutesEvenBeforeTheNextServerPoll() throws Exception {
        var player = connect();
        var expires = suggestion(player, "How far apart are the floors?");
        nowNanos += Duration.ofMinutes(2).toNanos();
        click(player, expires);
        assertTrue(requests.isEmpty());

        String question = "How do I make another floor?";
        var fresh = suggestion(player, question);
        nowNanos += Duration.ofSeconds(119).toNanos();
        click(player, fresh);
        assertEquals(question, take(OperationCase.ASK, player).command().getAsk().getPrompt());
    }

    @Test
    void cancellationAndLateRepliesStayScopedToTheirPlayers() throws Exception {
        var player = connect();
        var other = connect();
        execute(other, "ai What's in my inventory?");
        var otherAsk = take(OperationCase.ASK, other);
        execute(player, "ai cancel");
        assertTrue(requests.isEmpty(), "An idle cancel must not send a scope-wide command");

        execute(player, "ai I want to build a lift");
        var stoppedAsk = take(OperationCase.ASK, player);
        String stoppedTurn = stoppedAsk.command().getTurnId();
        assertTrue(readsGameTool(player, stoppedTurn));
        assertFalse(readsGameTool(other, stoppedTurn));

        execute(player, "ai cancel");
        var cancel = take(OperationCase.CANCEL_TURN, player);
        assertEquals(stoppedTurn, cancel.command().getTurnId());
        assertFalse(readsGameTool(player, stoppedTurn));
        assertTrue(readsGameTool(other, otherAsk.command().getTurnId()));
        complete(cancel, AssistantResult.newBuilder().setCancelled(SIGNAL));

        execute(player, "ai A new question");
        var newAsk = take(OperationCase.ASK, player);
        complete(stoppedAsk, AssistantResult.newBuilder().setAccepted(SIGNAL));
        assertTrue(readsGameTool(player, newAsk.command().getTurnId()));
        assertEquals(List.of(text(player, Message.AI_NOTHING_TO_CANCEL), text(player, Message.AI_CANCELLED)), messages(player));
        assertTrue(inbox.get(other).isEmpty());

        deliver(otherAsk, reply("Your inventory is empty."));
        assertEquals(List.of(thinkingMessage(other), "◆ Your inventory is empty."), messages(other));
        assertTrue(requests.isEmpty());
    }

    @Test
    void cancellationSuppressesTheOldPollAndCannotBeOvertakenAfterRejoin() throws Exception {
        var id = UUID.randomUUID();
        var leaving = connect(id, Language.EN_US);
        execute(leaving, "ai Help me find a build");
        var oldPoll = accepted(take(OperationCase.ASK, leaving));
        var thinking = ArgumentCaptor.forClass(BossBar.class);
        verify(leaving).showBossBar(thinking.capture());

        execute(leaving, "ai cancel");
        verify(leaving).hideBossBar(thinking.getValue());
        var cancel = take(OperationCase.CANCEL_TURN, leaving);
        complete(oldPoll, outcome(reply("Do not show this late answer")));
        quit(leaving);
        var joined = connect(id, Language.EN_US);

        assertEquals(0, execute(joined, "ai A new task"));
        assertTrue(requests.isEmpty());
        complete(cancel, AssistantResult.newBuilder().setCancelled(SIGNAL));
        assertEquals(List.of(thinkingMessage(leaving)), messages(leaving));
        assertEquals(List.of(text(joined, Message.AI_REQUEST_IN_PROGRESS)), messages(joined));
        execute(joined, "ai A new task");
        take(OperationCase.ASK, joined);
    }

    @Test
    void quitDiscardsTheExactTurnEvenBeforeTheAskHasAResponse() throws Exception {
        var id = UUID.randomUUID();
        var leaving = connect(id, Language.EN_US);
        execute(leaving, "ai Check my plot");
        var oldAsk = take(OperationCase.ASK, leaving);
        quit(leaving);
        assertEquals(oldAsk.command().getTurnId(), take(OperationCase.CANCEL_TURN, leaving).command().getTurnId());
        var joined = connect(id, Language.EN_US);
        execute(joined, "ai Check my new location");
        var newAsk = take(OperationCase.ASK, joined);

        complete(oldAsk, AssistantResult.newBuilder().setAccepted(SIGNAL));
        assertFalse(readsGameTool(joined, oldAsk.command().getTurnId()));
        assertTrue(readsGameTool(joined, newAsk.command().getTurnId()));
        assertTrue(inbox.get(leaving).isEmpty());
        assertTrue(inbox.get(joined).isEmpty());
        assertTrue(requests.isEmpty());
    }

    @Test
    void confirmationIsQueuedAfterSendingAndFailedSendsAreDiscarded() throws Exception {
        var player = connect();
        execute(player, "ai How does an elevator work?");
        var ask = take(OperationCase.ASK, player);
        doAnswer(call -> {
            assertTrue(requests.isEmpty(), "Do not confirm before calling sendMessage");
            inbox.get(player).add(call.getArgument(0));
            return null;
        }).when(player).sendMessage(any(Component.class));
        deliver(ask, reply("Stand on an iron block."));

        execute(player, "ai How do I go up?");
        var second = accepted(take(OperationCase.ASK, player));
        doThrow(new IllegalStateException("send failed")).when(player).sendMessage(any(Component.class));
        assertThrows(IllegalStateException.class,
                () -> complete(second, outcome(reply("Jump."))));
        assertEquals(second.command().getTurnId(), take(OperationCase.CANCEL_TURN, player).command().getTurnId());
        assertTrue(requests.isEmpty());
    }

    @Test
    void lateReplyIsDiscardedEvenWhenItsPollDoesComplete() throws Exception {
        var player = connect();
        execute(player, "ai How does an elevator work?");
        var ask = take(OperationCase.ASK, player);
        var lastPoll = accepted(ask);
        nowNanos += Duration.ofSeconds(100).toNanos();
        complete(lastPoll, outcome(reply("Too late.")));
        assertEquals(ask.command().getTurnId(), take(OperationCase.CANCEL_TURN, player).command().getTurnId());
        assertEquals(List.of(thinkingMessage(player), text(player, Message.AI_REQUEST_FAILED)), messages(player));
        assertTrue(requests.isEmpty());
    }

    @Test
    void defaultAiPermissionIsIndependentAndAuthorizationLossHidesPrivateReplies() throws Exception {
        var manifest = new YamlConfiguration();
        manifest.options().pathSeparator('/');
        try (var reader = new InputStreamReader(Objects.requireNonNull(
                AiModuleTest.class.getResourceAsStream("/paper-plugin.yml")), StandardCharsets.UTF_8)) {
            manifest.load(reader);
        }
        assertTrue(manifest.getBoolean("permissions/mik.ai.use/default"));

        var player = connect();
        execute(player, "ai How many iron blocks do I have?");
        var ask = take(OperationCase.ASK, player);
        String turn = ask.command().getTurnId();
        assertTrue(readsGameTool(player, turn));
        var pending = accepted(ask);

        when(player.hasPermission("group.custodian")).thenReturn(true);
        when(player.hasPermission("mik.ai.use")).thenReturn(false);
        assertThrows(CommandSyntaxException.class, () -> execute(player, "ai cancel"));
        assertFalse(readsGameTool(player, turn));

        when(player.hasPermission("mik.ai.use")).thenReturn(true);
        unauthorized.add(player);
        assertFalse(readsGameTool(player, turn));
        complete(pending, outcome(reply("Private game state")));
        assertEquals(turn, take(OperationCase.CANCEL_TURN, player).command().getTurnId());
        assertEquals(0, execute(player, "ai What is in my inventory?"));
        assertEquals(List.of(thinkingMessage(player)), messages(player));
        assertTrue(requests.isEmpty());
    }

    private boolean readsGameTool(Player player, String turn) throws Exception {
        return readGameTool(player, turn).hasObservation();
    }

    private AssistantToolResult readGameTool(Player player, String turn) throws Exception {
        return module.executeTool(toolCall(player, turn).build(), 1000);
    }

    private static AssistantToolCall.Builder toolCall(Player player, String turn) {
        return AssistantToolCall.newBuilder().setPlayerUuid(player.getUniqueId().toString())
                .setTurnId(turn).setName(WEATHER).setArgumentsJson("{}");
    }

    private Player connect() {
        return connect(UUID.randomUUID(), Language.EN_US);
    }

    private Player connect(UUID id, Language language) {
        var player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        when(player.isOnline()).thenReturn(true);
        when(player.hasPermission("mik.ai.use")).thenReturn(true);
        online.put(id, player);
        locales.put(player, language);
        inbox.put(player, new ArrayList<>());
        doAnswer(call -> {
            inbox.get(player).add(call.getArgument(0));
            return null;
        }).when(player).sendMessage(any(Component.class));
        return player;
    }

    private void quit(Player player) {
        var event = mock(PlayerQuitEvent.class);
        when(event.getPlayer()).thenReturn(player);
        module.onPlayerQuit(event);
        online.remove(player.getUniqueId(), player);
        when(player.isOnline()).thenReturn(false);
    }

    private int execute(Player player, String command) throws CommandSyntaxException {
        var source = mock(CommandSourceStack.class);
        when(source.getSender()).thenReturn(player);
        return dispatcher.execute(command, source);
    }

    private Exchange take(OperationCase operation, Player player) {
        var exchange = requests.removeFirst();
        assertEquals(operation, exchange.command().getOperationCase());
        assertEquals(player.getUniqueId().toString(), exchange.command().getPlayerUuid());
        return exchange;
    }

    private void complete(Exchange exchange, AssistantResult.Builder result) {
        exchange.reply().complete(result.build());
        poll.run();
    }

    private void loseResponse(Exchange exchange) {
        exchange.reply().completeExceptionally(new java.io.IOException("mikd disconnected"));
        poll.run();
        advanceOneSecond();
    }

    private void advanceOneSecond() {
        nowNanos += Duration.ofSeconds(1).toNanos();
        poll.run();
    }

    private Exchange accepted(Exchange ask) {
        assertTrue(ask.command().hasAsk());
        complete(ask, AssistantResult.newBuilder().setAccepted(SIGNAL));
        var player = online.get(UUID.fromString(ask.command().getPlayerUuid()));
        var exchange = take(OperationCase.POLL_RESULT, player);
        assertEquals(ask.command().getTurnId(), exchange.command().getTurnId());
        return exchange;
    }

    private void deliver(Exchange exchange, AssistantReply.Builder response) {
        if (exchange.command().hasAsk()) exchange = accepted(exchange);
        assertTrue(exchange.command().hasPollResult());
        complete(exchange, outcome(response));
        var command = exchange.command();
        var player = online.get(UUID.fromString(command.getPlayerUuid()));
        var receipt = take(OperationCase.CONFIRM_DELIVERY, player);
        assertEquals(command.getTurnId(), receipt.command().getTurnId());
        complete(receipt, AssistantResult.newBuilder().setDeliveryConfirmed(SIGNAL));
    }

    private Component replyToAsk(Player player, AssistantReply.Builder response) throws CommandSyntaxException {
        execute(player, "ai How do I use an iron elevator?");
        deliver(take(OperationCase.ASK, player), response);
        return inbox.get(player).getLast();
    }

    private ClickEvent<?> suggestion(Player player, String question) throws CommandSyntaxException {
        return suggestionButtons(replyToAsk(player, reply("Use iron blocks.")
                .addBlocks(followUp(question)))).getFirst().clickEvent();
    }

    private static List<Component> suggestionButtons(Component reply) {
        return components(reply).filter(part -> part.clickEvent() != null
                && part.clickEvent().action() == ClickEvent.Action.CUSTOM).toList();
    }

    private static void click(Player actor, ClickEvent<?> click) throws CommandSyntaxException {
        assertEquals(ClickEvent.Action.CUSTOM, click.action());
        var payload = assertInstanceOf(ClickEvent.Payload.Custom.class, click.payload());
        var manager = ClickCallbackProviderImpl.ADVENTURE_CLICK_MANAGER;
        manager.handleQueue(1);
        manager.tryRunCallback(actor, PaperAdventure.asVanilla(payload.key()),
                Optional.of(TagParser.parseCompoundFully(Objects.requireNonNull(payload.nbt()).string())));
    }

    private String text(Player player, Message key) {
        return translations.t(locales.get(player), key);
    }

    private List<String> messages(Player player) {
        return inbox.get(player).stream().map(AiModuleTest::plain).toList();
    }

    private String thinkingMessage(Player player) {
        return "◆ " + text(player, Message.AI_THINKING);
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static Stream<ClickEvent<?>> clicks(Component component) {
        return components(component).<ClickEvent<?>>map(Component::clickEvent).filter(Objects::nonNull);
    }

    private static Stream<Component> components(Component component) {
        return Stream.concat(Stream.of(component), component.children().stream().flatMap(AiModuleTest::components));
    }

    private static List<String> boldText(Component component) {
        return components(component).filter(part -> part.decoration(TextDecoration.BOLD) == TextDecoration.State.TRUE)
                .map(AiModuleTest::plain).toList();
    }

    private static AssistantSpan.Builder span(String text) {
        return AssistantSpan.newBuilder().setText(text);
    }

    private static AssistantSpan.Builder bold(String text) {
        return span(text).addStyles(Style.BOLD);
    }

    private static AssistantResult.Builder failure(Reason reason) {
        return AssistantResult.newBuilder().setFailure(AssistantFailure.newBuilder().setReason(reason));
    }

    private static AssistantReply.Builder reply(String answer) {
        return reply(span(answer));
    }

    private static AssistantReply.Builder reply(AssistantSpan.Builder... spans) {
        var part = AssistantPart.newBuilder();
        for (var span : spans) part.addSpans(span);
        return AssistantReply.newBuilder().addBlocks(paragraph(part));
    }

    private static AssistantPart.Builder part(String text) {
        return AssistantPart.newBuilder().addSpans(span(text));
    }

    private static AssistantBlock.Builder link(String label, String url) {
        return paragraph(part(label).setUrl(url));
    }

    private static AssistantBlock.Builder followUp(String prompt) {
        return paragraph(part(prompt).setAskLabel(AssistantAskLabel.getDefaultInstance()));
    }

    private static AssistantBlock.Builder paragraph(AssistantPart.Builder... parts) {
        var inline = AssistantInline.newBuilder();
        for (var part : parts) inline.addParts(part);
        return AssistantBlock.newBuilder().setParagraph(inline);
    }

    private static AssistantResult.Builder outcome(AssistantReply.Builder reply) {
        return AssistantResult.newBuilder().setReply(reply);
    }

    private record Exchange(AssistantCommand command, CompletableFuture<AssistantResult> reply) { }
}
