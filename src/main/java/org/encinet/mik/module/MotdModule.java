package org.encinet.mik.module;

import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.util.GeoUtil;
import org.encinet.mik.util.MotdCenterUtil;

import java.net.InetAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class MotdModule implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final long EASTER_EGG_WINDOW_MS = 30_000L;
    private static final long DEBOUNCE_MS = 500L;
    private static final int EASTER_EGG_THRESHOLD = 2;

    private static final Component LINE1_CN = MM.deserialize(
            MotdCenterUtil.center("<gold>米<white>客 <gray>| <green>26.1<gray> | <gold>创意<white>休闲服")
    );
    private static final Component LINE1_EN = MM.deserialize(
            MotdCenterUtil.center("<gold>Mi<white>k  <gray>| <green>26.1<gray> | <gold>Creative<white> Casual")
    );

    private static final String[] NORMAL_LINE2_CN = {
            "<gradient:#5e4fa2:#f79459>建造 · 摸鱼 · 音乐 · 快乐 · AFK",
            "<white><bold>推荐安装</bold> <green><bold>Plasmo Voice</bold> <white><bold>语音模组",
            "<gradient:#89f7fe:#66a6ff>✦</gradient> <white>身临其境 <gradient:#ee9ca7:#ffdde1><bold>ViveCraft</bold></gradient> <white>模组已支持 <gradient:#66a6ff:#89f7fe>✦</gradient>",
            "<gradient:#ee9ca7:#ffdde1>需要帮助？</gradient> <white>点击进入官网 <gradient:#89f7fe:#66a6ff><underlined>mik.noctiro.moe</underlined></gradient>"
    };

    private static final String[] NORMAL_LINE2_EN = {
            "<gradient:#5e4fa2:#f79459>Build · Chill · Music · Fun · AFK",
            "<white><bold>Recommended to install</bold> <green><bold>Plasmo Voice</bold> <white><bold>Mod",
            "<gradient:#89f7fe:#66a6ff>✦</gradient> <white>Experience <gradient:#ee9ca7:#ffdde1><bold>ViveCraft</bold></gradient> <white>In Reality <gradient:#66a6ff:#89f7fe>✦</gradient>",
            "<gradient:#ee9ca7:#ffdde1>Need Help?</gradient> <white>Visit Website <gradient:#89f7fe:#66a6ff><underlined>mik.noctiro.moe</underlined></gradient>"
    };

    private static final String[][] EGG_BRANCHES_CN = {
            {
                    "<gradient:#66edff:#66ffb2>Ping?</gradient>",
                    "<gradient:#ff9a9e:#fad0c4>Pong!</gradient>",
                    "<white><bold>检测到</bold><yellow>高频</yellow><white>刷新...</white>",
                    "<aqua>你在练习手速吗？</aqua>",
                    "<gradient:#a18cd1:#fbc2eb>别再 Ping 了，直接连接吧</gradient>"
            },
            {
                    "<gradient:#84fab0:#8fd3f4>在考虑要不要加入？</gradient>",
                    "<white>没事的，服务器<green>很友好</green> <white>:)</white>",
                    "<gold>慢慢来</gold>，<white>门为你开着</white>",
                    "<gradient:#ffecd2:#fcb69f>真的，进来看看就好</gradient>",
                    "<gradient:#f6d365:#fda085>等候多时了 ～</gradient>",
            },
            {
                    "<white>Ping！</white>",
                    "<yellow>再 Ping！</yellow>",
                    "<gold>又 Ping！</gold>",
                    "<gradient:#ff0080:#ff8c00>...你是在逗我吗 owo</gradient>",
                    "<bold><gradient:#42e695:#3bb2b8>行吧，进来玩吧 XD</gradient></bold>",
            },
            {
                    "<gray>正在加载精彩内容...</gray>",
                    "<white>其实这段 MOTD <yellow>是人写的</yellow></white>",
                    "<gradient:#43e97b:#38f9d7>写它的人希望你加入</gradient>",
                    "<aqua>（也许有那么一点无聊）</aqua>",
                    "<gradient:#fa709a:#fee140><bold>但服务器是真的好玩！</bold></gradient>",
            },
            {
                    "<white>休息一下</white>",
                    "<gradient:#fbc2eb:#a6c1ee>不用想太多</gradient>",
                    "<white>就是随便盖盖东西</white>",
                    "<gradient:#f6d365:#fda085>或者到处走走</gradient>",
                    "<white>这就够了 <green>:)</green></white>",
            },
            {
                    "<white>手边有吃的吗</white>",
                    "<gradient:#f9d423:#f7b733>有的话很完美</gradient>",
                    "<white>没有的话</white><yellow>...</yellow><white>也行</white>",
                    "<gradient:#fbc2eb:#a6c1ee>开玩吧</gradient>",
                    "<white>薯片掉键盘了不关我们的事 <gray>:)</gray></white>",
            },
    };

    private static final String[][] EGG_BRANCHES_EN = {
            {
                    "<gradient:#66edff:#66ffb2>Ping?</gradient>",
                    "<gradient:#ff9a9e:#fad0c4>Pong!</gradient>",
                    "<white><bold>High speed</bold> <yellow>detected...</yellow>",
                    "<aqua>Practicing your clicking speed?</aqua>",
                    "<gradient:#a18cd1:#fbc2eb>Stop pinging, just join already</gradient>"
            },
            {
                    "<gradient:#84fab0:#8fd3f4>Still deciding whether to join?</gradient>",
                    "<white>It's a <green>friendly</green> place, promise :)</white>",
                    "<gold>Take your time</gold>, <white>door's open</white>",
                    "<gradient:#ffecd2:#fcb69f>Seriously, come take a look</gradient>",
                    "<gradient:#f6d365:#fda085>We'll be here ~</gradient>",
            },
            {
                    "<white>Ping!</white>",
                    "<yellow>Ping again!</yellow>",
                    "<orange>Another ping!</orange>",
                    "<gradient:#ff0080:#ff8c00>...are you testing me? owo</gradient>",
                    "<bold><gradient:#42e695:#3bb2b8>Fine, just come play XD</gradient></bold>",
            },
            {
                    "<gray>Loading interesting content...</gray>",
                    "<white>Fun fact: <yellow>a human wrote this MOTD</yellow></white>",
                    "<gradient:#43e97b:#38f9d7>They really do want you to join</gradient>",
                    "<aqua>(May have been slightly bored at the time)</aqua>",
                    "<gradient:#fa709a:#fee140><bold>The server though? Genuinely fun.</bold></gradient>",
            },
            {
                    "<white>Taking a break?</white>",
                    "<gradient:#fbc2eb:#a6c1ee>No need to think too hard</gradient>",
                    "<white>Just build something random</white>",
                    "<gradient:#f6d365:#fda085>Or wander around</gradient>",
                    "<white>That's enough <green>:)</green></white>",
            },
            {
                    "<white>Got snacks?</white>",
                    "<gradient:#f9d423:#f7b733>Perfect setup if so</gradient>",
                    "<white>No snacks?</white> <yellow>...</yellow> <white>still fine</white>",
                    "<gradient:#fbc2eb:#a6c1ee>Let's get into it</gradient>",
                    "<white>Crumbs on the keyboard are your problem <gray>:)</gray></white>",
            },
    };

    private static final Component[] NORMAL_MOTDS_CN = buildMotds(LINE1_CN, NORMAL_LINE2_CN);
    private static final Component[] NORMAL_MOTDS_EN = buildMotds(LINE1_EN, NORMAL_LINE2_EN);
    private static final Component[][] EGG_MOTDS_CN = buildEggMotds(LINE1_CN, EGG_BRANCHES_CN);
    private static final Component[][] EGG_MOTDS_EN = buildEggMotds(LINE1_EN, EGG_BRANCHES_EN);

    private static Component[] buildMotds(Component line1, String[] line2s) {
        Component[] motds = new Component[line2s.length];
        for (int i = 0; i < line2s.length; i++) {
            String centeredLine2 = MotdCenterUtil.center(line2s[i]);
            motds[i] = line1.append(Component.newline()).append(MM.deserialize(centeredLine2));
        }
        return motds;
    }

    private static Component[][] buildEggMotds(Component line1, String[][] branches) {
        Component[][] result = new Component[branches.length][];
        for (int b = 0; b < branches.length; b++) {
            result[b] = buildMotds(line1, branches[b]);
        }
        return result;
    }

    private record PingRecord(int count, long lastPingAt, int eggBranch) {}

    private final ConcurrentHashMap<String, PingRecord> pingTracker = new ConcurrentHashMap<>();
    private final JavaPlugin plugin;

    public MotdModule(JavaPlugin plugin) { this.plugin = plugin; }

    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::cleanup, 1200L, 1200L);
    }

    public void disable() { pingTracker.clear(); }

    @EventHandler
    public void onPing(PaperServerListPingEvent event) {
        event.setMaxPlayers(2026);
        event.getListedPlayers().clear();

        InetAddress address = event.getAddress();

        boolean isCN = GeoUtil.isChinaIp(address);
        Component[] normals = isCN ? NORMAL_MOTDS_CN : NORMAL_MOTDS_EN;
        Component[][] eggs = isCN ? EGG_MOTDS_CN : EGG_MOTDS_EN;

        event.motd(resolveMotd(address.getHostAddress(), normals, eggs));
    }

    private Component resolveMotd(String ip, Component[] normals, Component[][] eggs) {
        long now = System.currentTimeMillis();
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        PingRecord record = pingTracker.compute(ip, (k, prev) -> {
            if (prev == null || now - prev.lastPingAt() > EASTER_EGG_WINDOW_MS) {
                return new PingRecord(1, now, rng.nextInt(eggs.length));
            }
            // 极短时间内的 Ping 视为同一次，不增加计数
            if (now - prev.lastPingAt() < DEBOUNCE_MS) {
                return new PingRecord(prev.count(), now, prev.eggBranch());
            }
            return new PingRecord(prev.count() + 1, now, prev.eggBranch());
        });

        int eggIndex = record.count() - EASTER_EGG_THRESHOLD - 1;
        Component[] branch = eggs[record.eggBranch()];

        // 未达阈值或彩蛋序列已结束 显示普通 MOTD 并重置
        if (record.count() <= EASTER_EGG_THRESHOLD || eggIndex >= branch.length) {
            if (eggIndex >= branch.length) pingTracker.remove(ip); // 序列结束，清除记录
            return normals[rng.nextInt(normals.length)];
        }

        return branch[eggIndex];
    }

    private void cleanup() {
        long cutoff = System.currentTimeMillis() - EASTER_EGG_WINDOW_MS;
        pingTracker.entrySet().removeIf(e -> e.getValue().lastPingAt() < cutoff);
    }
}