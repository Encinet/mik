package org.encinet.mik.module.event;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.encinet.mik.module.afk.AfkService;
import org.encinet.mik.module.afk.AfkState;
import org.encinet.mik.module.afk.AfkStateListener;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.menu.MenuItems;

import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.logging.Level;

public final class FifthAnniversaryEventModule implements Listener, AfkStateListener {

    private static final ZoneId EVENT_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Instant EVENT_START = ZonedDateTime.of(
            2026, 7, 20, 8, 0, 0, 0, EVENT_ZONE).toInstant();
    private static final Instant EVENT_END_EXCLUSIVE = ZonedDateTime.of(
            2026, 8, 1, 0, 0, 0, 0, EVENT_ZONE).toInstant();
    private static final String EVENT_TIME_TEXT = "2026/07/20 08:00 - 2026/07/31 23:59 (UTC+8:00)";
    private static final String COMMAND_NAME = "anniversary";
    private static final Set<UUID> EVENT_ADMINISTRATORS = Set.of(
            UUID.fromString("850ab457-2a91-45a5-916d-3cc24dc601c7"),
            UUID.fromString("531983d3-f5e4-4f0b-b1d3-3756be96b611"),
            UUID.fromString("bf02846b-0f29-4c42-891c-a95f2befc5fb")
    );
    private static final int MAX_DRAWS = 2;
    private static final long DRAW_INTERVAL_MILLIS = Duration.ofHours(4).toMillis();
    private static final long MAX_COUNTED_MILLIS = DRAW_INTERVAL_MILLIS * MAX_DRAWS;
    private static final long TICK_INTERVAL_TICKS = 20L;
    private static final int SAVE_INTERVAL_TICKS = 60;
    private static final String NO_PRIZE_ID = "not-won";
    private static final String SOLD_OUT_ID = "sold-out";
    private static final int ALGORITHM_VERSION = 2;
    private static final long CONTROL_BUCKET_MILLIS = Duration.ofMinutes(10).toMillis();
    private static final long RELEASE_SLOT_MILLIS = Duration.ofHours(2).toMillis();
    private static final double RELEASE_CURVE_EXPONENT = 0.72D;
    private static final double INITIAL_RELEASE_FRACTION = 0.08D;
    private static final int RELEASE_BORROW_LIMIT = 2;
    private static final double MIN_BASE_PROBABILITY = 0.02D;
    private static final double MAX_BASE_PROBABILITY = 0.58D;
    private static final double PERSONAL_ODDS_COEFFICIENT = 0.13D;
    private static final double PERSONAL_HISTORY_STRENGTH = 0.75D;
    private static final double MIN_PERSONAL_WEIGHT = 0.78D;
    private static final double MAX_PERSONAL_WEIGHT = 1.32D;
    private static final double PRIOR_OPPORTUNITIES_PER_HOUR = 1.5D;
    private static final int ADMIN_AUDIT_LIMIT = 10;
    private static final int WINNER_LIST_PAGE_SIZE = 10;
    private static final int VIRTUAL_BAG_SIZE = 27;
    private static final int VIRTUAL_BAG_CLOSE_SLOT = 22;
    private static final String GIFT_PACK_ID = "anniversary-gift-pack";
    private static final DateTimeFormatter ADMIN_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(EVENT_ZONE);
    private static final ClickCallback.Options CALLBACK_OPTIONS = ClickCallback.Options.builder()
            .uses(1)
            .lifetime(Duration.ofMinutes(5))
            .build();
    private static final String[] PRIZE_IDS = {
            "logo-keychain", "logo-mug", "group-photo-badge", GIFT_PACK_ID
    };
    private static final Material[] PRIZE_MATERIALS = {
            Material.TRIPWIRE_HOOK, Material.FLOWER_POT, Material.PAPER, Material.CHEST
    };
    private static final Message[] PRIZE_MESSAGES = {
            Message.ANNIVERSARY_PRIZE_KEYCHAIN,
            Message.ANNIVERSARY_PRIZE_MUG,
            Message.ANNIVERSARY_PRIZE_BADGE,
            Message.ANNIVERSARY_PRIZE_GIFT_PACK
    };
    private static final Message[] RULE_MESSAGES = {
            Message.ANNIVERSARY_RULE_1,
            Message.ANNIVERSARY_RULE_2,
            Message.ANNIVERSARY_RULE_3,
            Message.ANNIVERSARY_RULE_4,
            Message.ANNIVERSARY_RULE_5,
            Message.ANNIVERSARY_RULE_6,
            Message.ANNIVERSARY_RULE_7
    };
    private static final int[] INITIAL_STOCKS = {20, 10, 20, 5};

    static {
        if (PRIZE_IDS.length != PRIZE_MESSAGES.length
                || PRIZE_IDS.length != PRIZE_MATERIALS.length
                || PRIZE_IDS.length != INITIAL_STOCKS.length
                || Set.of(PRIZE_IDS).size() != PRIZE_IDS.length
                || Arrays.stream(INITIAL_STOCKS).anyMatch(stock -> stock <= 0)) {
            throw new IllegalStateException("Invalid anniversary prize configuration");
        }
    }

    private final JavaPlugin plugin;
    private final AfkService afkService;
    private final LanguageService languageService;
    private final File dataFile;
    private final Map<UUID, Participant> participants = new HashMap<>();
    private final Map<UUID, Long> lastAccountedAt = new HashMap<>();
    private final Map<UUID, BossBar> bossBars = new HashMap<>();
    private final List<AuditEntry> auditEntries = new ArrayList<>();
    private final int[] remainingStocks = new int[INITIAL_STOCKS.length];
    private final int[][] releaseSlots = new int[INITIAL_STOCKS.length][];
    private final SecureRandom random = new SecureRandom();

    private BukkitTask tickTask;
    private int ticksSinceSave;
    private long releasePlanSeed;
    private double lastBaseProbability;
    private long lastBaseProbabilityAt;
    private long nextAuditSequence = 1L;
    private boolean dataNeedsSave;

    public FifthAnniversaryEventModule(
            JavaPlugin plugin,
            AfkService afkService,
            LanguageService languageService
    ) {
        this.plugin = plugin;
        this.afkService = afkService;
        this.languageService = languageService;
        this.dataFile = new File(plugin.getDataFolder(), "fifth-anniversary-2026.yml");
    }

    public void enable() {
        loadData();
        dataNeedsSave |= removeEventAdministratorParticipants();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        afkService.addListener(this);

        long now = System.currentTimeMillis();
        if (dataNeedsSave) {
            saveData();
            dataNeedsSave = false;
        }
        Bukkit.getOnlinePlayers().stream()
                .filter(player -> !isEventAdministrator(player.getUniqueId()))
                .forEach(player -> lastAccountedAt.put(player.getUniqueId(), now));
        if (Instant.ofEpochMilli(now).isBefore(EVENT_END_EXCLUSIVE)) {
            tickTask = Bukkit.getScheduler().runTaskTimer(
                    plugin, this::tick, 1L, TICK_INTERVAL_TICKS);
        }
        plugin.getLogger().info("FifthAnniversaryEventModule enabled");
    }

    public void disable() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            accountTime(player, now, !afkService.isAfk(player.getUniqueId()));
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof VirtualBagHolder) {
                player.closeInventory();
            }
            hideBossBar(player);
        }
        afkService.removeListener(this);
        saveData();
        lastAccountedAt.clear();
        bossBars.clear();
    }

    public void registerCommands(LifecycleEventManager<Plugin> manager) {
        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> event.registrar().register(
                Commands.literal(COMMAND_NAME)
                        .executes(context -> execute(context.getSource().getSender()))
                        .then(Commands.literal("rule")
                                .executes(context -> showRules(
                                        context.getSource().getSender())))
                        .then(Commands.literal("bag")
                                .executes(context -> openVirtualBag(
                                        context.getSource().getSender())))
                        .then(Commands.literal("admin")
                                .requires(source -> isEventAdministrator(source.getSender()))
                                .executes(context -> sendAdminDashboard(
                                        context.getSource().getSender())))
                        .then(Commands.literal("audit")
                                .requires(source -> isEventAdministrator(source.getSender()))
                                .executes(context -> sendAdminAudit(
                                        context.getSource().getSender()))
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            suggestParticipantNames(builder.getRemaining(), builder::suggest);
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> sendParticipantAudit(
                                                context.getSource().getSender(),
                                                StringArgumentType.getString(context, "player")))))
                        .then(Commands.literal("winners")
                                .requires(source -> isEventAdministrator(source.getSender()))
                                .executes(context -> sendWinnerList(
                                        context.getSource().getSender(), 1))
                                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                        .executes(context -> sendWinnerList(
                                                context.getSource().getSender(),
                                                IntegerArgumentType.getInteger(context, "page")))))
                        .then(Commands.literal("bonus")
                                .requires(source -> isEventAdministrator(source.getSender()))
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            suggestParticipantNames(builder.getRemaining(), builder::suggest);
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> grantBuildingBonus(
                                                context.getSource().getSender(),
                                                StringArgumentType.getString(context, "player")))))
                        .build(),
                languageService.t(Language.DEFAULT, Message.ANNIVERSARY_COMMAND_DESCRIPTION),
                List.of("5th")));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!isEventAdministrator(player.getUniqueId())) {
            lastAccountedAt.put(player.getUniqueId(), System.currentTimeMillis());
        }
        Bukkit.getScheduler().runTask(plugin, () -> updateBossBar(player, Instant.now()));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        accountTime(player, now, !afkService.isAfk(player.getUniqueId()));
        lastAccountedAt.remove(player.getUniqueId());
        hideBossBar(player);
        saveData();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onVirtualBagClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof VirtualBagHolder) {
            event.setCancelled(true);
            if (event.getRawSlot() == VIRTUAL_BAG_CLOSE_SLOT
                    && event.getWhoClicked() instanceof Player player) {
                player.closeInventory();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onVirtualBagDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof VirtualBagHolder) {
            event.setCancelled(true);
        }
    }

    @Override
    public void onAfkStateChanged(Player player, AfkState state) {
        long now = System.currentTimeMillis();
        if (isEventAdministrator(player.getUniqueId())) {
            updateBossBar(player, Instant.ofEpochMilli(now));
            return;
        }
        if (state != null) {
            accountTime(player, now, true);
        } else {
            lastAccountedAt.put(player.getUniqueId(), now);
        }
        updateBossBar(player, Instant.ofEpochMilli(now));
    }

    private int execute(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(languageService.text(Language.DEFAULT, Message.PLAYER_ONLY, NamedTextColor.RED));
            return 0;
        }

        if (isEventAdministrator(player.getUniqueId())) {
            return sendAdminDashboard(player);
        }

        Instant now = Instant.now();
        if (now.isBefore(EVENT_START)) {
            player.sendMessage(text(player, Message.ANNIVERSARY_NOT_STARTED, NamedTextColor.YELLOW,
                    EVENT_TIME_TEXT));
            return Command.SINGLE_SUCCESS;
        }
        if (!now.isBefore(EVENT_END_EXCLUSIVE)) {
            Participant endedParticipant = participants.get(player.getUniqueId());
            if (endedParticipant != null && drawNext(player, endedParticipant, now.toEpochMilli())) {
                return Command.SINGLE_SUCCESS;
            }
            sendEndedStatus(player);
            return Command.SINGLE_SUCCESS;
        }

        accountTime(player, now.toEpochMilli(), !afkService.isAfk(player.getUniqueId()));
        Participant participant = participants.get(player.getUniqueId());
        if (participant == null) {
            openRulesDialog(player);
            return Command.SINGLE_SUCCESS;
        }
        if (drawNext(player, participant, now.toEpochMilli())) {
            return Command.SINGLE_SUCCESS;
        }
        sendParticipantStatus(player, participant);
        return Command.SINGLE_SUCCESS;
    }

    private int openVirtualBag(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(languageService.text(Language.DEFAULT, Message.PLAYER_ONLY,
                    NamedTextColor.RED));
            return 0;
        }
        if (isEventAdministrator(player.getUniqueId())) {
            return sendAdminDashboard(player);
        }
        Participant participant = participants.get(player.getUniqueId());
        if (participant == null) {
            player.sendMessage(text(player, Message.ANNIVERSARY_BAG_NOT_PARTICIPATING,
                    NamedTextColor.YELLOW, "/" + COMMAND_NAME));
            return 0;
        }

        VirtualBagHolder holder = new VirtualBagHolder(player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(holder, VIRTUAL_BAG_SIZE,
                text(player, Message.ANNIVERSARY_BAG_TITLE, NamedTextColor.GOLD));
        holder.attach(inventory);
        int[] prizeSlots = virtualBagPrizeSlots(participant.virtualBag.size());
        for (int index = 0; index < participant.virtualBag.size(); index++) {
            inventory.setItem(prizeSlots[index],
                    virtualPrizeItem(player, participant.virtualBag.get(index)));
        }
        if (participant.virtualBag.isEmpty()) {
            inventory.setItem(13, MenuItems.item(Material.GRAY_DYE,
                    text(player, Message.ANNIVERSARY_BAG_EMPTY, NamedTextColor.GRAY), List.of()));
        }
        inventory.setItem(VIRTUAL_BAG_CLOSE_SLOT, MenuItems.item(Material.BARRIER,
                text(player, Message.CLOSE, NamedTextColor.RED), List.of()));
        player.openInventory(inventory);
        return Command.SINGLE_SUCCESS;
    }

    static int[] virtualBagPrizeSlots(int prizeCount) {
        return switch (prizeCount) {
            case 0 -> new int[0];
            case 1 -> new int[]{13};
            case 2 -> new int[]{12, 14};
            case 3 -> new int[]{11, 13, 15};
            default -> throw new IllegalArgumentException("Unexpected virtual bag prize count");
        };
    }

    private ItemStack virtualPrizeItem(Player player, String prizeId) {
        int prizeIndex = prizeIndex(prizeId);
        ItemStack item = new ItemStack(PRIZE_MATERIALS[prizeIndex]);
        ItemMeta meta = item.getItemMeta();
        meta.customName(text(player, Message.ANNIVERSARY_BAG_ITEM_NAME,
                NamedTextColor.GOLD, prizeName(player, prizeId)));
        meta.lore(List.of(
                text(player, Message.ANNIVERSARY_BAG_ITEM_LORE, NamedTextColor.GRAY),
                text(player, Message.ANNIVERSARY_BAG_ITEM_LOCKED, NamedTextColor.DARK_GRAY)));
        meta.setEnchantmentGlintOverride(true);
        meta.setMaxStackSize(1);
        item.setItemMeta(meta);
        return item;
    }

    private boolean isEventAdministrator(CommandSender sender) {
        return sender instanceof Player player
                && isEventAdministrator(player.getUniqueId());
    }

    static boolean isEventAdministrator(UUID playerId) {
        return playerId != null && EVENT_ADMINISTRATORS.contains(playerId);
    }

    private void suggestParticipantNames(
            String remaining,
            java.util.function.Consumer<String> suggestionConsumer
    ) {
        String prefix = remaining.toLowerCase(Locale.ROOT);
        participants.values().stream()
                .map(participant -> participant.lastKnownName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .forEach(suggestionConsumer);
    }

    private int sendAdminDashboard(CommandSender sender) {
        Instant now = Instant.now();
        long nowMillis = now.toEpochMilli();
        List<DrawResult> draws = allDrawResults();
        int ready = participants.values().stream()
                .mapToInt(this::readyOpportunityCount)
                .sum();
        int earned = participants.values().stream()
                .mapToInt(this::earnedOpportunityCount)
                .sum();
        long wins = draws.stream().filter(draw -> isWinningPrize(draw.prizeId)).count();
        double probability = previewBaseProbability(nowMillis);

        sender.sendMessage(text(sender, Message.ANNIVERSARY_ADMIN_TITLE, NamedTextColor.GOLD));
        sender.sendMessage(text(sender, Message.ANNIVERSARY_ADMIN_EVENT, NamedTextColor.WHITE,
                adminEventState(sender, now), EVENT_TIME_TEXT));
        sender.sendMessage(text(sender, Message.ANNIVERSARY_ADMIN_PARTICIPANTS, NamedTextColor.WHITE,
                participants.size()));
        sender.sendMessage(text(sender, Message.ANNIVERSARY_ADMIN_OPPORTUNITIES,
                NamedTextColor.WHITE, draws.size(), ready, earned,
                wins + "/" + (draws.size() - wins)));
        sender.sendMessage(text(sender, Message.ANNIVERSARY_ADMIN_STOCK, NamedTextColor.YELLOW,
                prizeName(sender, PRIZE_IDS[0]) + " " + remainingStocks[0] + "/" + INITIAL_STOCKS[0],
                prizeName(sender, PRIZE_IDS[1]) + " " + remainingStocks[1] + "/" + INITIAL_STOCKS[1],
                prizeName(sender, PRIZE_IDS[2]) + " " + remainingStocks[2] + "/" + INITIAL_STOCKS[2],
                prizeName(sender, PRIZE_IDS[3]) + " " + remainingStocks[3] + "/" + INITIAL_STOCKS[3],
                remainingStock() + "/" + Arrays.stream(INITIAL_STOCKS).sum()));
        sender.sendMessage(text(sender, Message.ANNIVERSARY_ADMIN_CONTROL, NamedTextColor.AQUA,
                releasedStockAt(nowMillis), availableReleasedStock(nowMillis),
                formatProbability(probability), formatOpportunityRate(nowMillis)));
        sender.sendMessage(text(sender, Message.ANNIVERSARY_ADMIN_COMMANDS, NamedTextColor.GRAY));
        return Command.SINGLE_SUCCESS;
    }

    private int sendWinnerList(CommandSender sender, int requestedPage) {
        List<WinnerEntry> winners = winnerEntries();
        int totalPages = winnerListPageCount(winners.size());
        int page = Math.clamp(requestedPage, 1, totalPages);
        sender.sendMessage(text(sender, Message.ANNIVERSARY_ADMIN_WINNERS_TITLE,
                NamedTextColor.GOLD, page, totalPages, winners.size()));
        if (winners.isEmpty()) {
            sender.sendMessage(text(sender, Message.ANNIVERSARY_ADMIN_WINNERS_EMPTY,
                    NamedTextColor.GRAY));
            return Command.SINGLE_SUCCESS;
        }

        winners.sort(Comparator
                .comparingLong((WinnerEntry entry) -> entry.draw.drawnAt)
                .reversed()
                .thenComparing(entry -> entry.playerId));
        int firstIndex = (page - 1) * WINNER_LIST_PAGE_SIZE;
        int lastIndex = Math.min(firstIndex + WINNER_LIST_PAGE_SIZE, winners.size());
        for (WinnerEntry winner : winners.subList(firstIndex, lastIndex)) {
            DrawResult draw = winner.draw;
            String source = draw.round == 0
                    ? adminValue(sender, Message.ANNIVERSARY_ADMIN_DRAW_BONUS)
                    : adminValue(sender, Message.ANNIVERSARY_ADMIN_DRAW_ROUND, draw.round);
            sender.sendMessage(text(sender, Message.ANNIVERSARY_ADMIN_WINNERS_ENTRY,
                    NamedTextColor.WHITE, displayValue(winner.playerName), winner.playerId,
                    source + " · " + prizeName(sender, draw.prizeId),
                    formatAdminTime(draw.drawnAt)));
        }
        return Command.SINGLE_SUCCESS;
    }

    private List<WinnerEntry> winnerEntries() {
        List<WinnerEntry> winners = new ArrayList<>();
        for (Map.Entry<UUID, Participant> entry : participants.entrySet()) {
            Participant participant = entry.getValue();
            List<DrawResult> draws = allDrawResults(
                    participant.regularDraws, participant.bonusDraw);
            for (String prizeId : participant.virtualBag) {
                draws.stream()
                        .filter(draw -> prizeId.equals(draw.prizeId) && !draw.superseded)
                        .max(Comparator.comparingLong(draw -> draw.drawnAt))
                        .map(draw -> new WinnerEntry(
                                entry.getKey(), participant.lastKnownName, draw))
                        .ifPresent(winners::add);
            }
        }
        return winners;
    }

    static int winnerListPageCount(int winnerCount) {
        return Math.max(1, (Math.max(0, winnerCount) + WINNER_LIST_PAGE_SIZE - 1)
                / WINNER_LIST_PAGE_SIZE);
    }

    private int sendAdminAudit(CommandSender sender) {
        List<AuditEntry> recent = auditEntries.stream()
                .sorted(Comparator.comparingLong((AuditEntry entry) -> entry.occurredAt)
                        .thenComparingLong(entry -> entry.sequence)
                        .reversed())
                .limit(ADMIN_AUDIT_LIMIT)
                .toList();
        sender.sendMessage(text(sender, Message.ANNIVERSARY_ADMIN_AUDIT_TITLE,
                NamedTextColor.GOLD, recent.size(), auditEntries.size()));
        if (recent.isEmpty()) {
            sender.sendMessage(text(sender, Message.ANNIVERSARY_ADMIN_AUDIT_EMPTY,
                    NamedTextColor.GRAY));
            return Command.SINGLE_SUCCESS;
        }
        for (AuditEntry entry : recent) {
            sender.sendMessage(text(sender, Message.ANNIVERSARY_ADMIN_AUDIT_ENTRY,
                    NamedTextColor.WHITE, formatAdminTime(entry.occurredAt),
                    auditAction(sender, entry)));
            sender.sendMessage(text(sender, Message.ANNIVERSARY_ADMIN_AUDIT_IDENTITIES,
                    NamedTextColor.GRAY, displayValue(entry.actorName),
                    displayUuid(entry.actorId), displayValue(entry.targetName),
                    displayUuid(entry.targetId)));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int sendParticipantAudit(CommandSender sender, String requestedName) {
        Map.Entry<UUID, Participant> target = findParticipant(requestedName);
        if (target == null) {
            sender.sendMessage(text(sender, Message.ANNIVERSARY_ADMIN_PLAYER_NOT_FOUND,
                    NamedTextColor.RED, requestedName));
            return 0;
        }

        Participant participant = target.getValue();
        sender.sendMessage(text(sender, Message.ANNIVERSARY_ADMIN_PLAYER_TITLE,
                NamedTextColor.GOLD, participant.lastKnownName, target.getKey()));
        sender.sendMessage(text(sender, Message.ANNIVERSARY_ADMIN_PLAYER_PROGRESS,
                NamedTextColor.WHITE, formatAdminTime(participant.acceptedAt),
                formatDuration(participant.activeMillis), participant.regularDraws.size(),
                readyRegularDrawCount(participant)));
        sender.sendMessage(text(sender, Message.ANNIVERSARY_ADMIN_PLAYER_OUTCOME,
                NamedTextColor.WHITE, participant.wins, participant.losses,
                performedDrawCount(participant), participant.bonusGranted
                        ? adminBonusState(sender, participant) : adminValue(sender, Message.ANNIVERSARY_ADMIN_NONE)));
        sender.sendMessage(text(sender, Message.ANNIVERSARY_ADMIN_PLAYER_BAG,
                NamedTextColor.YELLOW, participant.virtualBag.isEmpty()
                        ? adminValue(sender, Message.ANNIVERSARY_ADMIN_NONE)
                        : localizedPrizes(sender, participant.virtualBag)));
        if (participant.bonusGranted) {
            sender.sendMessage(text(sender, Message.ANNIVERSARY_ADMIN_PLAYER_BONUS,
                    NamedTextColor.AQUA, adminBonusState(sender, participant),
                    formatAdminTime(participant.bonusGrantedAt),
                    displayValue(participant.bonusGrantedBy),
                    displayUuid(participant.bonusGrantedById)));
        }
        sender.sendMessage(text(sender, Message.ANNIVERSARY_ADMIN_PLAYER_HISTORY,
                NamedTextColor.YELLOW));
        if (participant.regularDraws.isEmpty() && participant.bonusDraw == null) {
            sender.sendMessage(text(sender, Message.ANNIVERSARY_ADMIN_NONE, NamedTextColor.GRAY));
            return Command.SINGLE_SUCCESS;
        }
        participant.regularDraws.stream()
                .sorted(Comparator.comparingInt(draw -> draw.round))
                .forEach(draw -> sendDrawAudit(sender,
                        adminValue(sender, Message.ANNIVERSARY_ADMIN_DRAW_ROUND, draw.round), draw));
        if (participant.bonusDraw != null) {
            sendDrawAudit(sender, adminValue(sender, Message.ANNIVERSARY_ADMIN_DRAW_BONUS),
                    participant.bonusDraw);
        }
        return Command.SINGLE_SUCCESS;
    }

    private void sendDrawAudit(CommandSender sender, String label, DrawResult draw) {
        String effectiveLabel = draw.superseded
                ? label + " · " + adminValue(sender, Message.ANNIVERSARY_ADMIN_DRAW_REPLACED)
                : label;
        sender.sendMessage(text(sender, Message.ANNIVERSARY_ADMIN_DRAW_DETAIL,
                NamedTextColor.WHITE, effectiveLabel, formatAdminTime(draw.drawnAt),
                prizeName(sender, draw.prizeId)));
        sender.sendMessage(text(sender, Message.ANNIVERSARY_ADMIN_DRAW_METRICS,
                NamedTextColor.GRAY, formatProbability(draw.probability),
                String.format(Locale.ROOT, "%.8f", draw.roll), draw.algorithmVersion));
    }

    private Map.Entry<UUID, Participant> findParticipant(String requestedName) {
        UUID requestedId = parseUuid(requestedName);
        return participants.entrySet().stream()
                .filter(entry -> requestedId != null
                        ? entry.getKey().equals(requestedId)
                        : entry.getValue().lastKnownName.equalsIgnoreCase(requestedName))
                .findFirst()
                .orElse(null);
    }

    private String adminEventState(CommandSender sender, Instant now) {
        if (now.isBefore(EVENT_START)) {
            return adminValue(sender, Message.ANNIVERSARY_ADMIN_STATE_SCHEDULED);
        }
        if (now.isBefore(EVENT_END_EXCLUSIVE)) {
            return adminValue(sender, Message.ANNIVERSARY_ADMIN_STATE_ACTIVE);
        }
        return adminValue(sender, Message.ANNIVERSARY_ADMIN_STATE_ENDED);
    }

    private String adminBonusState(CommandSender sender, Participant participant) {
        if (hasGiftPack(participant)) {
            return adminValue(sender, Message.ANNIVERSARY_ADMIN_BONUS_CANCELLED);
        }
        return adminValue(sender, participant.bonusDraw != null
                ? Message.ANNIVERSARY_ADMIN_BONUS_USED
                : Message.ANNIVERSARY_ADMIN_BONUS_PENDING);
    }

    private String auditAction(CommandSender sender, AuditEntry entry) {
        return switch (entry.action) {
            case AuditAction.BONUS_GRANT -> adminValue(sender,
                    Message.ANNIVERSARY_ADMIN_AUDIT_ACTION_BONUS);
            case AuditAction.ADMIN_PARTICIPANT_REMOVED -> adminValue(sender,
                    Message.ANNIVERSARY_ADMIN_AUDIT_ACTION_REMOVAL,
                    displayValue(entry.detail));
            default -> adminValue(sender, Message.ANNIVERSARY_ADMIN_AUDIT_ACTION_UNKNOWN,
                    entry.action);
        };
    }

    private double previewBaseProbability(long nowMillis) {
        if (nowMillis < EVENT_START.toEpochMilli()
                || nowMillis >= EVENT_END_EXCLUSIVE.toEpochMilli()) {
            return 0.0D;
        }
        int available = availableReleasedStock(nowMillis);
        if (remainingStock() <= 0 || available <= 0) {
            return 0.0D;
        }
        long bucket = Math.floorDiv(nowMillis, CONTROL_BUCKET_MILLIS) * CONTROL_BUCKET_MILLIS;
        if (lastBaseProbabilityAt == bucket && lastBaseProbability > 0.0D) {
            return lastBaseProbability;
        }
        int releaseInWindow = releasedStockAt(nowMillis + RELEASE_SLOT_MILLIS)
                - releasedStockAt(nowMillis);
        double remainingHours = Math.max(0.0D,
                (EVENT_END_EXCLUSIVE.toEpochMilli() - nowMillis) / 3_600_000.0D);
        double candidate = calculateBaseProbability(new ControllerSnapshot(
                remainingStock(), available, Math.max(0, releaseInWindow),
                opportunityRate(nowMillis), remainingHours, knownFutureRegularOpportunities()));
        if (lastBaseProbability > 0.0D && lastBaseProbabilityAt > 0L) {
            long elapsedBuckets = Math.max(1L,
                    Math.floorDiv(bucket - lastBaseProbabilityAt, CONTROL_BUCKET_MILLIS));
            candidate = limitOddsChange(lastBaseProbability, candidate,
                    Math.pow(0.80D, elapsedBuckets), Math.pow(1.20D, elapsedBuckets));
        }
        return candidate;
    }

    private String formatAdminTime(long millis) {
        return millis <= 0L ? "-" : ADMIN_TIME_FORMAT.format(Instant.ofEpochMilli(millis));
    }

    private String formatProbability(double probability) {
        return String.format(Locale.ROOT, "%.2f%%", probability * 100.0D);
    }

    private String formatOpportunityRate(long nowMillis) {
        if (nowMillis < EVENT_START.toEpochMilli()) {
            return "0.00/h";
        }
        return String.format(Locale.ROOT, "%.2f/h", opportunityRate(nowMillis));
    }

    private String displayValue(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String displayUuid(UUID value) {
        return value == null ? "-" : value.toString();
    }

    private Component rulesDialogBody(Player player) {
        return Component.text()
                .append(text(player, Message.ANNIVERSARY_DIALOG_TIME, NamedTextColor.YELLOW, EVENT_TIME_TEXT))
                .appendNewline()
                .append(text(player, Message.ANNIVERSARY_DIALOG_PRIZES, NamedTextColor.GOLD,
                        prizeName(player, PRIZE_IDS[0]),
                        prizeName(player, PRIZE_IDS[1]),
                        prizeName(player, PRIZE_IDS[2]),
                        prizeName(player, PRIZE_IDS[3])))
                .appendNewline()
                .appendNewline()
                .append(rules(player))
                .build();
    }

    private DialogBase rulesDialogBase(Player player) {
        return DialogBase.builder(text(player, Message.ANNIVERSARY_DIALOG_TITLE, NamedTextColor.GOLD))
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(List.of(DialogBody.plainMessage(rulesDialogBody(player), 400)))
                .inputs(List.of())
                .build();
    }

    private void openRulesDialog(Player player) {

        ActionButton confirm = ActionButton.create(
                text(player, Message.ANNIVERSARY_CONFIRM, NamedTextColor.GREEN),
                text(player, Message.ANNIVERSARY_CONFIRM_HINT, NamedTextColor.GRAY),
                130,
                DialogAction.customClick((response, audience) -> runOnMain(audience, this::confirmParticipation),
                        CALLBACK_OPTIONS));
        ActionButton cancel = ActionButton.create(
                text(player, Message.ANNIVERSARY_CANCEL, NamedTextColor.GRAY), null, 100, null);

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(rulesDialogBase(player))
                .type(DialogType.confirmation(confirm, cancel)));
        player.showDialog(dialog);
    }

    private int showRules(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(languageService.text(Language.DEFAULT, Message.PLAYER_ONLY,
                    NamedTextColor.RED));
            return 0;
        }

        ActionButton close = ActionButton.create(
                text(player, Message.CLOSE, NamedTextColor.GRAY), null, 100, null);
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(rulesDialogBase(player))
                .type(DialogType.notice(close)));
        player.showDialog(dialog);
        return Command.SINGLE_SUCCESS;
    }

    private void confirmParticipation(Player player) {
        if (isEventAdministrator(player.getUniqueId())) {
            player.sendMessage(text(player, Message.ANNIVERSARY_ADMIN_EXCLUDED, NamedTextColor.RED));
            sendAdminDashboard(player);
            return;
        }
        Instant now = Instant.now();
        if (now.isBefore(EVENT_START) || !now.isBefore(EVENT_END_EXCLUSIVE)) {
            player.sendMessage(text(player, Message.ANNIVERSARY_NOT_ACTIVE, NamedTextColor.RED));
            return;
        }
        UUID playerId = player.getUniqueId();
        if (participants.containsKey(playerId)) {
            sendParticipantStatus(player, participants.get(playerId));
            return;
        }

        Participant participant = new Participant(
                player.getName(), now.toEpochMilli(), 0L, new ArrayList<>(),
                new ArrayList<>(), null, 0, 0, false, null, null, 0L);
        participants.put(playerId, participant);
        lastAccountedAt.put(playerId, now.toEpochMilli());
        saveData();
        player.sendMessage(text(player, Message.ANNIVERSARY_ACCEPTED, NamedTextColor.GREEN));
        updateBossBar(player, now);
    }

    private boolean regularDrawReady(Participant participant) {
        return !hasGiftPack(participant) && readyRegularDrawCount(participant) > 0;
    }

    private int readyRegularDrawCount(Participant participant) {
        return readyRegularDrawCount(participant.activeMillis,
                participant.regularDraws.size(), hasGiftPack(participant));
    }

    static int readyRegularDrawCount(long activeMillis, int performedDraws) {
        return readyRegularDrawCount(activeMillis, performedDraws, false);
    }

    static int readyRegularDrawCount(
            long activeMillis,
            int performedDraws,
            boolean giftPackFinal
    ) {
        if (giftPackFinal) {
            return 0;
        }
        int unlocked = (int) Math.min(MAX_DRAWS,
                Math.max(0L, activeMillis) / DRAW_INTERVAL_MILLIS);
        return Math.max(0, unlocked - Math.max(0, performedDraws));
    }

    private boolean bonusDrawReady(Participant participant) {
        return bonusDrawReady(
                hasGiftPack(participant), participant.bonusGranted, participant.bonusDraw != null);
    }

    static boolean bonusDrawReady(
            boolean giftPackFinal,
            boolean bonusGranted,
            boolean bonusDrawn
    ) {
        return !giftPackFinal && bonusGranted && !bonusDrawn;
    }

    private int readyOpportunityCount(Participant participant) {
        return readyRegularDrawCount(participant) + (bonusDrawReady(participant) ? 1 : 0);
    }

    private int earnedOpportunityCount(Participant participant) {
        if (hasGiftPack(participant)) {
            return performedDrawCount(participant);
        }
        int regular = (int) Math.min(MAX_DRAWS,
                participant.activeMillis / DRAW_INTERVAL_MILLIS);
        return regular + (participant.bonusGranted ? 1 : 0);
    }

    private int performedDrawCount(Participant participant) {
        return participant.regularDraws.size() + (participant.bonusDraw == null ? 0 : 1);
    }

    private boolean drawNext(Player player, Participant participant, long drawnAt) {
        if (regularDrawReady(participant)) {
            int round = participant.regularDraws.size() + 1;
            DrawResult draw = drawOpportunity(participant, round, drawnAt);
            participant.regularDraws.add(draw);
            finishRegularDraw(player, participant, draw);
            return true;
        }
        if (bonusDrawReady(participant)) {
            DrawResult draw = drawOpportunity(participant, 0, drawnAt);
            participant.bonusDraw = draw;
            finishBonusDraw(player, participant, draw);
            return true;
        }
        return false;
    }

    private void finishRegularDraw(Player player, Participant participant, DrawResult draw) {
        int replaced = applyDrawToVirtualBag(participant, draw);
        participant.lastKnownName = player.getName();
        saveData();

        if (!isWinningPrize(draw.prizeId)) {
            Message message = SOLD_OUT_ID.equals(draw.prizeId)
                    ? Message.ANNIVERSARY_DRAW_SOLD_OUT
                    : Message.ANNIVERSARY_DRAW_NOT_WON;
            player.sendMessage(text(player, message, NamedTextColor.YELLOW, draw.round));
        } else {
            player.sendMessage(text(player, Message.ANNIVERSARY_DRAW_WON, NamedTextColor.GREEN,
                    draw.round, prizeName(player, draw.prizeId)));
            player.sendMessage(text(player, Message.ANNIVERSARY_DELIVERY_NOTE, NamedTextColor.YELLOW));
            player.sendMessage(text(player, Message.ANNIVERSARY_BAG_UPDATED, NamedTextColor.AQUA,
                    "/" + COMMAND_NAME + " bag"));
            if (GIFT_PACK_ID.equals(draw.prizeId)) {
                player.sendMessage(text(player, Message.ANNIVERSARY_GIFT_PACK_FINAL,
                        NamedTextColor.GOLD, replaced));
            }
            broadcastWinner(player.getName(), draw.round, draw.prizeId);
        }
        if (!hasGiftPack(participant) && participant.regularDraws.size() < MAX_DRAWS) {
            player.sendMessage(text(player, Message.ANNIVERSARY_SECOND_ROUND_HINT, NamedTextColor.GRAY));
        }
        updateBossBar(player, Instant.now());
    }

    private void finishBonusDraw(Player player, Participant participant, DrawResult draw) {
        int replaced = applyDrawToVirtualBag(participant, draw);
        participant.lastKnownName = player.getName();
        saveData();

        if (!isWinningPrize(draw.prizeId)) {
            Message message = SOLD_OUT_ID.equals(draw.prizeId)
                    ? Message.ANNIVERSARY_BONUS_DRAW_SOLD_OUT
                    : Message.ANNIVERSARY_BONUS_DRAW_NOT_WON;
            player.sendMessage(text(player, message, NamedTextColor.YELLOW));
        } else {
            player.sendMessage(text(player, Message.ANNIVERSARY_BONUS_DRAW_WON, NamedTextColor.GREEN,
                    prizeName(player, draw.prizeId)));
            player.sendMessage(text(player, Message.ANNIVERSARY_DELIVERY_NOTE, NamedTextColor.YELLOW));
            player.sendMessage(text(player, Message.ANNIVERSARY_BAG_UPDATED, NamedTextColor.AQUA,
                    "/" + COMMAND_NAME + " bag"));
            if (GIFT_PACK_ID.equals(draw.prizeId)) {
                player.sendMessage(text(player, Message.ANNIVERSARY_GIFT_PACK_FINAL,
                        NamedTextColor.GOLD, replaced));
            }
            broadcastBonusWinner(player.getName(), draw.prizeId);
        }
        updateBossBar(player, Instant.now());
    }

    private int grantBuildingBonus(CommandSender sender, String requestedName) {
        Instant now = Instant.now();
        if (now.isBefore(EVENT_START) || !now.isBefore(EVENT_END_EXCLUSIVE)) {
            sender.sendMessage(text(sender, Message.ANNIVERSARY_BONUS_GRANT_NOT_ACTIVE, NamedTextColor.RED));
            return 0;
        }

        UUID requestedId = parseUuid(requestedName);
        Player requestedPlayer = Bukkit.getPlayerExact(requestedName);
        if (isEventAdministrator(requestedId)
                || requestedPlayer != null
                && isEventAdministrator(requestedPlayer.getUniqueId())) {
            sender.sendMessage(text(sender, Message.ANNIVERSARY_ADMIN_TARGET_EXCLUDED,
                    NamedTextColor.RED, requestedName));
            return 0;
        }

        Map.Entry<UUID, Participant> targetEntry = findParticipant(requestedName);
        if (targetEntry == null) {
            sender.sendMessage(text(sender, Message.ANNIVERSARY_BONUS_PARTICIPANT_NOT_FOUND,
                    NamedTextColor.RED, requestedName));
            return 0;
        }

        if (isEventAdministrator(targetEntry.getKey())) {
            sender.sendMessage(text(sender, Message.ANNIVERSARY_ADMIN_TARGET_EXCLUDED,
                    NamedTextColor.RED, requestedName));
            return 0;
        }

        Participant participant = targetEntry.getValue();
        if (hasGiftPack(participant)) {
            sender.sendMessage(text(sender, Message.ANNIVERSARY_BONUS_GIFT_PACK_FINAL,
                    NamedTextColor.RED, participant.lastKnownName));
            return 0;
        }
        if (participant.bonusGranted) {
            sender.sendMessage(text(sender, Message.ANNIVERSARY_BONUS_ALREADY_GRANTED,
                    NamedTextColor.RED, participant.lastKnownName));
            return 0;
        }

        participant.bonusGranted = true;
        participant.bonusGrantedBy = sender.getName();
        participant.bonusGrantedById = ((Player) sender).getUniqueId();
        participant.bonusGrantedAt = now.toEpochMilli();
        appendAudit(AuditAction.BONUS_GRANT, now.toEpochMilli(),
                ((Player) sender).getUniqueId(), sender.getName(),
                targetEntry.getKey(), participant.lastKnownName, null);
        saveData();
        sender.sendMessage(text(sender, Message.ANNIVERSARY_BONUS_GRANT_SUCCESS,
                NamedTextColor.GREEN, participant.lastKnownName));

        Player target = Bukkit.getPlayer(targetEntry.getKey());
        if (target != null) {
            target.sendMessage(text(target, Message.ANNIVERSARY_BONUS_GRANTED, NamedTextColor.GREEN));
            updateBossBar(target, now);
        }
        return Command.SINGLE_SUCCESS;
    }

    private DrawResult drawOpportunity(
            Participant participant,
            int round,
            long drawnAt
    ) {
        Set<String> excludedPrizeIds = Set.copyOf(participant.virtualBag);
        int[] availableByType = availablePrizeStockForPlayer(drawnAt, excludedPrizeIds);
        double baseProbability = Arrays.stream(availableByType).sum() > 0
                ? baseProbability(drawnAt) : 0.0D;
        double weight = personalWeight(participant.wins, participant.losses);
        double calibration = baseProbability > 0.0D
                ? calibrationOffset(baseProbability) : 0.0D;
        double probability = baseProbability <= 0.0D
                ? 0.0D
                : Math.clamp(personalizedProbability(
                        baseProbability, weight, calibration),
                        0.0D, 1.0D);

        double roll = random.nextDouble();
        String prizeId = remainingStock() <= 0 ? SOLD_OUT_ID : NO_PRIZE_ID;
        if (roll < probability) {
            prizeId = selectReleasedPrize(availableByType);
        }
        if (isWinningPrize(prizeId)) {
            participant.wins++;
        } else {
            participant.losses++;
        }
        return new DrawResult(round, drawnAt, prizeId,
                probability, roll, ALGORITHM_VERSION, false);
    }

    private double baseProbability(long nowMillis) {
        int available = availableReleasedStock(nowMillis);
        if (remainingStock() <= 0 || available <= 0) {
            return 0.0D;
        }
        long bucket = Math.floorDiv(nowMillis, CONTROL_BUCKET_MILLIS) * CONTROL_BUCKET_MILLIS;
        if (lastBaseProbabilityAt == bucket && lastBaseProbability > 0.0D) {
            return lastBaseProbability;
        }

        // Global pacing controls expected wins; personal history only redistributes that budget.
        double opportunityRate = opportunityRate(nowMillis);
        int releaseInWindow = releasedStockAt(nowMillis + RELEASE_SLOT_MILLIS)
                - releasedStockAt(nowMillis);
        double remainingHours = Math.max(0.0D,
                (EVENT_END_EXCLUSIVE.toEpochMilli() - nowMillis) / 3_600_000.0D);
        int knownFuture = knownFutureRegularOpportunities();
        ControllerSnapshot snapshot = new ControllerSnapshot(
                remainingStock(), available, Math.max(0, releaseInWindow),
                opportunityRate, remainingHours, knownFuture);
        double candidate = calculateBaseProbability(snapshot);
        if (lastBaseProbability > 0.0D && lastBaseProbabilityAt > 0L) {
            long elapsedBuckets = Math.max(1L,
                    Math.floorDiv(bucket - lastBaseProbabilityAt, CONTROL_BUCKET_MILLIS));
            candidate = limitOddsChange(lastBaseProbability, candidate,
                    Math.pow(0.80D, elapsedBuckets), Math.pow(1.20D, elapsedBuckets));
        }
        lastBaseProbability = candidate;
        lastBaseProbabilityAt = bucket;
        return candidate;
    }

    static double calculateBaseProbability(ControllerSnapshot snapshot) {
        if (snapshot.remainingStock <= 0) {
            return 0.0D;
        }
        if (snapshot.availableStock <= 0) {
            return 0.0D;
        }
        double expectedWindowOpportunities = Math.max(1.0D,
                snapshot.opportunitiesPerHour * RELEASE_SLOT_MILLIS / 3_600_000.0D);
        double desiredWindowWins = snapshot.releaseInWindow + 0.35D * snapshot.availableStock;
        double speedProbability = desiredWindowWins / expectedWindowOpportunities;

        double predictedRemainingOpportunities = Math.max(
                snapshot.remainingStock,
                Math.max(snapshot.knownFutureOpportunities,
                        snapshot.opportunitiesPerHour * snapshot.remainingHours));
        double stockProbability = snapshot.remainingStock / predictedRemainingOpportunities;
        return Math.clamp(0.80D * speedProbability + 0.20D * stockProbability,
                MIN_BASE_PROBABILITY, MAX_BASE_PROBABILITY);
    }

    private double opportunityRate(long nowMillis) {
        return 0.50D * smoothedRate(nowMillis, Duration.ofMinutes(30).toMillis())
                + 0.30D * smoothedRate(nowMillis, Duration.ofHours(2).toMillis())
                + 0.20D * smoothedRate(nowMillis, Duration.ofHours(12).toMillis());
    }

    private double smoothedRate(long nowMillis, long windowMillis) {
        long observedMillis = Math.clamp(
                nowMillis - EVENT_START.toEpochMilli(), 0L, windowMillis);
        long from = nowMillis - observedMillis;
        long count = allDrawResults().stream()
                .filter(draw -> draw.drawnAt > from && draw.drawnAt <= nowMillis)
                .count();
        double windowHours = observedMillis / 3_600_000.0D;
        double priorHours = 1.0D;
        return (count + PRIOR_OPPORTUNITIES_PER_HOUR * priorHours) / (windowHours + priorHours);
    }

    private int knownFutureRegularOpportunities() {
        return participants.values().stream()
                .filter(participant -> !hasGiftPack(participant))
                .mapToInt(participant -> MAX_DRAWS - participant.regularDraws.size())
                .sum();
    }

    private double calibrationOffset(double baseProbability) {
        List<Double> weights = futureOpportunityWeights();
        return calibratedPersonalOffset(baseProbability, weights);
    }

    private List<Double> futureOpportunityWeights() {
        List<Double> weights = new ArrayList<>();
        for (Participant participant : participants.values()) {
            if (hasGiftPack(participant)) {
                continue;
            }
            int remainingOpportunities = MAX_DRAWS - participant.regularDraws.size();
            if (participant.bonusGranted && participant.bonusDraw == null) {
                remainingOpportunities++;
            }
            double weight = personalWeight(participant.wins, participant.losses);
            for (int index = 0; index < remainingOpportunities; index++) {
                weights.add(weight);
            }
        }
        return weights;
    }

    static double calibratedPersonalOffset(
            double baseProbability,
            List<Double> weights
    ) {
        if (weights.isEmpty() || baseProbability <= 0.0D || baseProbability >= 1.0D) {
            return 0.0D;
        }
        double baseLogit = logit(baseProbability);
        double low = -2.0D;
        double high = 2.0D;
        for (int iteration = 0; iteration < 24; iteration++) {
            double middle = (low + high) / 2.0D;
            double average = weights.stream()
                    .mapToDouble(weight -> sigmoid(baseLogit
                            + PERSONAL_HISTORY_STRENGTH * Math.log(weight)
                            + middle))
                    .average()
                    .orElse(baseProbability);
            if (average > baseProbability) {
                high = middle;
            } else {
                low = middle;
            }
        }
        return (low + high) / 2.0D;
    }

    static double personalWeight(int wins, int losses) {
        double score = Math.clamp(losses - 1.25D * wins, -2.0D, 2.0D);
        return Math.clamp(Math.exp(PERSONAL_ODDS_COEFFICIENT * score),
                MIN_PERSONAL_WEIGHT, MAX_PERSONAL_WEIGHT);
    }

    static double personalizedProbability(
            double baseProbability,
            double weight,
            double calibrationOffset
    ) {
        if (baseProbability <= 0.0D || baseProbability >= 1.0D) {
            return baseProbability;
        }
        double weightedLogOdds = logit(baseProbability)
                + PERSONAL_HISTORY_STRENGTH * Math.log(weight)
                + calibrationOffset;
        return sigmoid(weightedLogOdds);
    }

    private int[] availablePrizeStockForPlayer(
            long nowMillis,
            Set<String> excludedPrizeIds
    ) {
        int[] availableByType = new int[INITIAL_STOCKS.length];
        int total = 0;
        for (int index = 0; index < INITIAL_STOCKS.length; index++) {
            if (!isPrizeTypeEligible(excludedPrizeIds, PRIZE_IDS[index])) {
                continue;
            }
            int awarded = INITIAL_STOCKS[index] - remainingStocks[index];
            int unlocked = releasedForTypeAt(index, nowMillis);
            availableByType[index] = Math.clamp(
                    unlocked - awarded, 0, remainingStocks[index]);
            total += availableByType[index];
        }
        if (total <= 0) {
            int awarded = Arrays.stream(INITIAL_STOCKS).sum() - remainingStock();
            int borrowCapacity = Math.min(Arrays.stream(INITIAL_STOCKS).sum(),
                    releasedStockAt(nowMillis) + RELEASE_BORROW_LIMIT) - awarded;
            if (borrowCapacity <= 0) {
                return availableByType;
            }
            int earliestSlot = Integer.MAX_VALUE;
            for (int index = 0; index < remainingStocks.length; index++) {
                if (remainingStocks[index] <= 0
                        || !isPrizeTypeEligible(excludedPrizeIds, PRIZE_IDS[index])) {
                    continue;
                }
                int awardedForType = INITIAL_STOCKS[index] - remainingStocks[index];
                int nextSlot = releaseSlots[index][awardedForType];
                if (nextSlot < earliestSlot) {
                    Arrays.fill(availableByType, 0);
                    earliestSlot = nextSlot;
                    total = 0;
                }
                if (nextSlot == earliestSlot) {
                    availableByType[index] = 1;
                    total++;
                }
            }
        }
        return availableByType;
    }

    private String selectReleasedPrize(int[] availableByType) {
        int total = Arrays.stream(availableByType).sum();
        if (total <= 0) {
            return NO_PRIZE_ID;
        }
        int prizeIndex = takeAvailablePrize(
                remainingStocks, availableByType, random.nextInt(total));
        return PRIZE_IDS[prizeIndex];
    }

    private int applyDrawToVirtualBag(Participant participant, DrawResult draw) {
        int restored = applyWinningPrizeToVirtualBag(
                participant.virtualBag, draw.prizeId, remainingStocks);
        if (GIFT_PACK_ID.equals(draw.prizeId)) {
            markSupersededDraws(participant, draw);
        }
        return restored;
    }

    static int applyWinningPrizeToVirtualBag(
            List<String> virtualBag,
            String prizeId,
            int[] stocks
    ) {
        if (!isWinningPrize(prizeId)) {
            return 0;
        }
        if (!GIFT_PACK_ID.equals(prizeId)) {
            if (!virtualBag.contains(GIFT_PACK_ID) && !virtualBag.contains(prizeId)) {
                virtualBag.add(prizeId);
            }
            return 0;
        }

        List<String> replacedPrizeIds = virtualBag.stream()
                .filter(existingPrizeId -> !GIFT_PACK_ID.equals(existingPrizeId))
                .toList();
        int restored = restoreWinningPrizeStocks(stocks, replacedPrizeIds);
        virtualBag.clear();
        virtualBag.add(GIFT_PACK_ID);
        return restored;
    }

    static boolean isPrizeTypeEligible(Set<String> virtualBag, String prizeId) {
        return isWinningPrize(prizeId)
                && !virtualBag.contains(GIFT_PACK_ID)
                && !virtualBag.contains(prizeId);
    }

    private void markSupersededDraws(Participant participant, DrawResult giftPackDraw) {
        participant.regularDraws.stream()
                .filter(draw -> draw != giftPackDraw && isWinningPrize(draw.prizeId))
                .forEach(draw -> draw.superseded = true);
        if (participant.bonusDraw != null
                && participant.bonusDraw != giftPackDraw
                && isWinningPrize(participant.bonusDraw.prizeId)) {
            participant.bonusDraw.superseded = true;
        }
    }

    private boolean hasGiftPack(Participant participant) {
        return participant.virtualBag.contains(GIFT_PACK_ID);
    }

    static int takeAvailablePrize(
            int[] remaining,
            int[] available,
            int selected
    ) {
        if (remaining.length != available.length) {
            throw new IllegalArgumentException("Prize stock and availability lengths differ");
        }
        for (int index = 0; index < available.length; index++) {
            if (remaining[index] < 0
                    || available[index] < 0
                    || available[index] > remaining[index]) {
                throw new IllegalArgumentException("Invalid released prize availability");
            }
        }
        int total = Arrays.stream(available).sum();
        if (selected < 0 || selected >= total) {
            throw new IllegalArgumentException("Selected prize offset is out of bounds");
        }
        for (int index = 0; index < available.length; index++) {
            if (selected < available[index]) {
                remaining[index]--;
                return index;
            }
            selected -= available[index];
        }
        throw new IllegalStateException("Released prize selection was out of bounds");
    }

    private int availableReleasedStock(long nowMillis) {
        if (nowMillis < EVENT_START.toEpochMilli()) {
            return 0;
        }
        int totalStock = Arrays.stream(INITIAL_STOCKS).sum();
        return availableReleasedStock(
                totalStock, remainingStock(), releasedStockAt(nowMillis), RELEASE_BORROW_LIMIT);
    }

    static int availableReleasedStock(
            int totalStock,
            int remainingStock,
            int releasedStock,
            int borrowLimit
    ) {
        int awarded = totalStock - remainingStock;
        int releaseLimit = Math.min(totalStock, releasedStock + borrowLimit);
        return Math.clamp(releaseLimit - awarded, 0, remainingStock);
    }

    private int releasedStockAt(long nowMillis) {
        int released = 0;
        for (int index = 0; index < INITIAL_STOCKS.length; index++) {
            released += releasedForTypeAt(index, nowMillis);
        }
        return released;
    }

    private int releasedForTypeAt(int prizeIndex, long nowMillis) {
        if (nowMillis >= EVENT_END_EXCLUSIVE.toEpochMilli()) {
            return INITIAL_STOCKS[prizeIndex];
        }
        if (nowMillis < EVENT_START.toEpochMilli()) {
            return 0;
        }
        int slot = (int) Math.floorDiv(nowMillis - EVENT_START.toEpochMilli(), RELEASE_SLOT_MILLIS);
        return (int) Arrays.stream(releaseSlots[prizeIndex])
                .filter(releaseSlot -> releaseSlot <= slot)
                .count();
    }

    private List<DrawResult> allDrawResults() {
        List<DrawResult> draws = new ArrayList<>();
        for (Participant participant : participants.values()) {
            draws.addAll(allDrawResults(participant.regularDraws, participant.bonusDraw));
        }
        return draws;
    }

    private static List<DrawResult> allDrawResults(
            List<DrawResult> regularDraws,
            DrawResult bonusDraw
    ) {
        List<DrawResult> draws = new ArrayList<>(regularDraws);
        if (bonusDraw != null) {
            draws.add(bonusDraw);
        }
        return draws;
    }

    private void initializeReleasePlan() {
        if (releasePlanSeed == 0L) {
            releasePlanSeed = random.nextLong();
            if (releasePlanSeed == 0L) {
                releasePlanSeed = 1L;
            }
            dataNeedsSave = true;
        }
        // The persisted seed keeps the front-loaded random release plan stable across restarts.
        for (int index = 0; index < INITIAL_STOCKS.length; index++) {
            releaseSlots[index] = generateReleaseSlots(
                    INITIAL_STOCKS[index], releasePlanSeed + 0x9E3779B97F4A7C15L * (index + 1L));
        }
    }

    static int[] generateReleaseSlots(int stock, long seed) {
        int slotCount = (int) Math.ceil(
                (double) Duration.between(EVENT_START, EVENT_END_EXCLUSIVE).toMillis()
                        / RELEASE_SLOT_MILLIS);
        SplittableRandom generator = new SplittableRandom(seed);
        int[] slots = new int[stock];
        for (int index = 0; index < stock; index++) {
            double quantile = (index + 0.15D + 0.70D * generator.nextDouble()) / stock;
            double progress = quantile <= INITIAL_RELEASE_FRACTION
                    ? 0.0D
                    : Math.pow((quantile - INITIAL_RELEASE_FRACTION)
                            / (1.0D - INITIAL_RELEASE_FRACTION), 1.0D / RELEASE_CURVE_EXPONENT);
            double jitter = generator.nextDouble(-0.75D, 0.75D);
            slots[index] = (int) Math.clamp(Math.floor(progress * slotCount + jitter),
                    0.0D, slotCount - 1.0D);
        }
        Arrays.sort(slots);
        return slots;
    }

    static double limitOddsChange(double previous, double candidate, double minimumFactor, double maximumFactor) {
        double previousOdds = previous / (1.0D - previous);
        double candidateOdds = candidate / (1.0D - candidate);
        double limitedOdds = Math.clamp(candidateOdds,
                previousOdds * minimumFactor, previousOdds * maximumFactor);
        return limitedOdds / (1.0D + limitedOdds);
    }

    private static double logit(double probability) {
        return Math.log(probability / (1.0D - probability));
    }

    private static double sigmoid(double value) {
        return 1.0D / (1.0D + Math.exp(-value));
    }

    private void broadcastWinner(String winner, int round, String prizeId) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            viewer.sendMessage(text(viewer, Message.ANNIVERSARY_BROADCAST_WON, NamedTextColor.GOLD,
                    winner, round, prizeName(viewer, prizeId)));
        }
        Bukkit.getConsoleSender().sendMessage(languageService.text(
                Language.DEFAULT,
                Message.ANNIVERSARY_BROADCAST_WON,
                NamedTextColor.GOLD,
                winner,
                round,
                prizeName(Language.DEFAULT, prizeId)));
    }

    private void broadcastBonusWinner(String winner, String prizeId) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            viewer.sendMessage(text(viewer, Message.ANNIVERSARY_BONUS_BROADCAST_WON,
                    NamedTextColor.GOLD, winner, prizeName(viewer, prizeId)));
        }
        Bukkit.getConsoleSender().sendMessage(languageService.text(
                Language.DEFAULT,
                Message.ANNIVERSARY_BONUS_BROADCAST_WON,
                NamedTextColor.GOLD,
                winner,
                prizeName(Language.DEFAULT, prizeId)));
    }

    private void sendParticipantStatus(Player player, Participant participant) {
        player.sendMessage(text(player, Message.ANNIVERSARY_STATUS_TITLE, NamedTextColor.GOLD));
        player.sendMessage(text(player, Message.ANNIVERSARY_STATUS_PROGRESS, NamedTextColor.WHITE,
                participant.regularDraws.size(), MAX_DRAWS, formatDuration(participant.activeMillis)));
        if (!participant.virtualBag.isEmpty()) {
            player.sendMessage(text(player, Message.ANNIVERSARY_STATUS_RESULTS, NamedTextColor.YELLOW,
                    localizedPrizes(player, participant.virtualBag)));
            player.sendMessage(text(player, Message.ANNIVERSARY_BAG_UPDATED, NamedTextColor.AQUA,
                    "/" + COMMAND_NAME + " bag"));
        }
        if (hasGiftPack(participant)) {
            player.sendMessage(text(player, Message.ANNIVERSARY_STATUS_GIFT_PACK_FINAL,
                    NamedTextColor.GOLD));
            return;
        }
        if (participant.bonusGranted) {
            Message bonusStatus = participant.bonusDraw != null
                    ? Message.ANNIVERSARY_BONUS_STATUS_USED
                    : Message.ANNIVERSARY_BONUS_STATUS_READY;
            player.sendMessage(text(player, bonusStatus,
                    participant.bonusDraw != null ? NamedTextColor.GRAY : NamedTextColor.GREEN));
        }
        if (participant.regularDraws.size() >= MAX_DRAWS) {
            player.sendMessage(text(player, Message.ANNIVERSARY_STATUS_COMPLETED, NamedTextColor.GREEN));
            return;
        }
        long required = DRAW_INTERVAL_MILLIS * (participant.regularDraws.size() + 1L);
        long remaining = Math.max(0L, required - participant.activeMillis);
        player.sendMessage(text(player, Message.ANNIVERSARY_STATUS_REMAINING, NamedTextColor.GRAY,
                participant.regularDraws.size() + 1, formatDuration(remaining)));
    }

    private void sendEndedStatus(Player player) {
        Participant participant = participants.get(player.getUniqueId());
        player.sendMessage(text(player, Message.ANNIVERSARY_ENDED, NamedTextColor.YELLOW));
        if (participant != null && !participant.virtualBag.isEmpty()) {
            player.sendMessage(text(player, Message.ANNIVERSARY_ENDED_RESULTS, NamedTextColor.WHITE,
                    localizedPrizes(player, participant.virtualBag)));
        }
    }

    private void tick() {
        Instant now = Instant.now();
        long nowMillis = now.toEpochMilli();
        for (Player player : Bukkit.getOnlinePlayers()) {
            accountTime(player, nowMillis, !afkService.isAfk(player.getUniqueId()));
            updateBossBar(player, now);
        }

        if (dataNeedsSave) {
            saveData();
            dataNeedsSave = false;
        }
        ticksSinceSave++;
        if (ticksSinceSave >= SAVE_INTERVAL_TICKS) {
            ticksSinceSave = 0;
            saveData();
        }
        if (!now.isBefore(EVENT_END_EXCLUSIVE)) {
            saveData();
            if (tickTask != null) {
                tickTask.cancel();
                tickTask = null;
            }
        }
    }

    private void accountTime(Player player, long nowMillis, boolean active) {
        UUID playerId = player.getUniqueId();
        if (isEventAdministrator(playerId)) {
            return;
        }
        long previous = lastAccountedAt.getOrDefault(playerId, nowMillis);
        lastAccountedAt.put(playerId, nowMillis);

        Participant participant = participants.get(playerId);
        if (participant == null || hasGiftPack(participant)
                || !active || participant.activeMillis >= MAX_COUNTED_MILLIS) {
            return;
        }
        long from = Math.max(previous, Math.max(EVENT_START.toEpochMilli(), participant.acceptedAt));
        long until = Math.min(nowMillis, EVENT_END_EXCLUSIVE.toEpochMilli());
        if (until <= from) {
            return;
        }
        participant.activeMillis = Math.min(MAX_COUNTED_MILLIS, participant.activeMillis + until - from);
        participant.lastKnownName = player.getName();
        dataNeedsSave = true;
    }

    private void updateBossBar(Player player, Instant now) {
        if (now.isBefore(EVENT_START) || !now.isBefore(EVENT_END_EXCLUSIVE)) {
            hideBossBar(player);
            return;
        }

        boolean administrator = isEventAdministrator(player.getUniqueId());
        Participant participant = participants.get(player.getUniqueId());
        Component name;
        float progress;
        BossBar.Color color;
        if (administrator) {
            name = text(player, Message.ANNIVERSARY_ADMIN_BOSS, NamedTextColor.GOLD,
                    "/" + COMMAND_NAME + " admin");
            long total = Duration.between(EVENT_START, EVENT_END_EXCLUSIVE).toMillis();
            long remaining = Duration.between(now, EVENT_END_EXCLUSIVE).toMillis();
            progress = progress(remaining, total);
            color = BossBar.Color.BLUE;
        } else if (participant == null) {
            name = text(player, Message.ANNIVERSARY_BOSS_INVITE, NamedTextColor.WHITE,
                    "/" + COMMAND_NAME);
            long total = Duration.between(EVENT_START, EVENT_END_EXCLUSIVE).toMillis();
            long remaining = Duration.between(now, EVENT_END_EXCLUSIVE).toMillis();
            progress = progress(remaining, total);
            color = BossBar.Color.YELLOW;
        } else if (hasGiftPack(participant)) {
            name = text(player, Message.ANNIVERSARY_BOSS_GIFT_PACK_FINAL, NamedTextColor.WHITE,
                    "/" + COMMAND_NAME + " bag");
            progress = 1.0F;
            color = BossBar.Color.PURPLE;
        } else if (regularDrawReady(participant)) {
            int nextRound = participant.regularDraws.size() + 1;
            name = text(player, Message.ANNIVERSARY_BOSS_READY, NamedTextColor.WHITE,
                    nextRound, "/" + COMMAND_NAME);
            progress = 1.0F;
            color = BossBar.Color.GREEN;
        } else if (bonusDrawReady(participant)) {
            name = text(player, Message.ANNIVERSARY_BONUS_BOSS_READY, NamedTextColor.WHITE,
                    "/" + COMMAND_NAME);
            progress = 1.0F;
            color = BossBar.Color.GREEN;
        } else if (participant.regularDraws.size() >= MAX_DRAWS) {
            name = text(player, Message.ANNIVERSARY_BOSS_COMPLETED, NamedTextColor.WHITE);
            progress = 1.0F;
            color = BossBar.Color.PURPLE;
        } else {
            int nextRound = participant.regularDraws.size() + 1;
            long required = DRAW_INTERVAL_MILLIS * nextRound;
            long remaining = Math.max(0L, required - participant.activeMillis);
            name = text(player, Message.ANNIVERSARY_BOSS_PROGRESS, NamedTextColor.WHITE,
                    participant.regularDraws.size(), MAX_DRAWS, nextRound, formatDuration(remaining));
            long phaseProgress = participant.activeMillis
                    - DRAW_INTERVAL_MILLIS * participant.regularDraws.size();
            progress = progress(phaseProgress, DRAW_INTERVAL_MILLIS);
            color = afkService.isAfk(player.getUniqueId()) ? BossBar.Color.WHITE : BossBar.Color.YELLOW;
        }

        BossBar bossBar = bossBars.get(player.getUniqueId());
        if (bossBar == null) {
            bossBar = BossBar.bossBar(name, progress, color, BossBar.Overlay.PROGRESS);
            bossBars.put(player.getUniqueId(), bossBar);
            player.showBossBar(bossBar);
        } else {
            bossBar.name(name);
            bossBar.progress(progress);
            bossBar.color(color);
        }
    }

    private void hideBossBar(Player player) {
        BossBar bossBar = bossBars.remove(player.getUniqueId());
        if (bossBar != null) {
            player.hideBossBar(bossBar);
        }
    }

    private float progress(long value, long maximum) {
        if (maximum <= 0L) {
            return 0.0F;
        }
        return (float) Math.clamp((double) value / maximum, 0.0D, 1.0D);
    }

    private int remainingStock() {
        int total = 0;
        for (int stock : remainingStocks) {
            total += stock;
        }
        return total;
    }

    private String formatDuration(long millis) {
        long seconds = Math.max(0L, (millis + 999L) / 1000L);
        long hours = seconds / 3600L;
        long minutes = seconds % 3600L / 60L;
        long remainingSeconds = seconds % 60L;
        return String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, remainingSeconds);
    }

    private Component rules(Player player) {
        Component rules = Component.empty();
        for (int index = 0; index < RULE_MESSAGES.length; index++) {
            if (index > 0) {
                rules = rules.appendNewline();
            }
            rules = rules.append(text(player, RULE_MESSAGES[index], NamedTextColor.WHITE));
        }
        return rules;
    }

    private Component text(Player player, Message message, NamedTextColor color, Object... args) {
        return languageService.text(player, message, color, args);
    }

    private Component text(CommandSender sender, Message message, NamedTextColor color, Object... args) {
        if (sender instanceof Player player) {
            return text(player, message, color, args);
        }
        return languageService.text(Language.DEFAULT, message, color, args);
    }

    private String adminValue(CommandSender sender, Message message, Object... args) {
        if (sender instanceof Player player) {
            return languageService.t(languageService.language(player), message, args);
        }
        return languageService.t(Language.DEFAULT, message, args);
    }

    private String localizedPrizes(CommandSender sender, List<String> prizeIds) {
        Language language = sender instanceof Player player
                ? languageService.language(player) : Language.DEFAULT;
        String delimiter = switch (language) {
            case ZH_CN, ZH_HK, ZH_TW, LZH, JA_JP -> "、";
            default -> ", ";
        };
        return prizeIds.stream()
                .map(this::normalizePrizeId)
                .map(prizeId -> prizeName(language, prizeId))
                .reduce((left, right) -> left + delimiter + right)
                .orElse("");
    }

    private String prizeName(Player player, String prizeId) {
        return prizeName(languageService.language(player), prizeId);
    }

    private String prizeName(Language language, String prizeId) {
        return languageService.t(language, prizeMessage(normalizePrizeId(prizeId)));
    }

    private String prizeName(CommandSender sender, String prizeId) {
        if (sender instanceof Player player) {
            return prizeName(player, prizeId);
        }
        return prizeName(Language.DEFAULT, prizeId);
    }

    private Message prizeMessage(String prizeId) {
        if (NO_PRIZE_ID.equals(prizeId)) {
            return Message.ANNIVERSARY_PRIZE_NOT_WON;
        }
        if (SOLD_OUT_ID.equals(prizeId)) {
            return Message.ANNIVERSARY_PRIZE_SOLD_OUT;
        }
        for (int index = 0; index < PRIZE_IDS.length; index++) {
            if (PRIZE_IDS[index].equals(prizeId)) {
                return PRIZE_MESSAGES[index];
            }
        }
        return Message.ANNIVERSARY_PRIZE_SOLD_OUT;
    }

    private int prizeIndex(String prizeId) {
        for (int index = 0; index < PRIZE_IDS.length; index++) {
            if (PRIZE_IDS[index].equals(prizeId)) {
                return index;
            }
        }
        throw new IllegalArgumentException("Unknown anniversary prize: " + prizeId);
    }

    private String normalizePrizeId(String storedPrize) {
        return switch (storedPrize) {
            case "Logo 钥匙扣" -> PRIZE_IDS[0];
            case "Logo 马克杯" -> PRIZE_IDS[1];
            case "五周年合照吧唧" -> PRIZE_IDS[2];
            case "五周年大礼包" -> PRIZE_IDS[3];
            case "奖品池已抽完" -> SOLD_OUT_ID;
            default -> storedPrize;
        };
    }

    private static boolean isWinningPrize(String prizeId) {
        return Arrays.asList(PRIZE_IDS).contains(prizeId);
    }

    static List<String> normalizeVirtualBag(List<String> storedPrizeIds) {
        List<String> normalized = storedPrizeIds.stream()
                .filter(FifthAnniversaryEventModule::isWinningPrize)
                .distinct()
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (normalized.contains(GIFT_PACK_ID)) {
            return new ArrayList<>(List.of(GIFT_PACK_ID));
        }
        return normalized;
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private void runOnMain(Audience audience, java.util.function.Consumer<Player> action) {
        if (!(audience instanceof Player player)) {
            return;
        }
        if (Bukkit.isPrimaryThread()) {
            action.accept(player);
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> action.accept(player));
        }
    }

    private void loadData() {
        System.arraycopy(INITIAL_STOCKS, 0, remainingStocks, 0, INITIAL_STOCKS.length);
        auditEntries.clear();
        nextAuditSequence = 1L;
        if (!dataFile.isFile()) {
            initializeReleasePlan();
            return;
        }
        YamlConfiguration data = YamlConfiguration.loadConfiguration(dataFile);
        releasePlanSeed = data.getLong("algorithm.release-plan-seed");
        lastBaseProbability = Math.clamp(data.getDouble("algorithm.last-base-probability"),
                0.0D, MAX_BASE_PROBABILITY);
        lastBaseProbabilityAt = Math.max(0L,
                data.getLong("algorithm.last-base-probability-at"));
        initializeReleasePlan();
        loadAuditEntries(data);
        for (int index = 0; index < PRIZE_IDS.length; index++) {
            remainingStocks[index] = Math.clamp(
                    data.getInt("remaining-prizes." + PRIZE_IDS[index], INITIAL_STOCKS[index]),
                    0,
                    INITIAL_STOCKS[index]);
        }

        ConfigurationSection players = data.getConfigurationSection("players");
        if (players == null) {
            return;
        }
        for (String rawPlayerId : players.getKeys(false)) {
            try {
                UUID playerId = UUID.fromString(rawPlayerId);
                String path = "players." + rawPlayerId + ".";
                String name = data.getString(path + "name", rawPlayerId);
                long acceptedAt = Math.max(EVENT_START.toEpochMilli(), data.getLong(path + "accepted-at"));
                long activeMillis = Math.clamp(
                        data.getLong(path + "active-millis"), 0L, MAX_COUNTED_MILLIS);
                boolean bonusGranted = data.getBoolean(path + "bonus-granted");
                String bonusGrantedBy = data.getString(path + "bonus-granted-by");
                UUID bonusGrantedById = parseUuid(data.getString(path + "bonus-granted-by-uuid"));
                long bonusGrantedAt = Math.max(0L, data.getLong(path + "bonus-granted-at"));
                List<String> virtualBag = normalizeVirtualBag(
                        data.getStringList(path + "virtual-bag").stream()
                                .map(this::normalizePrizeId)
                                .toList());
                List<DrawResult> regularDraws = loadRegularDraws(data, path);
                DrawResult bonusDraw = loadDraw(data, path + "bonus-draw", 0);
                int wins = data.contains(path + "wins")
                        ? Math.max(0, data.getInt(path + "wins"))
                        : (int) allDrawResults(regularDraws, bonusDraw).stream()
                                .filter(draw -> isWinningPrize(draw.prizeId)).count();
                int losses = data.contains(path + "losses")
                        ? Math.max(0, data.getInt(path + "losses"))
                        : Math.max(0, regularDraws.size() + (bonusDraw == null ? 0 : 1) - wins);
                participants.put(playerId,
                        new Participant(name, acceptedAt, activeMillis, virtualBag,
                                regularDraws, bonusDraw, wins, losses,
                                bonusGranted, bonusGrantedBy, bonusGrantedById,
                                bonusGrantedAt));
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Ignoring invalid anniversary participant UUID: " + rawPlayerId);
            }
        }
    }

    private void loadAuditEntries(YamlConfiguration data) {
        ConfigurationSection section = data.getConfigurationSection("audit");
        if (section == null) {
            return;
        }
        for (String rawSequence : section.getKeys(false)) {
            long sequence;
            try {
                sequence = Long.parseLong(rawSequence);
            } catch (NumberFormatException exception) {
                continue;
            }
            String path = "audit." + rawSequence + ".";
            long occurredAt = Math.max(0L, data.getLong(path + "occurred-at"));
            String action = data.getString(path + "action", "unknown");
            UUID actorId = parseUuid(data.getString(path + "actor-uuid"));
            UUID targetId = parseUuid(data.getString(path + "target-uuid"));
            auditEntries.add(new AuditEntry(sequence, occurredAt, action,
                    actorId, data.getString(path + "actor-name"), targetId,
                    data.getString(path + "target-name"), data.getString(path + "detail")));
            nextAuditSequence = Math.max(nextAuditSequence, sequence + 1L);
        }
        auditEntries.sort(Comparator.comparingLong(entry -> entry.sequence));
    }

    private boolean removeEventAdministratorParticipants() {
        List<Map.Entry<UUID, Participant>> administrators = participants.entrySet().stream()
                .filter(entry -> isEventAdministrator(entry.getKey()))
                .toList();
        if (administrators.isEmpty()) {
            return false;
        }
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Participant> entry : administrators) {
            Participant participant = entry.getValue();
            int restoredPrizes = restoreWinningPrizeStocks(
                    remainingStocks, participant.virtualBag);
            appendAudit(AuditAction.ADMIN_PARTICIPANT_REMOVED, now, null, null,
                    entry.getKey(), participant.lastKnownName,
                    Integer.toString(restoredPrizes));
            participants.remove(entry.getKey());
            lastAccountedAt.remove(entry.getKey());
            plugin.getLogger().warning("Removed event administrator from anniversary participation: "
                    + entry.getKey());
        }
        return true;
    }

    static int restoreWinningPrizeStocks(int[] stocks, List<String> prizeIds) {
        if (stocks.length != INITIAL_STOCKS.length) {
            throw new IllegalArgumentException("Unexpected anniversary stock array length");
        }
        int restored = 0;
        for (String prizeId : prizeIds) {
            for (int index = 0; index < PRIZE_IDS.length; index++) {
                if (PRIZE_IDS[index].equals(prizeId)
                        && stocks[index] < INITIAL_STOCKS[index]) {
                    stocks[index]++;
                    restored++;
                    break;
                }
            }
        }
        return restored;
    }

    private void appendAudit(
            String action,
            long occurredAt,
            UUID actorId,
            String actorName,
            UUID targetId,
            String targetName,
            String detail
    ) {
        auditEntries.add(new AuditEntry(nextAuditSequence++, occurredAt, action,
                actorId, actorName, targetId, targetName, detail));
        if (auditEntries.size() > 500) {
            auditEntries.subList(0, auditEntries.size() - 500).clear();
        }
        dataNeedsSave = true;
    }

    private List<DrawResult> loadRegularDraws(YamlConfiguration data, String playerPath) {
        List<DrawResult> draws = new ArrayList<>();
        ConfigurationSection section = data.getConfigurationSection(playerPath + "regular-draws");
        if (section == null) {
            return draws;
        }
        section.getKeys(false).stream()
                .mapToInt(key -> {
                    try {
                        return Integer.parseInt(key);
                    } catch (NumberFormatException exception) {
                        return -1;
                    }
                })
                .filter(round -> round >= 1 && round <= MAX_DRAWS)
                .sorted()
                .mapToObj(round -> loadDraw(data,
                        playerPath + "regular-draws." + round, round))
                .filter(java.util.Objects::nonNull)
                .forEach(draws::add);
        return draws;
    }

    private DrawResult loadDraw(
            YamlConfiguration data,
            String path,
            int round
    ) {
        if (!data.isConfigurationSection(path)) {
            return null;
        }
        long drawnAt = Math.max(EVENT_START.toEpochMilli(), data.getLong(path + ".drawn-at"));
        String prizeId = normalizePrizeId(data.getString(path + ".prize", NO_PRIZE_ID));
        double probability = Math.clamp(data.getDouble(path + ".probability"), 0.0D, 1.0D);
        double roll = Math.clamp(data.getDouble(path + ".roll"), 0.0D, 1.0D);
        int version = Math.max(0, data.getInt(path + ".algorithm-version"));
        boolean superseded = data.getBoolean(path + ".superseded");
        return new DrawResult(round, drawnAt, prizeId, probability, roll, version, superseded);
    }

    private void saveData() {
        File parent = dataFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            plugin.getLogger().warning("Could not create plugin data directory for anniversary event");
            return;
        }

        YamlConfiguration data = new YamlConfiguration();
        data.set("event-time", EVENT_TIME_TEXT);
        data.set("algorithm.version", ALGORITHM_VERSION);
        data.set("algorithm.release-plan-seed", releasePlanSeed);
        data.set("algorithm.last-base-probability", lastBaseProbability);
        data.set("algorithm.last-base-probability-at", lastBaseProbabilityAt);
        for (int index = 0; index < PRIZE_IDS.length; index++) {
            data.set("remaining-prizes." + PRIZE_IDS[index], remainingStocks[index]);
        }
        for (Map.Entry<UUID, Participant> entry : participants.entrySet()) {
            String path = "players." + entry.getKey() + ".";
            Participant participant = entry.getValue();
            data.set(path + "name", participant.lastKnownName);
            data.set(path + "accepted-at", participant.acceptedAt);
            data.set(path + "active-millis", participant.activeMillis);
            data.set(path + "virtual-bag", participant.virtualBag);
            data.set(path + "wins", participant.wins);
            data.set(path + "losses", participant.losses);
            for (DrawResult draw : participant.regularDraws) {
                saveDraw(data, path + "regular-draws." + draw.round, draw);
            }
            data.set(path + "bonus-granted", participant.bonusGranted);
            data.set(path + "bonus-granted-by", participant.bonusGrantedBy);
            data.set(path + "bonus-granted-by-uuid",
                    participant.bonusGrantedById == null
                            ? null : participant.bonusGrantedById.toString());
            data.set(path + "bonus-granted-at", participant.bonusGrantedAt);
            if (participant.bonusDraw != null) {
                saveDraw(data, path + "bonus-draw", participant.bonusDraw);
            }
        }
        for (AuditEntry entry : auditEntries) {
            String path = "audit." + entry.sequence + ".";
            data.set(path + "occurred-at", entry.occurredAt);
            data.set(path + "action", entry.action);
            data.set(path + "actor-uuid", entry.actorId == null ? null : entry.actorId.toString());
            data.set(path + "actor-name", entry.actorName);
            data.set(path + "target-uuid", entry.targetId == null ? null : entry.targetId.toString());
            data.set(path + "target-name", entry.targetName);
            data.set(path + "detail", entry.detail);
        }
        try {
            data.save(dataFile);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not save fifth anniversary event data", exception);
        }
    }

    private void saveDraw(YamlConfiguration data, String path, DrawResult draw) {
        data.set(path + ".drawn-at", draw.drawnAt);
        data.set(path + ".prize", draw.prizeId);
        data.set(path + ".probability", draw.probability);
        data.set(path + ".roll", draw.roll);
        data.set(path + ".algorithm-version", draw.algorithmVersion);
        data.set(path + ".superseded", draw.superseded);
    }

    private static final class Participant {

        private String lastKnownName;
        private final long acceptedAt;
        private long activeMillis;
        private final List<String> virtualBag;
        private final List<DrawResult> regularDraws;
        private DrawResult bonusDraw;
        private int wins;
        private int losses;
        private boolean bonusGranted;
        private String bonusGrantedBy;
        private UUID bonusGrantedById;
        private long bonusGrantedAt;

        private Participant(
                String lastKnownName,
                long acceptedAt,
                long activeMillis,
                List<String> virtualBag,
                List<DrawResult> regularDraws,
                DrawResult bonusDraw,
                int wins,
                int losses,
                boolean bonusGranted,
                String bonusGrantedBy,
                UUID bonusGrantedById,
                long bonusGrantedAt
        ) {
            this.lastKnownName = lastKnownName;
            this.acceptedAt = acceptedAt;
            this.activeMillis = activeMillis;
            this.virtualBag = virtualBag;
            this.regularDraws = regularDraws;
            this.bonusDraw = bonusDraw;
            this.wins = wins;
            this.losses = losses;
            this.bonusGranted = bonusGranted;
            this.bonusGrantedBy = bonusGrantedBy;
            this.bonusGrantedById = bonusGrantedById;
            this.bonusGrantedAt = bonusGrantedAt;
        }
    }

    private static final class AuditAction {

        private static final String BONUS_GRANT = "bonus-grant";
        private static final String ADMIN_PARTICIPANT_REMOVED = "admin-participant-removed";

        private AuditAction() {
        }
    }

    private static final class VirtualBagHolder implements InventoryHolder {

        private final UUID playerId;
        private Inventory inventory;

        private VirtualBagHolder(UUID playerId) {
            this.playerId = playerId;
        }

        private void attach(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class WinnerEntry {

        private final UUID playerId;
        private final String playerName;
        private final DrawResult draw;

        private WinnerEntry(UUID playerId, String playerName, DrawResult draw) {
            this.playerId = playerId;
            this.playerName = playerName;
            this.draw = draw;
        }
    }

    private static final class AuditEntry {

        private final long sequence;
        private final long occurredAt;
        private final String action;
        private final UUID actorId;
        private final String actorName;
        private final UUID targetId;
        private final String targetName;
        private final String detail;

        private AuditEntry(
                long sequence,
                long occurredAt,
                String action,
                UUID actorId,
                String actorName,
                UUID targetId,
                String targetName,
                String detail
        ) {
            this.sequence = sequence;
            this.occurredAt = occurredAt;
            this.action = action;
            this.actorId = actorId;
            this.actorName = actorName;
            this.targetId = targetId;
            this.targetName = targetName;
            this.detail = detail;
        }
    }

    static final class ControllerSnapshot {

        private final int remainingStock;
        private final int availableStock;
        private final int releaseInWindow;
        private final double opportunitiesPerHour;
        private final double remainingHours;
        private final int knownFutureOpportunities;

        ControllerSnapshot(
                int remainingStock,
                int availableStock,
                int releaseInWindow,
                double opportunitiesPerHour,
                double remainingHours,
                int knownFutureOpportunities
        ) {
            this.remainingStock = remainingStock;
            this.availableStock = availableStock;
            this.releaseInWindow = releaseInWindow;
            this.opportunitiesPerHour = opportunitiesPerHour;
            this.remainingHours = remainingHours;
            this.knownFutureOpportunities = knownFutureOpportunities;
        }
    }

    private static final class DrawResult {

        private final int round;
        private final long drawnAt;
        private final String prizeId;
        private final double probability;
        private final double roll;
        private final int algorithmVersion;
        private boolean superseded;

        private DrawResult(
                int round,
                long drawnAt,
                String prizeId,
                double probability,
                double roll,
                int algorithmVersion,
                boolean superseded
        ) {
            this.round = round;
            this.drawnAt = drawnAt;
            this.prizeId = prizeId;
            this.probability = probability;
            this.roll = roll;
            this.algorithmVersion = algorithmVersion;
            this.superseded = superseded;
        }
    }
}
