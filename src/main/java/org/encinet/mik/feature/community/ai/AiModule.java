package org.encinet.mik.feature.community.ai;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.encinet.mik.feature.community.ai.game.GameToolCalls;
import org.encinet.mik.feature.community.ai.game.GameToolCatalog;
import org.encinet.mik.feature.community.ai.turn.AiTurns;
import org.encinet.mik.integration.geyser.BedrockPlayerDetector;
import org.encinet.mik.integration.mikd.RequestPort;
import org.encinet.mik.protocol.AssistantToolCall;
import org.encinet.mik.protocol.AssistantToolResult;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;

import java.util.function.LongSupplier;
import java.util.function.Predicate;

public final class AiModule implements Listener {
    private final Plugin plugin;
    private final LanguageService languages;
    private final AiTurns turns;
    private final GameToolCalls gameTools;
    private BukkitTask poller;

    public AiModule(Plugin plugin, LanguageService languages, RequestPort requests,
                    BedrockPlayerDetector bedrock, Predicate<Player> authorized, GameToolCatalog tools) {
        this(plugin, languages, requests, bedrock, authorized, tools, System::nanoTime);
    }

    AiModule(Plugin plugin, LanguageService languages, RequestPort requests,
             BedrockPlayerDetector bedrock, Predicate<Player> authorized, GameToolCatalog tools, LongSupplier nanoTime) {
        this.plugin = plugin;
        this.languages = languages;
        this.turns = new AiTurns(languages, requests, bedrock, authorized, tools, nanoTime, plugin.getLogger());
        this.gameTools = new GameToolCalls(plugin, tools, turns::toolViewer);
    }

    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        poller = Bukkit.getScheduler().runTaskTimer(plugin, turns::poll, 10, 10);
    }

    public void disable() {
        HandlerList.unregisterAll(this);
        if (poller != null) poller.cancel();
        poller = null;
        turns.close();
    }

    public void registerCommands(LifecycleEventManager<Plugin> lifecycle) {
        lifecycle.registerEventHandler(LifecycleEvents.COMMANDS, event -> event.registrar().register(
                buildCommand(), languages.t(Language.DEFAULT, Message.AI_COMMAND_DESCRIPTION)));
    }

    LiteralCommandNode<CommandSourceStack> buildCommand() {
        return Commands.literal("ai")
                .requires(source -> source.getSender() instanceof Player player && AiTurns.permitted(player))
                .executes(context -> {
                    var player = (Player) context.getSource().getSender();
                    player.sendMessage(languages.text(languages.language(player), Message.AI_USAGE, NamedTextColor.YELLOW));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("cancel")
                        .executes(context -> turns.cancel((Player) context.getSource().getSender()))
                        // Only the bare word is reserved; longer text is still a prompt.
                        .then(prompt("cancel ")))
                .then(prompt(""))
                .build();
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> prompt(String prefix) {
        return Commands.argument("prompt", StringArgumentType.greedyString()).executes(context ->
                turns.ask((Player) context.getSource().getSender(),
                        (prefix + StringArgumentType.getString(context, "prompt")).strip()));
    }

    public AssistantToolResult executeTool(AssistantToolCall call, int remainingMs) throws Exception {
        return gameTools.execute(call, remainingMs);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        turns.quit(event.getPlayer());
    }
}
