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
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
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

import java.util.List;
import java.util.function.Predicate;

/**
 * NameTagModule — 处理 /nametag 命令，允许玩家自定义前缀和后缀。
 *
 * <p>需要权限：{@code group.member}
 *
 * <p>允许的 MiniMessage 标签：color, decoration, gradient, rainbow, reset,
 * hover, insertion, font, keybind, translatable, newline, score；
 * 禁用：click, selector。
 */
public class NameTagModule {

    private static final int CUSTOM_PRIORITY = 10_000;
    private static final int MAX_LENGTH = 200;
    private static final String PERM_USE = "group.member";

    private static final String URL_EDITOR = "https://webui.advntr.dev/";
    private static final String URL_DOCS = "https://docs.papermc.io/adventure/minimessage/format/";

    // ── 颜色常量 ──────────────────────────────────────────────────────────────

    private static final TextColor C_ACCENT = TextColor.color(0xFFAA00);
    private static final TextColor C_LINK = TextColor.color(0x55AAFF);
    private static final TextColor C_RAW = TextColor.color(0xFFFF55);
    private static final TextColor C_DIM = NamedTextColor.DARK_GRAY;
    private static final TextColor C_MUTED = NamedTextColor.GRAY;

    // ── 安全 MiniMessage（禁用 click / selector）──────────────────────────────

    private static final MiniMessage SAFE_MM = MiniMessage.builder()
            .tags(TagResolver.resolver(
                    StandardTags.color(),
                    StandardTags.decorations(),
                    StandardTags.gradient(),
                    StandardTags.rainbow(),
                    StandardTags.reset(),
                    StandardTags.hoverEvent(),
                    StandardTags.insertion(),
                    StandardTags.font(),
                    StandardTags.keybind(),
                    StandardTags.translatable(),
                    StandardTags.newline(),
                    StandardTags.score()
            ))
            .build();

    private static final Component NL = Component.newline();

    // ── 状态 ─────────────────────────────────────────────────────────────────

    private final JavaPlugin plugin;
    private LuckPerms luckPerms;

    public NameTagModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // ── 生命周期 ──────────────────────────────────────────────────────────────

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
                    "自定义名称标签",
                    List.of("nametag")
            );
        });
    }

    // ── 权限 & 校验工具 ───────────────────────────────────────────────────────

    /**
     * 确保发送者是玩家；不是则发送提示并返回 null。
     */
    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player p) return p;
        sender.sendMessage(SAFE_MM.deserialize("<red>该命令只能由玩家执行</red>"));
        return null;
    }

    /**
     * 校验玩家是否拥有 {@value PERM_USE} 权限。
     * 无权限时向玩家发送提示并返回 false。
     */
    private boolean checkPerm(Player player) {
        if (player == null) return false;
        if (player.hasPermission(PERM_USE)) return true;
        player.sendMessage(SAFE_MM.deserialize("<red>你没有使用该命令的权限</red>"));
        return false;
    }

    // ── 渲染工具 ──────────────────────────────────────────────────────────────

    /**
     * 将 MiniMessage 原文渲染为 Component，空值显示灰色斜体占位符。
     */
    private Component render(String raw) {
        if (raw == null || raw.isEmpty())
            return Component.text("(未设置)", C_DIM, TextDecoration.ITALIC);
        return SAFE_MM.deserialize(raw);
    }

    /**
     * 渲染原始字符串，后附可点击的 [复制] 按钮。
     */
    private Component rawWithCopy(String raw) {
        if (raw == null || raw.isEmpty()) return Component.empty();
        return Component.text()
                .append(Component.text(raw, C_RAW))
                .append(Component.text(" [复制]", C_DIM)
                        .clickEvent(ClickEvent.copyToClipboard(raw))
                        .hoverEvent(HoverEvent.showText(
                                Component.text("复制 MiniMessage 原文到剪贴板", C_MUTED))))
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
    private Component chatPreview(String name, String prefixRaw, String suffixRaw) {
        Component pre = (prefixRaw != null && !prefixRaw.isEmpty())
                ? SAFE_MM.deserialize(prefixRaw) : Component.empty();
        Component suf = (suffixRaw != null && !suffixRaw.isEmpty())
                ? SAFE_MM.deserialize(suffixRaw) : Component.empty();

        return Component.text()
                .append(pre)
                .append(Component.text(name))
                .append(suf)
                .append(Component.text(" » ", NamedTextColor.GOLD))
                .append(Component.text("示例消息", NamedTextColor.WHITE))
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
    private Component tagEntry(String label, String raw) {
        var builder = Component.text()
                .append(Component.text(label + "  ", C_MUTED))
                .append(render(raw));
        if (raw != null && !raw.isEmpty()) {
            builder.append(NL)
                    .append(Component.text("      ", C_DIM))   // indent
                    .append(rawWithCopy(raw));
        }
        return builder.build();
    }

    // ── 命令处理器 ────────────────────────────────────────────────────────────

    /**
     * /nametag  /nametag view
     */
    private int cmdOverview(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (!checkPerm(player) || luckPerms == null) return Command.SINGLE_SUCCESS;

        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) {
            player.sendMessage(SAFE_MM.deserialize("<red>无法加载用户数据，请稍后重试</red>"));
            return Command.SINGLE_SUCCESS;
        }

        CachedMetaData meta = user.getCachedData().getMetaData();
        String pre = NameTag.PREFIX.get(meta);
        String suf = NameTag.SUFFIX.get(meta);

        player.sendMessage(Component.text()
                .append(Component.text("🏷 名称标签", C_ACCENT, TextDecoration.BOLD)).append(NL)
                .append(tagEntry("前缀", pre)).append(NL)
                .append(tagEntry("后缀", suf)).append(NL)
                .append(Component.text("预览  ", C_MUTED)).append(chatPreview(player.getName(), pre, suf)).append(NL)
                .append(linkButton("在线编辑器", URL_EDITOR))
                .append(Component.text("  ·  ", C_DIM))
                .append(linkButton("格式文档", URL_DOCS))
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
            player.sendMessage(SAFE_MM.deserialize("<red>无法加载用户数据，请稍后重试</red>"));
            return Command.SINGLE_SUCCESS;
        }

        CachedMetaData meta = user.getCachedData().getMetaData();
        String targetRaw = target.get(meta);
        String otherRaw = target.other().get(meta);
        String pre = target == NameTag.PREFIX ? targetRaw : otherRaw;
        String suf = target == NameTag.SUFFIX ? targetRaw : otherRaw;

        player.sendMessage(Component.text()
                .append(Component.text("🏷 " + target.label, C_ACCENT, TextDecoration.BOLD)).append(NL)
                .append(tagEntry(target.label, targetRaw)).append(NL)
                .append(Component.text("预览  ", C_MUTED)).append(chatPreview(player.getName(), pre, suf)).append(NL)
                .append(linkButton("在线编辑器", URL_EDITOR))
                .append(Component.text("  ·  ", C_DIM))
                .append(linkButton("格式文档", URL_DOCS))
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
                    .append(Component.text("内容不能为空，如需清除请使用 ", NamedTextColor.RED))
                    .append(Component.text("/nametag " + target.id + " clear", C_LINK)
                            .clickEvent(ClickEvent.suggestCommand("/nametag " + target.id + " clear"))
                            .hoverEvent(HoverEvent.showText(Component.text("点击填入命令", C_MUTED))))
                    .build());
            return Command.SINGLE_SUCCESS;
        }
        if (normalized.length() > MAX_LENGTH) {
            player.sendMessage(Component.text(
                    target.label + "过长，最多 " + MAX_LENGTH + " 个字符", NamedTextColor.RED));
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
                player.sendMessage(Component.text("设置" + target.label + "时发生错误，请稍后重试", NamedTextColor.RED));
                return;
            }

            String pre = target == NameTag.PREFIX ? normalized : otherRaw;
            String suf = target == NameTag.SUFFIX ? normalized : otherRaw;

            player.sendMessage(Component.text()
                    .append(Component.text("✔ " + target.label + "已设置", NamedTextColor.GREEN, TextDecoration.BOLD)).append(NL)
                    .append(Component.text("效果  ", C_MUTED)).append(render(normalized)).append(NL)
                    .append(Component.text("      ", C_DIM)).append(rawWithCopy(normalized)).append(NL)
                    .append(Component.text("预览  ", C_MUTED)).append(chatPreview(player.getName(), pre, suf))
                    .build());
        }));

        return Command.SINGLE_SUCCESS;
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
                        player.sendMessage(Component.text("清除" + target.label + "时发生错误", NamedTextColor.RED));
                        return;
                    }
                    player.sendMessage(Component.text(
                            "✔ 已清除你的" + target.label, NamedTextColor.GREEN));
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
                player.sendMessage(Component.text("清除时发生错误，请稍后重试", NamedTextColor.RED));
                return;
            }
            player.sendMessage(Component.text("✔ 已清除所有名称标签", NamedTextColor.GREEN));
        }));

        return Command.SINGLE_SUCCESS;
    }

    // ── NameTag 枚举 ──────────────────────────────────────────────────────────

    private enum NameTag {
        PREFIX("prefix", "前缀") {
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
        SUFFIX("suffix", "后缀") {
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
        final String label;

        NameTag(String id, String label) {
            this.id = id;
            this.label = label;
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
