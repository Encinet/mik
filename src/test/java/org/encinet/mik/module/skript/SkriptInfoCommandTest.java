package org.encinet.mik.module.skript;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.encinet.mik.module.i18n.Language;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkriptInfoCommandTest {

    private static final SkriptInfoCommand.Environment ENVIRONMENT = new SkriptInfoCommand.Environment(
            "26.2",
            List.of("Skript 2.16.0", "SkBee 3.25.2", "skript-particle 1.4.1",
                    "MIK Skript API (MIK 1.0)"));

    @Test
    void chinesePromptContainsExactEnvironmentAndAllDocumentationLinks() {
        String prompt = SkriptInfoCommand.aiPrompt(Language.ZH_CN, ENVIRONMENT);

        assertTrue(prompt.contains("Paper / Minecraft 26.2"));
        assertTrue(prompt.contains("简体中文”（locale zh_cn）"));
        assertTrue(prompt.contains(ENVIRONMENT.supportSummary()));
        assertTrue(prompt.contains("文档链接，不代表它已安装或可用"));
        assertTrue(prompt.contains(SkriptInfoCommand.SKRIPT_DOCS));
        assertTrue(prompt.contains(SkriptInfoCommand.SKRIPT_RELEASES));
        assertTrue(prompt.contains(SkriptInfoCommand.SKBEE_DOCS));
        assertTrue(prompt.contains(SkriptInfoCommand.SKBEE_SYNTAX));
        assertTrue(prompt.contains(SkriptInfoCommand.SKBEE_RELEASES));
        assertTrue(prompt.contains(SkriptInfoCommand.SKRIPT_PARTICLE_DOCS));
        assertTrue(prompt.contains(SkriptInfoCommand.SKRIPT_PARTICLE_RELEASES));
        assertTrue(prompt.contains(SkriptInfoCommand.MIK_DOCS));
        assertTrue(prompt.contains("必须使用上面的 MIK 文档核对语法和副作用"));
        assertTrue(prompt.contains("不得声称已经在服务器运行、测试或验证通过"));
        assertTrue(prompt.contains("仅在需要限制时加入明确的权限节点"));
        assertTrue(prompt.contains("正常的一次性、低频操作不必添加无关限制"));
        assertTrue(prompt.contains("功能需求：\n[尚未填写]"));
        assertTrue(prompt.length() < 8_000);
    }

    @Test
    void nonChinesePromptRequestsThePlayersLocale() {
        String prompt = SkriptInfoCommand.aiPrompt(Language.JA_JP, ENVIRONMENT);

        assertTrue(prompt.contains("日本語 (locale ja_jp)"));
        assertTrue(prompt.contains("A documentation link below does not mean"));
        assertTrue(prompt.contains("never claim that it has run, been tested, or passed validation"));
        assertTrue(prompt.contains("Feature request:\n[not provided yet]"));
        assertTrue(prompt.length() < 8_000);
    }

    @Test
    void traditionalChinesePromptRequestsTraditionalChineseOutput() {
        String prompt = SkriptInfoCommand.aiPrompt(Language.ZH_TW, ENVIRONMENT);

        assertTrue(prompt.contains("繁體中文（台灣）”（locale zh_tw）"));
    }

    @Test
    void copyButtonCopiesTheWholePrompt() {
        String prompt = SkriptInfoCommand.aiPrompt(Language.EN_US, ENVIRONMENT);
        Component button = SkriptInfoCommand.copyButton("copy", "hover", prompt);

        assertEquals(ClickEvent.copyToClipboard(prompt), button.clickEvent());
    }

    @Test
    void documentationButtonOpensTheRequestedPage() {
        Component button = SkriptInfoCommand.documentationButton(
                "MIK API", SkriptInfoCommand.MIK_DOCS, "open");

        assertEquals(ClickEvent.openUrl(SkriptInfoCommand.MIK_DOCS), button.clickEvent());
    }
}
