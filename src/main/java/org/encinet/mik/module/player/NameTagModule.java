package org.encinet.mik.module.player;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.PrefixNode;
import net.luckperms.api.node.types.SuffixNode;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.util.NameMetaRenderer;
import org.encinet.mik.util.PlayerDisplay;

import java.util.List;
import java.util.function.Predicate;

/**
 * NameTagModule — 处理 /nametag 命令，允许玩家自定义前缀和后缀。
 *
 * <p>需要权限：{@code group.member}
 *
 * <p>允许的 MiniMessage 标签：color, decoration, gradient, rainbow, reset,
 * hover, insertion, font, keybind, translatable, score；newline 仅在 hover 文本中生效。
 * 禁用：click, selector。
 */
public class NameTagModule {

    private static final int CUSTOM_PRIORITY = 10_000;
    private static final int MAX_LENGTH = 200;
    private static final String PERM_USE = "group.member";

    private static final String URL_EDITOR = "https://webui.advntr.dev/";
    private static final String URL_DOCS = "https://docs.papermc.io/adventure/minimessage/format/";

    private static final TextColor C_ACCENT = TextColor.color(0xFFAA00);
    private static final TextColor C_LINK = TextColor.color(0x55AAFF);
    private static final TextColor C_RAW = TextColor.color(0xFFFF55);
    private static final TextColor C_DIM = NamedTextColor.GRAY;
    private static final TextColor C_MUTED = NamedTextColor.GRAY;

    private static final Component NL = Component.newline();

    private final JavaPlugin plugin;
    private final LanguageService languageService;
    private LuckPerms luckPerms;

    public NameTagModule(JavaPlugin plugin, LanguageService languageService) {
        this.plugin = plugin;
        this.languageService = languageService;
    }

    public void enable() {
        RegisteredServiceProvider<LuckPerms> provider =
                Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        if (provider == null) {
            plugin.getLogger().warning("LuckPerms 未找到，NameTagModule 已禁用。");
            return;
        }
        luckPerms = provider.getProvider();
        plugin.getLogger().info("NameTagModule 已启动。");
    }

    public void registerCommands(LifecycleEventManager<Plugin> manager) {
        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();
            commands.register(
                    Commands.literal("nametag")
                            .executes(ctx -> cmdOverview(ctx.getSource().getSender()))
                            .then(Commands.literal("view")
                                    .executes(ctx -> cmdOverview(ctx.getSource().getSender())))
                            .then(Commands.literal("clear")
                                    .executes(ctx -> cmdClearAll(requirePlayer(ctx.getSource().getSender()))))
                            .then(Commands.literal("prefix")
                                    .executes(ctx -> cmdViewOne(ctx.getSource().getSender(), NameTag.PREFIX))
                                    .then(Commands.literal("set")
                                            .then(Commands.argument("content", StringArgumentType.greedyString())
                                                    .executes(ctx -> cmdSet(
                                                            requirePlayer(ctx.getSource().getSender()),
                                                            NameTag.PREFIX,
                                                            StringArgumentType.getString(ctx, "content")))))
                                    .then(Commands.literal("clear")
                                            .executes(ctx -> cmdClear(
                                                    requirePlayer(ctx.getSource().getSender()), NameTag.PREFIX))))
                            .then(Commands.literal("suffix")
                                    .executes(ctx -> cmdViewOne(ctx.getSource().getSender(), NameTag.SUFFIX))
                                    .then(Commands.literal("set")
                                            .then(Commands.argument("content", StringArgumentType.greedyString())
                                                    .executes(ctx -> cmdSet(
                                                            requirePlayer(ctx.getSource().getSender()),
                                                            NameTag.SUFFIX,
                                                            StringArgumentType.getString(ctx, "content")))))
                                    .then(Commands.literal("clear")
                                            .executes(ctx -> cmdClear(
                                                    requirePlayer(ctx.getSource().getSender()), NameTag.SUFFIX))))
                            .build(),
                    languageService.t(Language.DEFAULT, Message.NAMETAG_COMMAND_DESCRIPTION),
                    List.of("nametag")
            );
        });
    }

    /**
     * 确保发送者是玩家；不是则发送提示并返回 null。
     */
    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player p) return p;
        sender.sendMessage(Component.text(languageService.t(Language.DEFAULT, Message.PLAYER_ONLY), NamedTextColor.RED));
        return null;
    }

    /**
     * 校验玩家是否拥有 {@value PERM_USE} 权限。
     * 无权限时向玩家发送提示并返回 false。
     */
    private boolean checkPerm(Player player) {
        if (player == null) return false;
        if (player.hasPermission(PERM_USE)) return true;
        player.sendMessage(languageService.text(player, Message.NAMETAG_NO_PERMISSION, NamedTextColor.RED));
        return false;
    }

    /**
     * 将 MiniMessage 原文渲染为 Component，空值显示灰色斜体占位符。
     */
    private Component render(Player player, String raw) {
        if (raw == null || raw.isEmpty())
            return Component.text(languageService.t(player, Message.NAMETAG_UNSET), C_DIM, TextDecoration.ITALIC);
        return NameMetaRenderer.deserialize(player, raw);
    }

    /**
     * 渲染原始字符串，后附可点击的 [复制] 按钮。
     */
    private Component rawWithCopy(Player player, String raw) {
        if (raw == null || raw.isEmpty()) return Component.empty();
        return Component.text()
                .append(Component.text(raw, C_RAW))
                .append(Component.text(languageService.t(player, Message.NAMETAG_COPY_LABEL), C_DIM)
                        .clickEvent(ClickEvent.copyToClipboard(raw))
                        .hoverEvent(HoverEvent.showText(
                                Component.text(languageService.t(player, Message.NAMETAG_COPY_HOVER), C_MUTED))))
                .build();
    }

    /**
     * 生成可点击的外链按钮。
     */
    private Component linkButton(String label, String url) {
        return Component.text(label, C_LINK, TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.openUrl(url))
                .hoverEvent(HoverEvent.showText(Component.text(url, C_DIM)));
    }

    /**
     * 聊天预览行。
     */
    private Component chatPreview(Player player, String prefixRaw, String suffixRaw) {
        Component pre = (prefixRaw != null && !prefixRaw.isEmpty())
                ? NameMetaRenderer.deserialize(player, prefixRaw) : Component.empty();
        Component suf = (suffixRaw != null && !suffixRaw.isEmpty())
                ? NameMetaRenderer.deserialize(player, suffixRaw) : Component.empty();

        return Component.text()
                .append(pre)
                .append(PlayerDisplay.name(player, NamedTextColor.WHITE))
                .append(suf)
                .append(Component.text(" » ", NamedTextColor.GOLD))
                .append(Component.text(languageService.t(player, Message.NAMETAG_SAMPLE_MESSAGE), NamedTextColor.WHITE))
                .build();
    }

    /**
     * 标签条目：第一行显示渲染效果，若有原文则换行缩进显示原文和复制按钮。
     *
     * <pre>
     * 前缀  &lt;rendered&gt;
     *   &lt;raw&gt; [复制]
     * </pre>
     */
    private Component tagEntry(Player player, Message labelMessage, String raw) {
        String label = languageService.t(player, labelMessage);
        var builder = Component.text()
                .append(Component.text(label + "  ", C_MUTED))
                .append(render(player, raw));
        if (raw != null && !raw.isEmpty()) {
            builder.append(NL)
                    .append(Component.text("      ", C_DIM))   // indent
                    .append(rawWithCopy(player, raw));
        }
        return builder.build();
    }

    /**
     * /nametag  /nametag view
     */
    private int cmdOverview(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (!checkPerm(player) || luckPerms == null) return Command.SINGLE_SUCCESS;

        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) {
            player.sendMessage(languageService.text(player, Message.NAMETAG_USER_DATA_LOAD_FAILED, NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }

        CachedMetaData meta = user.getCachedData().getMetaData();
        String pre = NameTag.PREFIX.get(meta);
        String suf = NameTag.SUFFIX.get(meta);

        player.sendMessage(Component.text()
                .append(Component.text("🏷 " + languageService.t(player, Message.NAMETAG_TITLE), C_ACCENT, TextDecoration.BOLD)).append(NL)
                .append(tagEntry(player, NameTag.PREFIX.label(), pre)).append(NL)
                .append(tagEntry(player, NameTag.SUFFIX.label(), suf)).append(NL)
                .append(Component.text(languageService.t(player, Message.NAMETAG_PREVIEW_LABEL), C_MUTED)).append(chatPreview(player, pre, suf)).append(NL)
                .append(linkButton(languageService.t(player, Message.NAMETAG_ONLINE_EDITOR), URL_EDITOR))
                .append(Component.text("  ·  ", C_DIM))
                .append(linkButton(languageService.t(player, Message.NAMETAG_FORMAT_DOCS), URL_DOCS))
                .build());

        return Command.SINGLE_SUCCESS;
    }

    /**
     * /nametag prefix  /nametag suffix
     */
    private int cmdViewOne(CommandSender sender, NameTag target) {
        Player player = requirePlayer(sender);
        if (!checkPerm(player) || luckPerms == null) return Command.SINGLE_SUCCESS;

        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) {
            player.sendMessage(languageService.text(player, Message.NAMETAG_USER_DATA_LOAD_FAILED, NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }

        CachedMetaData meta = user.getCachedData().getMetaData();
        String targetRaw = target.get(meta);
        String otherRaw = target.other().get(meta);
        String pre = target == NameTag.PREFIX ? targetRaw : otherRaw;
        String suf = target == NameTag.SUFFIX ? targetRaw : otherRaw;

        String targetLabel = targetLabel(player, target);
        player.sendMessage(Component.text()
                .append(Component.text("🏷 " + targetLabel, C_ACCENT, TextDecoration.BOLD)).append(NL)
                .append(tagEntry(player, target.label(), targetRaw)).append(NL)
                .append(Component.text(languageService.t(player, Message.NAMETAG_PREVIEW_LABEL), C_MUTED)).append(chatPreview(player, pre, suf)).append(NL)
                .append(linkButton(languageService.t(player, Message.NAMETAG_ONLINE_EDITOR), URL_EDITOR))
                .append(Component.text("  ·  ", C_DIM))
                .append(linkButton(languageService.t(player, Message.NAMETAG_FORMAT_DOCS), URL_DOCS))
                .build());

        return Command.SINGLE_SUCCESS;
    }

    /**
     * /nametag prefix set <content>  /nametag suffix set <content>
     */
    private int cmdSet(Player player, NameTag target, String content) {
        if (!checkPerm(player) || luckPerms == null) return Command.SINGLE_SUCCESS;

        String normalized = content.trim();
        if (normalized.isEmpty()) {
            player.sendMessage(Component.text()
                    .append(Component.text(languageService.t(player, Message.NAMETAG_EMPTY_CONTENT), NamedTextColor.RED))
                    .append(Component.text("/nametag " + target.id + " clear", C_LINK)
                            .clickEvent(ClickEvent.suggestCommand("/nametag " + target.id + " clear"))
                            .hoverEvent(HoverEvent.showText(Component.text(
                                    languageService.t(player, Message.NAMETAG_CLICK_FILL_COMMAND), C_MUTED))))
                    .build());
            return Command.SINGLE_SUCCESS;
        }
        if (normalized.length() > MAX_LENGTH) {
            player.sendMessage(languageService.text(player, Message.NAMETAG_TOO_LONG,
                    NamedTextColor.RED, targetLabel(player, target), MAX_LENGTH));
            return Command.SINGLE_SUCCESS;
        }
        String unsupportedPlaceholder = NameMetaRenderer.findUnsupportedPlaceholder(normalized);
        if (unsupportedPlaceholder != null) {
            player.sendMessage(Component.text()
                    .append(Component.text(languageService.t(player, Message.NAMETAG_UNSUPPORTED_PLACEHOLDER_BEFORE), NamedTextColor.RED))
                    .append(Component.text("%player_...%", C_LINK))
                    .append(Component.text(languageService.t(player, Message.NAMETAG_UNSUPPORTED_PLACEHOLDER_AFTER), NamedTextColor.RED))
                    .append(Component.text("%" + unsupportedPlaceholder + "%", C_RAW))
                    .build());
            return Command.SINGLE_SUCCESS;
        }

        User cached = luckPerms.getUserManager().getUser(player.getUniqueId());
        String otherRaw = cached != null ? target.other().get(cached.getCachedData().getMetaData()) : null;

        luckPerms.getUserManager().modifyUser(player.getUniqueId(), user -> {
            target.clearOwned(user);
            target.addNode(user, normalized);
        }).whenComplete((v, err) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (err != null) {
                plugin.getLogger().warning("设置 " + target.id + " 失败（" + player.getName() + "）: " + err.getMessage());
                player.sendMessage(languageService.text(player, Message.NAMETAG_SET_ERROR,
                        NamedTextColor.RED, targetLabel(player, target)));
                return;
            }

            String pre = target == NameTag.PREFIX ? normalized : otherRaw;
            String suf = target == NameTag.SUFFIX ? normalized : otherRaw;

            player.sendMessage(Component.text()
                    .append(Component.text(languageService.t(player, Message.NAMETAG_SET_SUCCESS,
                            targetLabel(player, target)), NamedTextColor.GREEN, TextDecoration.BOLD)).append(NL)
                    .append(Component.text(languageService.t(player, Message.NAMETAG_EFFECT_LABEL), C_MUTED)).append(render(player, normalized)).append(NL)
                    .append(Component.text("      ", C_DIM)).append(rawWithCopy(player, normalized)).append(NL)
                    .append(Component.text(languageService.t(player, Message.NAMETAG_PREVIEW_LABEL), C_MUTED)).append(chatPreview(player, pre, suf))
                    .build());
        }));

        return Command.SINGLE_SUCCESS;
    }

    private String targetLabel(Player player, NameTag target) {
        return languageService.t(player, target.label());
    }

    /**
     * /nametag prefix clear  /nametag suffix clear
     */
    private int cmdClear(Player player, NameTag target) {
        if (!checkPerm(player) || luckPerms == null) return Command.SINGLE_SUCCESS;

        luckPerms.getUserManager().modifyUser(player.getUniqueId(), target::clearOwned)
                .whenComplete((v, err) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (err != null) {
                        plugin.getLogger().warning("清除 " + target.id + " 失败（" + player.getName() + "）");
                        player.sendMessage(languageService.text(player, Message.NAMETAG_CLEAR_ERROR,
                                NamedTextColor.RED, targetLabel(player, target)));
                        return;
                    }
                    player.sendMessage(languageService.text(player, Message.NAMETAG_CLEAR_SUCCESS,
                            NamedTextColor.GREEN, targetLabel(player, target)));
                }));

        return Command.SINGLE_SUCCESS;
    }

    /**
     * /nametag clear
     */
    private int cmdClearAll(Player player) {
        if (!checkPerm(player) || luckPerms == null) return Command.SINGLE_SUCCESS;

        luckPerms.getUserManager().modifyUser(player.getUniqueId(), user -> {
            NameTag.PREFIX.clearOwned(user);
            NameTag.SUFFIX.clearOwned(user);
        }).whenComplete((v, err) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (err != null) {
                plugin.getLogger().warning("清除所有标签失败（" + player.getName() + "）");
                player.sendMessage(languageService.text(player, Message.NAMETAG_CLEAR_ALL_ERROR, NamedTextColor.RED));
                return;
            }
            player.sendMessage(languageService.text(player, Message.NAMETAG_CLEAR_ALL_SUCCESS, NamedTextColor.GREEN));
        }));

        return Command.SINGLE_SUCCESS;
    }

    private enum NameTag {
        PREFIX("prefix", Message.NAMETAG_PREFIX_LABEL) {
            @Override
            public String get(CachedMetaData m) {
                return m.getPrefix();
            }

            @Override
            public Predicate<net.luckperms.api.node.Node> ownedPredicate() {
                return NodeType.PREFIX.predicate(n -> n.getPriority() == CUSTOM_PRIORITY);
            }

            @Override
            public void addNode(User u, String v) {
                u.data().add(PrefixNode.builder(v, CUSTOM_PRIORITY).build());
            }

            @Override
            public NameTag other() {
                return SUFFIX;
            }
        },
        SUFFIX("suffix", Message.NAMETAG_SUFFIX_LABEL) {
            @Override
            public String get(CachedMetaData m) {
                return m.getSuffix();
            }

            @Override
            public Predicate<net.luckperms.api.node.Node> ownedPredicate() {
                return NodeType.SUFFIX.predicate(n -> n.getPriority() == CUSTOM_PRIORITY);
            }

            @Override
            public void addNode(User u, String v) {
                u.data().add(SuffixNode.builder(v, CUSTOM_PRIORITY).build());
            }

            @Override
            public NameTag other() {
                return PREFIX;
            }
        };

        final String id;
        final Message label;

        NameTag(String id, Message label) {
            this.id = id;
            this.label = label;
        }

        Message label() {
            return label;
        }

        public abstract String get(CachedMetaData meta);

        public abstract Predicate<net.luckperms.api.node.Node> ownedPredicate();

        public abstract void addNode(User user, String value);

        public abstract NameTag other();

        public void clearOwned(User user) {
            user.data().clear(ownedPredicate());
        }
    }
}
