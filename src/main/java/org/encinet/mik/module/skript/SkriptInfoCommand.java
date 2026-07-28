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

                MIK API 是本服务器专用扩展。需求涉及玩家语言、客户端版本、身份、AFK 或 PVP 时，必须使用上面的 MIK 文档核对语法和副作用，不要猜测。

                功能需求写在本提示词末尾。若需求尚未填写，请先问我想实现什么；若已有描述，只追问会影响正确实现的歧义。必要信息确认前不要输出代码。

                生成要求：
                1. 只输出一个完整、自包含的 .sk 文件，不要求安装清单之外的插件。优先选择简单、容易审核的写法。
                2. 若包含玩家命令，说明它应公开还是受权限保护；仅在需要限制时加入明确的权限节点和拒绝提示。持久或全局变量使用独特前缀，避免与其他脚本冲突。
                3. 默认不使用高权限或高风险能力，例如控制台命令、OP/权限变更、插件或脚本管理、任意文件/网络访问、世界或服务器 tick 控制、动态解析并执行代码。若需求离不开其中某项，先说明原因和影响，等待管理员确认后再继续。
                4. 仅当功能包含循环、重复任务、实体或粒子时，给出明确的频率、数量、并发或持续时间上限，并在适用时清理任务与临时状态。正常的一次性、低频操作不必添加无关限制。
                5. 不取消与功能无关的事件，不修改无关数据。若某项语法、版本兼容性或副作用无法确认，请把它列为待确认项，不要猜测或悄悄替换实现。

                最终回答格式：
                - 简要复述功能和必要假设，并列出实际使用的 Skript 附属插件/SkBee 模块；只在适用时列权限节点。
                - 提供建议文件名，以及唯一一个包含完整脚本的 Skript 代码块；不要再给第二份候选代码。
                - 最后列出简短的管理员测试步骤、资源或数据副作用，以及所有尚未验证的事项。

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

                MIK API is a server-specific extension. If the feature needs player language, client version, role, AFK, or PVP integration, use the MIK document above to verify its syntax and side effects instead of guessing.

                Reply in the player's selected language, %s (locale %s). The feature request is at the end of this prompt. If it is still blank, first ask what I want to build. If it is present, ask only about ambiguities that affect correctness. Do not output code before the necessary details are settled.

                Requirements:
                1. Produce one complete, self-contained .sk file and require no plugin outside the permitted list. Prefer the simplest implementation that is easy to audit.
                2. For player commands, state whether each command should be public or permission-protected. Add an explicit permission node and denial message only when access should be restricted. Give persistent or global variables a unique prefix to avoid collisions.
                3. By default, do not use privileged or high-risk capabilities such as console commands, OP/permission changes, plugin or script management, arbitrary file/network access, world or server-tick control, or dynamic code parsing/execution. If a requirement truly needs one, explain why and its impact, then wait for administrator approval before proceeding.
                4. Only when the feature uses loops, recurring tasks, entities, or particles, specify explicit frequency, count, concurrency, or duration limits and clean up tasks or temporary state where applicable. Do not add irrelevant restrictions to normal one-shot, low-frequency work.
                5. Do not cancel unrelated events or modify unrelated data. If syntax, version compatibility, or side effects cannot be verified, list the item as unresolved rather than guessing or silently changing the design.

                Final response format:
                - Briefly restate the feature and necessary assumptions. List the Skript addons and SkBee modules actually used; list permission nodes only when applicable.
                - Give a suggested file name and exactly one Skript code block containing the complete script. Do not provide a second alternative implementation.
                - End with concise administrator test steps, resource or data side effects, and every item that remains unverified.

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
