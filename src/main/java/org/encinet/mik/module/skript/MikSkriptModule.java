package org.encinet.mik.module.skript;

import ch.njol.skript.Skript;
import ch.njol.skript.ScriptLoader;
import ch.njol.skript.expressions.base.PropertyExpression;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.Mik;
import org.encinet.mik.module.afk.AfkModule;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.player.ClientVersionReminderModule;
import org.encinet.mik.module.player.PlayerRole;
import org.encinet.mik.module.pvp.PvpModule;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import java.util.Objects;

/** Registers MIK's player state API with Skript when it is installed. */
public final class MikSkriptModule {

    public static final String ADDON_NAME = "MIK";
    public static final String UNKNOWN_CLIENT_VERSION = "unknown";

    private final JavaPlugin plugin;
    private final MikSkriptFacade facade;
    private final SkriptInfoCommand infoCommand;
    private final ScriptLoader.ScriptUnloadEvent unloadListener;

    public MikSkriptModule(JavaPlugin plugin, LanguageService languageService,
                           ClientVersionReminderModule clientVersionModule,
                           AfkModule afkModule, PvpModule pvpModule) {
        this(plugin, new MikSkriptFacade(
                plugin,
                player -> languageService.language(player).id(),
                player -> clientVersionName(clientVersionModule, player),
                player -> PlayerRole.resolve(player).id(),
                afkModule,
                pvpModule), new SkriptInfoCommand(plugin, languageService));
    }

    MikSkriptModule(JavaPlugin plugin, MikSkriptFacade facade) {
        this(plugin, facade, null);
    }

    private MikSkriptModule(JavaPlugin plugin, MikSkriptFacade facade, SkriptInfoCommand infoCommand) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.facade = Objects.requireNonNull(facade, "facade");
        this.infoCommand = infoCommand;
        this.unloadListener = (parser, script) ->
                facade.clearPvpOverridesOwnedBy(MikSkriptOwner.of(script));
    }

    public void registerCommands(LifecycleEventManager<Plugin> manager) {
        if (infoCommand != null) {
            infoCommand.registerCommands(manager);
        }
    }

    public void enable() {
        Plugin skriptPlugin = Bukkit.getPluginManager().getPlugin("Skript");
        if (skriptPlugin == null || !skriptPlugin.isEnabled()) {
            plugin.getLogger().info("Skript not found; MIK Skript expressions are disabled.");
            return;
        }
        register(Skript.instance(), facade);
        ScriptLoader.eventRegistry().register(ScriptLoader.ScriptUnloadEvent.class, unloadListener);
        plugin.getLogger().info("Registered MIK player state API with Skript.");
    }

    public void disable() {
        ScriptLoader.eventRegistry().unregister(unloadListener);
    }

    static void register(org.skriptlang.skript.Skript skript, MikSkriptFacade facade) {
        SkriptAddon addon = skript.registerAddon(Mik.class, ADDON_NAME);
        SyntaxRegistry registry = addon.syntaxRegistry();
        registry.register(SyntaxRegistry.EXPRESSION,
                SyntaxInfo.Expression.builder(MikPlayerPropertyExpression.class, String.class)
                        .supplier(() -> new MikPlayerPropertyExpression(facade))
                        .priority(PropertyExpression.DEFAULT_PRIORITY)
                        .addPatterns(
                                "[the] mik language of %players%",
                                "%players%'[s] mik language",
                                "[the] mik client version of %players%",
                                "%players%'[s] mik client version",
                                "[the] mik role of %players%",
                                "%players%'[s] mik role",
                                "[the] mik afk message of %players%",
                                "%players%'[s] mik afk message",
                                "[the] mik afk source of %players%",
                                "%players%'[s] mik afk source",
                                "[the] mik pvp override id of %players%",
                                "%players%'[s] mik pvp override id",
                                "[the] mik pvp override owner of %players%",
                                "%players%'[s] mik pvp override owner"
                        )
                        .build());
        registry.register(SyntaxRegistry.EXPRESSION,
                SyntaxInfo.Expression.builder(MikPlayerBooleanExpression.class, Boolean.class)
                        .supplier(() -> new MikPlayerBooleanExpression(facade))
                        .priority(PropertyExpression.DEFAULT_PRIORITY)
                        .addPatterns(
                                "[the] mik afk state of %players%",
                                "%players%'[s] mik afk state",
                                "[the] mik pvp state of %players%",
                                "%players%'[s] mik pvp state",
                                "[the] mik pvp preference of %players%",
                                "%players%'[s] mik pvp preference",
                                "[the] mik pvp overridden state of %players%",
                                "%players%'[s] mik pvp overridden state",
                                "[the] mik pvp override value of %players%",
                                "%players%'[s] mik pvp override value",
                                "[the] mik combat tagged state of %players%",
                                "%players%'[s] mik combat tagged state"
                        )
                        .build());
        registry.register(SyntaxRegistry.EXPRESSION,
                SyntaxInfo.Expression.builder(MikPlayerTimespanExpression.class, ch.njol.skript.util.Timespan.class)
                        .supplier(() -> new MikPlayerTimespanExpression(facade))
                        .priority(PropertyExpression.DEFAULT_PRIORITY)
                        .addPatterns(
                                "[the] mik afk duration of %players%",
                                "%players%'[s] mik afk duration",
                                "[the] mik pvp override remaining time of %players%",
                                "%players%'[s] mik pvp override remaining time",
                                "[the] mik combat tag remaining time of %players%",
                                "%players%'[s] mik combat tag remaining time"
                        )
                        .build());
        registry.register(SyntaxRegistry.EXPRESSION,
                SyntaxInfo.Expression.builder(MikPvpOverridePriorityExpression.class, Number.class)
                        .supplier(() -> new MikPvpOverridePriorityExpression(facade))
                        .priority(PropertyExpression.DEFAULT_PRIORITY)
                        .addPatterns(
                                "[the] mik pvp override priority of %players%",
                                "%players%'[s] mik pvp override priority"
                        )
                        .build());
        registry.register(SyntaxRegistry.EXPRESSION,
                SyntaxInfo.Expression.builder(MikPvpOverrideIdsExpression.class, String.class)
                        .supplier(() -> new MikPvpOverrideIdsExpression(facade))
                        .priority(PropertyExpression.DEFAULT_PRIORITY)
                        .addPatterns(
                                "[the] mik pvp override ids of %players%",
                                "%players%'[s] mik pvp override ids"
                        )
                        .build());

        registry.register(SyntaxRegistry.EFFECT, SyntaxInfo.builder(MikAfkSetEffect.class)
                .supplier(() -> new MikAfkSetEffect(facade))
                .addPattern("set %players% to mik afk [with [the] message %-string%] [silent:silently]")
                .build());
        registry.register(SyntaxRegistry.EFFECT, SyntaxInfo.builder(MikAfkClearEffect.class)
                .supplier(() -> new MikAfkClearEffect(facade))
                .addPattern("(clear|remove) [the] mik afk state (of|from) %players% [silent:silently]")
                .build());
        registry.register(SyntaxRegistry.EFFECT, SyntaxInfo.builder(MikPvpPreferenceEffect.class)
                .supplier(() -> new MikPvpPreferenceEffect(facade))
                .addPattern("set [the] mik pvp preference of %players% to %boolean%")
                .build());
        registry.register(SyntaxRegistry.EFFECT, SyntaxInfo.builder(MikPvpOverrideSetEffect.class)
                .supplier(() -> new MikPvpOverrideSetEffect(facade))
                .addPatterns(
                        "set [the] mik pvp override %string% of %players% to %boolean% [with priority %-number%] [for %-timespan%]",
                        "force [the] mik pvp (on:on|off:off) for %players% (using|with) [override] [id] %string% [with priority %-number%] [for %-timespan%]"
                )
                .build());
        registry.register(SyntaxRegistry.EFFECT, SyntaxInfo.builder(MikPvpOverrideClearEffect.class)
                .supplier(() -> new MikPvpOverrideClearEffect(facade))
                .addPatterns(
                        "clear [the] mik pvp override %string% (of|for|from) %players%",
                        "clear all [of the] mik pvp overrides (of|for|from) %players%"
                )
                .build());

        registry.register(SyntaxRegistry.CONDITION, SyntaxInfo.builder(MikPlayerStateCondition.class)
                .supplier(() -> new MikPlayerStateCondition(facade))
                .priority(PropertyExpression.DEFAULT_PRIORITY)
                .addPatterns(
                        "%players% (is|are) mik afk",
                        "%players% (isn't|is not|aren't|are not) mik afk",
                        "%players% (has|have) mik pvp enabled",
                        "%players% (doesn't|does not|do not|don't) have mik pvp enabled",
                        "%players% (is|are) mik pvp overridden",
                        "%players% (isn't|is not|aren't|are not) mik pvp overridden",
                        "%players% (is|are) mik combat tagged",
                        "%players% (isn't|is not|aren't|are not) mik combat tagged"
                )
                .build());
        registry.register(SyntaxRegistry.CONDITION, SyntaxInfo.builder(MikPvpOverrideCondition.class)
                .supplier(() -> new MikPvpOverrideCondition(facade))
                .priority(PropertyExpression.DEFAULT_PRIORITY)
                .addPatterns(
                        "%players% (has|have) [the] mik pvp override %string%",
                        "%players% (doesn't|does not|do not|don't) have [the] mik pvp override %string%"
                )
                .build());
    }

    static String clientVersionName(ClientVersionReminderModule module, Player player) {
        if (module == null) {
            return UNKNOWN_CLIENT_VERSION;
        }

        String version = module.clientVersionName(player);
        return version == null || version.isBlank() || version.equalsIgnoreCase(UNKNOWN_CLIENT_VERSION)
                ? UNKNOWN_CLIENT_VERSION : version;
    }
}
