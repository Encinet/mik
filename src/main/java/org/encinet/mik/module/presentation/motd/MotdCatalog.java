package org.encinet.mik.module.presentation.motd;

import org.encinet.mik.module.i18n.Language;

public final class MotdCatalog {

    private static final MotdProfileSpec SIMPLIFIED_CHINESE = new MotdProfileSpec(
            "<gold>米<white>客",
            "<gold>创意<white>休闲服",
            new String[]{
                    "<gradient:#5e4fa2:#f79459>建造 · 摸鱼 · 音乐 · 快乐 · AFK",
                    "<white><bold>推荐安装</bold> <green><bold>Plasmo Voice</bold> <white><bold>语音模组",
                    "<gradient:#89f7fe:#66a6ff>✦</gradient> <white>身临其境 <gradient:#ee9ca7:#ffdde1><bold>ViveCraft</bold></gradient> <white>模组已支持 <gradient:#66a6ff:#89f7fe>✦</gradient>",
                    "<gradient:#ee9ca7:#ffdde1>需要帮助？</gradient> <white>访问官网 <gradient:#89f7fe:#66a6ff><underlined>mcmik.top</underlined></gradient>",
            },
            new String[][]{
                    {
                            "<gradient:#66edff:#66ffb2>Ping?</gradient>",
                            "<gradient:#ff9a9e:#fad0c4>Pong!</gradient>",
                            "<white><bold>检测到</bold><yellow>高频</yellow><white>刷新...</white>",
                            "<aqua>你在练习手速吗？</aqua>",
                            "<gradient:#a18cd1:#fbc2eb>别再 Ping 了，直接连接吧</gradient>",
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
                    {"<gradient:#ff9a9e:#fad0c4><bold>我没有说过这句话。</bold></gradient> <gray>—— 鲁迅</gray>"},
            },
            new String[]{
                    "<gradient:#ffd89b:#19547b>大家都在认真挂机中...</gradient>",
                    "<gray>服务器正在进行集体静默测试(AFK)</gray>",
            },
            new String[]{
                    "<gradient:#7f7fd5:#86a8e7:#91eae4>夜已深</gradient><white>，记得</white><gradient:#fbc2eb:#a6c1ee>早点睡觉哦</gradient>",
                    "<gradient:#f6d365:#fda085>真晚啊</gradient><white>，记得</white><gradient:#84fab0:#8fd3f4>休息哦</gradient>",
            },
            new String[]{
                    "<white>欢迎回来，</white><gradient:#9bd8d0:#b9d7f0>{player}</gradient>",
                    "<white>又见面了，</white><gradient:#d6c6f2:#b7d9ea>{player}</gradient><white>，今天也轻松一点</white>",
                    "<gradient:#b8dfd8:#d7e8c8>{player}</gradient><white>，欢迎回到这片小世界</white>",
                    "<white>看到你回来啦，</white><gradient:#f0c9c2:#d6d4f0>{player}</gradient><white>，祝你玩得开心</white>",
                    "<gradient:#d7e7c6:#b8d8e8>{player}</gradient><white>，今天想从哪里开始？</white>",
            }
    );

    private static final MotdProfileSpec ENGLISH = new MotdProfileSpec(
            "<gold>Mi<white>k",
            "<gold>Creative<white> Casual",
            new String[]{
                    "<gradient:#5e4fa2:#f79459>Build · Chill · Music · Fun · AFK",
                    "<white><bold>Recommended to install</bold> <green><bold>Plasmo Voice</bold> <white><bold>Mod",
                    "<gradient:#89f7fe:#66a6ff>✦</gradient> <white>Experience <gradient:#ee9ca7:#ffdde1><bold>ViveCraft</bold></gradient> <white>In Reality <gradient:#66a6ff:#89f7fe>✦</gradient>",
                    "<gradient:#ee9ca7:#ffdde1>Need Help?</gradient> <white>Visit Website <gradient:#89f7fe:#66a6ff><underlined>mcmik.top</underlined></gradient>",
            },
            new String[][]{
                    {
                            "<gradient:#66edff:#66ffb2>Ping?</gradient>",
                            "<gradient:#ff9a9e:#fad0c4>Pong!</gradient>",
                            "<white><bold>High speed</bold> <yellow>detected...</yellow>",
                            "<aqua>Practicing your clicking speed?</aqua>",
                            "<gradient:#a18cd1:#fbc2eb>Stop pinging, just join already</gradient>",
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
                            "<gold>Another ping!</gold>",
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
            },
            new String[]{
                    "<gradient:#ffd89b:#19547b>Everyone is totally working hard</gradient>",
                    "<gray>The server is in collective AFK mode</gray>",
            },
            new String[]{
                    "<gradient:#7f7fd5:#86a8e7:#91eae4>It's getting late</gradient><white>, remember to </white><gradient:#fbc2eb:#a6c1ee>sleep well</gradient>",
                    "<gradient:#f6d365:#fda085>Still awake?</gradient><white> Remember to </white><gradient:#84fab0:#8fd3f4>take a rest</gradient>",
            },
            new String[]{
                    "<white>Welcome back, </white><gradient:#9bd8d0:#b9d7f0>{player}</gradient>",
                    "<white>Good to see you again, </white><gradient:#d6c6f2:#b7d9ea>{player}</gradient><white>. Settle in.</white>",
                    "<gradient:#b8dfd8:#d7e8c8>{player}</gradient><white>, welcome back to this little world.</white>",
                    "<white>Nice to have you back, </white><gradient:#f0c9c2:#d6d4f0>{player}</gradient><white>. Have a good time.</white>",
                    "<gradient:#d7e7c6:#b8d8e8>{player}</gradient><white>, where would you like to start?</white>",
            }
    );

    private static final String[][] TRADITIONAL_EGGS = {
            {
                    "<gradient:#66edff:#66ffb2>Ping？</gradient>",
                    "<gradient:#ff9a9e:#fad0c4>Pong！</gradient>",
                    "<white><bold>偵測到</bold><yellow>高頻</yellow><white>重新整理...</white>",
                    "<aqua>你在練習手速嗎？</aqua>",
                    "<gradient:#a18cd1:#fbc2eb>別再 Ping 了，直接連線吧</gradient>",
            },
            {
                    "<gradient:#84fab0:#8fd3f4>還在考慮要不要加入？</gradient>",
                    "<white>放心，這裡<green>很友善</green> <white>:)</white>",
                    "<gold>慢慢來</gold>，<white>門一直開著</white>",
                    "<gradient:#ffecd2:#fcb69f>真的，進來看看就好</gradient>",
                    "<gradient:#f6d365:#fda085>等你很久了～</gradient>",
            },
            {
                    "<white>Ping！</white>",
                    "<yellow>再 Ping！</yellow>",
                    "<gold>又 Ping！</gold>",
                    "<gradient:#ff0080:#ff8c00>…你是在逗我嗎 owo</gradient>",
                    "<bold><gradient:#42e695:#3bb2b8>好啦，進來玩吧 XD</gradient></bold>",
            },
            {
                    "<gray>正在載入精彩內容...</gray>",
                    "<white>其實這段 MOTD <yellow>是人寫的</yellow></white>",
                    "<gradient:#43e97b:#38f9d7>寫它的人希望你加入</gradient>",
                    "<aqua>（也許當時有點無聊）</aqua>",
                    "<gradient:#fa709a:#fee140><bold>但伺服器真的很好玩！</bold></gradient>",
            },
    };

    private static final String[] TRADITIONAL_AFK = {
            "<gradient:#ffd89b:#19547b>眾人都在認真掛機中...</gradient>",
            "<gray>伺服器正在進行集體靜默測試（AFK）</gray>",
    };

    private static final String[] TRADITIONAL_NIGHT = {
            "<gradient:#7f7fd5:#86a8e7:#91eae4>夜已深</gradient><white>，記得</white><gradient:#fbc2eb:#a6c1ee>早點休息</gradient>",
            "<gradient:#f6d365:#fda085>這麼晚了</gradient><white>，也該</white><gradient:#84fab0:#8fd3f4>睡覺囉</gradient>",
    };

    private static final String[] TRADITIONAL_KNOWN_PLAYER = {
            "<white>歡迎回來，</white><gradient:#9bd8d0:#b9d7f0>{player}</gradient>",
            "<white>又見面了，</white><gradient:#d6c6f2:#b7d9ea>{player}</gradient><white>，今天也放輕鬆吧</white>",
            "<gradient:#b8dfd8:#d7e8c8>{player}</gradient><white>，歡迎回到這個小世界</white>",
            "<white>看到你回來了，</white><gradient:#f0c9c2:#d6d4f0>{player}</gradient><white>，祝你玩得開心</white>",
    };

    private static final MotdProfileSpec HONG_KONG = traditional(
            "<gold>創意<white>休閒服",
            "<white><bold>推薦安裝</bold> <green><bold>Plasmo Voice</bold> <white><bold>語音模組",
            "<gradient:#ee9ca7:#ffdde1>需要協助？</gradient> <white>瀏覽網站 <gradient:#89f7fe:#66a6ff><underlined>mcmik.top</underlined></gradient>"
    );

    private static final MotdProfileSpec TAIWAN = traditional(
            "<gold>創意<white>休閒服",
            "<white><bold>推薦安裝</bold> <green><bold>Plasmo Voice</bold> <white><bold>語音模組",
            "<gradient:#ee9ca7:#ffdde1>需要幫忙？</gradient> <white>前往網站 <gradient:#89f7fe:#66a6ff><underlined>mcmik.top</underlined></gradient>"
    );

    private static final MotdProfileSpec CLASSICAL_CHINESE = new MotdProfileSpec(
            "<gold>米<white>客",
            "<gold>營造<white>閒遊之服",
            new String[]{
                    "<gradient:#5e4fa2:#f79459>營造 · 閒遊 · 絲竹 · 歡娛 · 暫離</gradient>",
                    "<white><bold>宜裝</bold> <green><bold>Plasmo Voice</bold> <white><bold>語音增益",
                    "<gradient:#89f7fe:#66a6ff>✦</gradient> <white>如臨其境，<gradient:#ee9ca7:#ffdde1><bold>ViveCraft</bold></gradient> <white>今亦可用 <gradient:#66a6ff:#89f7fe>✦</gradient>",
                    "<gradient:#ee9ca7:#ffdde1>有所疑乎？</gradient> <white>且訪 <gradient:#89f7fe:#66a6ff><underlined>mcmik.top</underlined></gradient>",
            },
            new String[][]{
                    {"<gradient:#66edff:#66ffb2>叩之？</gradient>", "<gradient:#ff9a9e:#fad0c4>應之！</gradient>", "<white>屢叩不止...</white>", "<aqua>子試指力乎？</aqua>", "<gradient:#a18cd1:#fbc2eb>毋復叩矣，入而遊之</gradient>"},
                    {"<gradient:#84fab0:#8fd3f4>尚疑入否？</gradient>", "<white>此間<green>和善</green>，君可無憂</white>", "<gold>徐徐思之</gold>，<white>戶常爲君啟</white>", "<gradient:#ffecd2:#fcb69f>姑入一觀，亦何妨？</gradient>", "<gradient:#f6d365:#fda085>候君久矣</gradient>"},
                    {"<white>一叩！</white>", "<yellow>再叩！</yellow>", "<gold>又叩！</gold>", "<gradient:#ff0080:#ff8c00>子其戲我乎 owo</gradient>", "<bold><gradient:#42e695:#3bb2b8>善，入而同遊 XD</gradient></bold>"},
                    {"<gray>方載妙物⋯</gray>", "<white>其實此 MOTD <yellow>出於人手</yellow></white>", "<gradient:#43e97b:#38f9d7>纂此文者願君入焉</gradient>", "<aqua>（彼時或稍覺無聊）</aqua>", "<gradient:#fa709a:#fee140><bold>然此服誠可樂也！</bold></gradient>"},
            },
            new String[]{"<gradient:#ffd89b:#19547b>眾皆暫離，勤矣哉⋯</gradient>", "<gray>舉服寂然，若有所待</gray>"},
            new String[]{"<gradient:#7f7fd5:#86a8e7:#91eae4>夜既深矣</gradient><white>，宜</white><gradient:#fbc2eb:#a6c1ee>早寢</gradient>", "<gradient:#f6d365:#fda085>更深露重</gradient><white>，君其</white><gradient:#84fab0:#8fd3f4>少憩</gradient>"},
            new String[]{"<white>歸去來兮，</white><gradient:#9bd8d0:#b9d7f0>{player}</gradient>", "<white>復見君，</white><gradient:#d6c6f2:#b7d9ea>{player}</gradient><white>，今日且從容</white>", "<gradient:#b8dfd8:#d7e8c8>{player}</gradient><white>，此方小天地候君久矣</white>", "<white>幸見君還，</white><gradient:#f0c9c2:#d6d4f0>{player}</gradient><white>，願君盡興</white>"}
    );

    private MotdCatalog() {}

    public static MotdProfileSpec profile(Language language) {
        return switch (language) {
            case ZH_CN -> SIMPLIFIED_CHINESE;
            case ZH_HK -> HONG_KONG;
            case ZH_TW -> TAIWAN;
            case LZH -> CLASSICAL_CHINESE;
            case EN_US -> ENGLISH;
            default -> LocalizedMotdCatalog.profile(language);
        };
    }

    private static MotdProfileSpec traditional(String category, String voiceLine, String helpLine) {
        return new MotdProfileSpec(
                "<gold>米<white>客",
                category,
                new String[]{
                        "<gradient:#5e4fa2:#f79459>建造 · 摸魚 · 音樂 · 快樂 · AFK",
                        voiceLine,
                        "<gradient:#89f7fe:#66a6ff>✦</gradient> <white>身歷其境 <gradient:#ee9ca7:#ffdde1><bold>ViveCraft</bold></gradient> <white>模組已支援 <gradient:#66a6ff:#89f7fe>✦</gradient>",
                        helpLine,
                },
                TRADITIONAL_EGGS,
                TRADITIONAL_AFK,
                TRADITIONAL_NIGHT,
                TRADITIONAL_KNOWN_PLAYER
        );
    }
}
