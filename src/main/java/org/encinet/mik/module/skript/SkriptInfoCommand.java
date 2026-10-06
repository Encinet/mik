package org.encinet.mik.module.skript;

import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Shows players the installed Skript toolchain and a prompt they can give to an AI assistant. */
final class SkriptInfoCommand {

    static final String COMMAND_NAME = "script";
    static final String SKRIPT_DOCS = "https://docs.skriptlang.org/";
    static final String SKRIPT_RELEASES = "https://modrinth.com/plugin/skript/versions";
    static final String SKBEE_DOCS = "https://github.com/ShaneBeee/SkBee/wiki";
    static final String SKBEE_SYNTAX = "https://skripthub.net/docs/?addon=SkBee";
    static final String SKBEE_RELEASES = "https://modrinth.com/plugin/skbee/versions";
    static final String SKRIPT_PARTICLE_DOCS = "https://skripthub.net/docs/?addon=Skript-Particle";
    static final String SKRIPT_PARTICLE_RELEASES =
            "https://modrinth.com/plugin/skript-particle/versions";
    static final String MIK_DOCS = "https://github.com/Encinet/mik/blob/main/docs/skript.md";

    private final JavaPlugin plugin;
    private final LanguageService languageService;

    SkriptInfoCommand(JavaPlugin plugin, LanguageService languageService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.languageService = Objects.requireNonNull(languageService, "languageService");
    }

    void registerCommands(LifecycleEventManager<Plugin> manager) {
        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> event.registrar().register(
                Commands.literal(COMMAND_NAME)
                        .executes(context -> show(context.getSource().getSender()))
                        .build(),
                languageService.t(Language.DEFAULT, Message.SKRIPT_COMMAND_DESCRIPTION)));
    }

    private int show(CommandSender sender) {
        Language language = sender instanceof Player player
                ? languageService.language(player)
                : Language.DEFAULT;
        Environment environment = detectEnvironment();
        String prompt = aiPrompt(language, environment);

        TextComponent message = Component.text()
                .append(Component.text("----- ", NamedTextColor.DARK_GRAY))
                .append(Component.text(languageService.t(language, Message.SKRIPT_INFO_TITLE),
                        NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text(" -----", NamedTextColor.DARK_GRAY))
                .appendNewline()
                .append(Component.text(languageService.t(language, Message.SKRIPT_INFO_SUPPORTED,
                        environment.displaySummary()), NamedTextColor.GRAY))
                .appendNewline()
                .append(Component.text(languageService.t(language, Message.SKRIPT_INFO_DOCUMENTATION)
                        + ": ", NamedTextColor.GRAY))
                .append(documentationLinks(language))
                .appendNewline()
                .append(Component.text(languageService.t(language, Message.SKRIPT_INFO_WORKFLOW),
                        NamedTextColor.WHITE))
                .appendNewline()
                .append(Component.text(languageService.t(language, Message.SKRIPT_INFO_REVIEW),
                        NamedTextColor.YELLOW))
                .appendNewline()
                .append(Component.text(languageService.t(language, Message.SKRIPT_INFO_AI_OPTION),
                        NamedTextColor.GRAY))
                .appendNewline()
                .append(copyButton(
                        languageService.t(language, Message.SKRIPT_INFO_COPY),
                        languageService.t(language, Message.SKRIPT_INFO_COPY_HOVER),
                        prompt))
                .build();
        sender.sendMessage(message);
        return Command.SINGLE_SUCCESS;
    }

    private Component documentationLinks(Language language) {
        String hover = languageService.t(language, Message.CLICK_OPEN);
        return Component.text()
                .append(documentationButton("Skript", SKRIPT_DOCS, hover))
                .append(Component.space())
                .append(documentationButton("SkBee", SKBEE_SYNTAX, hover))
                .append(Component.space())
                .append(documentationButton("skript-particle", SKRIPT_PARTICLE_DOCS, hover))
                .append(Component.space())
                .append(documentationButton("MIK API", MIK_DOCS, hover))
                .build();
    }

    private Environment detectEnvironment() {
        List<String> tools = new ArrayList<>();
        addInstalledPlugin(tools, "Skript", "Skript");
        addInstalledPlugin(tools, "SkBee", "SkBee");
        addInstalledPlugin(tools, "skript-particle", "skript-particle", "SkriptParticle");
        tools.add("MIK Skript API (MIK " + plugin.getPluginMeta().getVersion() + ")");
        return new Environment(plugin.getServer().getMinecraftVersion(), tools);
    }

    private void addInstalledPlugin(List<String> tools, String label, String... pluginNames) {
        for (Plugin installed : plugin.getServer().getPluginManager().getPlugins()) {
            if (!installed.isEnabled()) {
                continue;
            }
            for (String pluginName : pluginNames) {
                if (normalizePluginName(installed.getName()).equals(normalizePluginName(pluginName))) {
                    tools.add(label + " " + installed.getPluginMeta().getVersion());
                    return;
                }
            }
        }
    }

    private static String normalizePluginName(String name) {
        return name.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "").replace(" ", "");
    }

    static Component copyButton(String label, String hover, String prompt) {
        return Component.text(label, NamedTextColor.AQUA, TextDecoration.BOLD)
                .clickEvent(ClickEvent.copyToClipboard(prompt))
                .hoverEvent(HoverEvent.showText(Component.text(hover, NamedTextColor.YELLOW)));
    }

    static Component documentationButton(String label, String url, String hover) {
        Component hoverText = Component.text(hover, NamedTextColor.YELLOW)
                .appendNewline()
                .append(Component.text(url, NamedTextColor.GRAY));
        return Component.text("[" + label + "]", NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.openUrl(url))
                .hoverEvent(HoverEvent.showText(hoverText));
    }

    static String aiPrompt(Language language, Environment environment) {
        return isChinese(language)
                ? chinesePrompt(language, environment)
                : englishPrompt(language, environment);
    }

    private static boolean isChinese(Language language) {
        return language == Language.ZH_CN || language == Language.ZH_HK
                || language == Language.ZH_TW || language == Language.LZH;
    }

    private static String chinesePrompt(Language language, Environment environment) {
        return """
                你是一名严谨的 Minecraft Paper / Skript 开发者。请和我一起制作一个可提交给服务器管理员审核的单文件 .sk 脚本。你生成的是待审核源码，不得声称已经在服务器运行、测试或验证通过。
                请使用玩家选择的语言“%s”（locale %s）回答。

                唯一可用的运行环境（由服务器运行时检测）：
                - Paper / Minecraft %s
                - 已启用的脚本组件：%s

                只能使用上面明确列出的组件。下面提供某个组件的文档链接，不代表它已安装或可用。SkBee 的部分模块可能由服务器配置关闭；若使用模块专有语法，请注明模块名称，交由管理员确认。

                文档与版本页：
                - Skript 语法: %s
                - Skript 版本: %s
                - SkBee Wiki: %s
                - SkBee 语法: %s
                - SkBee 版本: %s
                - skript-particle 语法: %s
                - skript-particle 版本: %s
                - MIK 服务器 API: %s

                写代码前，按已安装版本核对实际用到的事件、条件、表达式、effect 和类型。若无法访问链接，请明确告诉我需要粘贴哪一页；不要假装查过文档，也不要从其他 Skript 附属插件猜测相似语法。

                MIK API 是本服务器专用扩展。需求涉及玩家语言、客户端版本、身份、AFK、PVP 或非欧空间时，必须使用上面的 MIK 文档核对语法和副作用，不要猜测。

                功能需求写在本提示词末尾。若需求尚未填写，请先问我想实现什么；若已有描述，只追问会影响正确实现的歧义。必要信息确认前不要输出代码。

                生成要求：
                1. 只输出一个完整、自包含的 .sk 文件，不要求安装清单之外的插件。优先选择简单、容易审核的写法。
                2. 文件名必须准确描述功能，采用小写 ASCII kebab-case，并以 .sk 结尾，例如 player-welcome.sk。必须以英文字母开头，除扩展名前只允许 a-z、0-9 和单个连字符；禁止空格、下划线、中文、连续连字符、首尾连字符及其他特殊字符。不要使用 script.sk、test.sk、new.sk 等含义不明的名称。
                3. 脚本第一行必须就是下面的文件头，前面不得有空行或其他内容。字段顺序与名称保持一致，所有占位符都要替换为真实内容；建议文件名必须与 File 字段完全一致。Version 使用语义化版本，初始版本默认 1.0.0。Dependencies 只列实际使用且已安装的组件及所需 SkBee 模块；Access 写 Public，或写实际允许的 MIK 角色。

                   Author 行语法为 # Author: NAME[ <TYPE:VALUE> ...]；方括号表示可选内容，不得原样输出。NAME 必填，去掉首尾空白后必须非空、单行且不含 < 或 >；允许中文和内部空格，其他拼写保持原样。名称缺失时先询问，禁止猜测或填写 AI、ChatGPT、Unknown。联系方式全部选填；未提供时只写 NAME，不得追问或阻塞生成。每个标签前恰好一个空格，例如 Noctiro <email:noctiro@example.com> <qq:123456789>。type 使用小写 ASCII kebab-case，优先选 email、qq、github、discord、website、matrix。标签中的第一个冒号分隔 type 与 value；value 必须非空且不含空白、<、>、换行，但可继续包含冒号，如 website:https://example.com 或 matrix:@user:example.com。只写我明确提供的联系方式，不得猜测、补全或虚构。

                # File: <规范化文件名>.sk
                # Description: <一至两句说明用途、触发方式和主要行为>
                # Author: <规范化作者信息>
                # Version: <语义化版本>
                # Environment: Paper / Minecraft <版本>
                # Dependencies: <实际使用的组件与模块，或 None>
                # Access: <Public，或允许的 MIK 角色>

                4. 文件使用 UTF-8 且不带 BOM。七行文件头严格采用 # Field: Value，之后恰好一个空行。仅为已有内容添加固定英文标题，顺序为 # --- Configuration ---、# --- Functions ---、# --- Commands ---、# --- Events ---、# --- Scheduled Tasks ---；禁止空章节和其他装饰线。普通说明注释统一使用 # <内容>，单独放在相关代码块上方，以玩家选择的语言简述目的、原因、单位、限制或非显而易见的副作用，不复述代码。禁止行尾注释、连续多个 #、表情符号、注释掉的旧代码、TODO/FIXME 和对话内容。
                5. 所有命令和功能默认向全部玩家开放，不得创建或使用任何权限节点，不得使用 Skript 命令的 permission 字段、permission message、玩家权限判断或底层 group.* 权限。只有需求明确要求区分玩家群体时，才使用 MIK API 的 mik role of player 做角色白名单判断：default 表示新成员，member 表示正式成员，moderator 表示管理，custodian 表示维护者。“正式成员可用”默认允许 member、moderator、custodian；不要按字符串大小推断角色层级，应明确列出允许的角色。目标群体不清楚时先询问；采用角色限制时应给被拒绝者明确提示，并安全处理非玩家命令发送者。持久或全局变量使用独特前缀，避免与其他脚本冲突。
                6. 默认不使用高权限或高风险能力，例如控制台命令、OP/权限变更、插件或脚本管理、任意文件/网络访问、世界或服务器 tick 控制、动态解析并执行代码。若需求离不开其中某项，先说明原因和影响，等待管理员确认后再继续。
                7. 仅当功能包含循环、重复任务、实体或粒子时，给出明确的频率、数量、并发或持续时间上限，并在适用时清理任务与临时状态。正常的一次性、低频操作不必添加无关限制。
                8. 不取消与功能无关的事件，不修改无关数据。若某项语法、版本兼容性或副作用无法确认，请把它列为待确认项，不要猜测或悄悄替换实现。

                最终回答格式：
                - 简要复述功能和必要假设，并列出实际使用的 Skript 附属插件/SkBee 模块；说明功能是公开的，或仅在适用时列出允许的 MIK 角色。不要列出权限节点。
                - 提供一个符合上述规则的建议文件名，以及唯一一个包含完整脚本的 Skript 代码块；文件头必须位于代码块第一行，且 File 字段与建议文件名完全一致。不要再给第二份候选代码。
                - 最后列出简短的管理员测试步骤、资源或数据副作用，以及所有尚未验证的事项。

                作者信息（名称必填；联系方式选填，例如 Noctiro <email:noctiro@example.com> <qq:123456789>）：
                [名称尚未填写]

                功能需求：
                [尚未填写]
                """.formatted(
                language.displayName(), language.id(),
                environment.paperVersion(), environment.supportSummary(),
                SKRIPT_DOCS, SKRIPT_RELEASES, SKBEE_DOCS, SKBEE_SYNTAX, SKBEE_RELEASES,
                SKRIPT_PARTICLE_DOCS, SKRIPT_PARTICLE_RELEASES, MIK_DOCS).strip();
    }

    private static String englishPrompt(Language language, Environment environment) {
        return """
                You are a careful Minecraft Paper and Skript developer. Work with me to create one self-contained .sk file for server-administrator review. The result is unreviewed source code: never claim that it has run, been tested, or passed validation on the server.

                Only permitted runtime environment (detected by the server):
                - Paper / Minecraft %s
                - Enabled scripting components: %s

                Use only components explicitly listed above. A documentation link below does not mean that its component is installed or permitted. Some SkBee modules may be disabled in server configuration; when using module-specific syntax, name the required module for administrator confirmation.

                Documentation and release pages:
                - Skript syntax: %s
                - Skript releases: %s
                - SkBee wiki: %s
                - SkBee syntax: %s
                - SkBee releases: %s
                - skript-particle syntax: %s
                - skript-particle releases: %s
                - MIK server API: %s

                Before coding, check every event, condition, expression, effect, and type actually used against the installed versions. If you cannot access a link, tell me exactly which page I should paste. Never pretend that you read it, and never infer similar syntax from another Skript addon.

                MIK API is a server-specific extension. If the feature needs player language, client version, role, AFK, PVP, or non-Euclidean space integration, use the MIK document above to verify its syntax and side effects instead of guessing.

                Reply in the player's selected language, %s (locale %s). The feature request is at the end of this prompt. If it is still blank, first ask what I want to build. If it is present, ask only about ambiguities that affect correctness. Do not output code before the necessary details are settled.

                Requirements:
                1. Produce one complete, self-contained .sk file and require no plugin outside the permitted list. Prefer the simplest implementation that is easy to audit.
                2. The file name must describe the feature, use lowercase ASCII kebab-case, and end in .sk, for example player-welcome.sk. It must start with a letter; before the extension, allow only a-z, 0-9, and single hyphens. Do not use spaces, underscores, non-ASCII characters, repeated hyphens, leading or trailing hyphens, or other special characters. Avoid vague names such as script.sk, test.sk, or new.sk.
                3. The very first line of the script must begin the exact header below, with no blank line or other content before it. Keep the field names and order, replace every placeholder with real information, and make File exactly match the suggested file name. Use semantic versioning and default a new script to 1.0.0. Dependencies must list only installed components and SkBee modules actually used. Write Public for Access, or list the MIK roles actually allowed.

                   Author syntax is # Author: NAME[ <TYPE:VALUE> ...]; brackets denote optional content and are not literal. NAME is required, non-empty after trimming, single-line, and contains neither < nor >. Allow Unicode and internal spaces; otherwise preserve spelling. If absent, ask instead of guessing or using AI, ChatGPT, or Unknown. Every contact is optional; with none supplied, output only NAME without asking or blocking. Put exactly one space before each tag, for example Noctiro <email:noctiro@example.com> <qq:123456789>. Use lowercase ASCII kebab-case types, preferably email, qq, github, discord, website, or matrix. The first colon in a tag separates type from value. Its value must be non-empty and contain no whitespace, <, >, or newline; further colons are valid, as in website:https://example.com and matrix:@user:example.com. Include only explicitly supplied details; never infer, complete, or invent them.

                # File: <normalized-file-name>.sk
                # Description: <one or two sentences covering purpose, trigger, and main behavior>
                # Author: <normalized author information>
                # Version: <semantic version>
                # Environment: Paper / Minecraft <version>
                # Dependencies: <components and modules actually used, or None>
                # Access: <Public, or allowed MIK roles>

                4. Encode the file as UTF-8 without a BOM. Keep the seven header lines in exact # Field: Value form, followed by exactly one blank line. Add only non-empty fixed English headings, ordered # --- Configuration ---, # --- Functions ---, # --- Commands ---, # --- Events ---, and # --- Scheduled Tasks ---; use no other decorative divider. Write ordinary explanatory comments as # <text> immediately above the relevant block, in the player's language. Explain purpose, rationale, units, limits, or non-obvious side effects without narrating code. Do not use end-of-line comments, repeated # markers, emoji, commented-out old code, TODO/FIXME markers, or conversational notes.
                5. Make every command and feature available to all players by default. Never create or use permission nodes, Skript command permission fields, permission messages, player permission checks, or underlying group.* permissions. Only when the request explicitly distinguishes player groups, use mik role of player from the MIK API as an allowlist: default means a new member, member means a full member, moderator means moderator, and custodian means custodian. “Full members only” allows member, moderator, and custodian unless I explicitly request a narrower audience. Never infer hierarchy from string ordering; list every allowed role explicitly. If the intended audience is unclear, ask first. When role-gating a feature, give rejected users a clear message and safely handle non-player command senders. Give persistent or global variables a unique prefix to avoid collisions.
                6. By default, do not use privileged or high-risk capabilities such as console commands, OP/permission changes, plugin or script management, arbitrary file/network access, world or server-tick control, or dynamic code parsing/execution. If a requirement truly needs one, explain why and its impact, then wait for administrator approval before proceeding.
                7. Only when the feature uses loops, recurring tasks, entities, or particles, specify explicit frequency, count, concurrency, or duration limits and clean up tasks or temporary state where applicable. Do not add irrelevant restrictions to normal one-shot, low-frequency work.
                8. Do not cancel unrelated events or modify unrelated data. If syntax, version compatibility, or side effects cannot be verified, list the item as unresolved rather than guessing or silently changing the design.

                Final response format:
                - Briefly restate the feature and necessary assumptions. List the Skript addons and SkBee modules actually used. State that access is public, or list allowed MIK roles only when role gating applies. Do not list permission nodes.
                - Give one suggested file name that follows the rules above and exactly one Skript code block containing the complete script. The header must start on the first line of the code block, and its File value must exactly match the suggested name. Do not provide a second alternative implementation.
                - End with concise administrator test steps, resource or data side effects, and every item that remains unverified.

                Author information (name required; contacts optional, for example Noctiro <email:noctiro@example.com> <qq:123456789>):
                [name not provided yet]

                Feature request:
                [not provided yet]
                """.formatted(
                environment.paperVersion(), environment.supportSummary(),
                SKRIPT_DOCS, SKRIPT_RELEASES, SKBEE_DOCS, SKBEE_SYNTAX, SKBEE_RELEASES,
                SKRIPT_PARTICLE_DOCS, SKRIPT_PARTICLE_RELEASES, MIK_DOCS,
                language.displayName(), language.id()).strip();
    }

    record Environment(String paperVersion, List<String> tools) {
        Environment {
            paperVersion = Objects.requireNonNull(paperVersion, "paperVersion");
            tools = List.copyOf(tools);
        }

        String supportSummary() {
            return String.join(" + ", tools);
        }

        String displaySummary() {
            return "Paper " + paperVersion + " + " + supportSummary();
        }
    }
}
