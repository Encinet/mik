package org.encinet.mik.module.skript;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.encinet.mik.module.i18n.Language;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        assertTrue(prompt.contains("采用小写 ASCII kebab-case"));
        assertTrue(prompt.contains("禁止空格、下划线、中文、连续连字符"));
        assertTrue(prompt.contains("# File: <规范化文件名>.sk\n"
                + "# Description: <一至两句说明用途、触发方式和主要行为>\n"
                + "# Author: <规范化作者信息>"));
        assertTrue(prompt.contains("# Author: NAME[ <TYPE:VALUE> ...]"));
        assertTrue(prompt.contains("联系方式全部选填；未提供时只写 NAME"));
        assertTrue(prompt.contains("Noctiro <email:noctiro@example.com> <qq:123456789>"));
        assertTrue(prompt.contains("标签中的第一个冒号分隔 type 与 value"));
        assertTrue(prompt.contains("website:https://example.com 或 matrix:@user:example.com"));
        assertTrue(prompt.contains("只写我明确提供的联系方式，不得猜测、补全或虚构"));
        assertTrue(prompt.contains("File 字段与建议文件名完全一致"));
        assertTrue(prompt.contains("# Access: <Public，或允许的 MIK 角色>"));
        assertTrue(prompt.contains("不得创建或使用任何权限节点"));
        assertTrue(prompt.contains("default 表示新成员，member 表示正式成员"));
        assertTrue(prompt.contains("“正式成员可用”默认允许 member、helper、manager"));
        assertFalse(prompt.contains("# Permissions:"));
        assertTrue(prompt.contains("文件使用 UTF-8 且不带 BOM"));
        assertTrue(prompt.contains("# --- Configuration ---、# --- Functions ---、"
                + "# --- Commands ---、# --- Events ---、# --- Scheduled Tasks ---"));
        assertTrue(prompt.contains("普通说明注释统一使用 # <内容>"));
        assertTrue(prompt.contains("禁止行尾注释、连续多个 #、表情符号、注释掉的旧代码、TODO/FIXME"));
        assertTrue(prompt.contains("正常的一次性、低频操作不必添加无关限制"));
        assertTrue(prompt.contains("作者信息（名称必填；联系方式选填"));
        assertTrue(prompt.contains("[名称尚未填写]"));
        assertTrue(prompt.contains("功能需求：\n[尚未填写]"));
        assertTrue(prompt.length() < 8_000, () -> "Chinese prompt length: " + prompt.length());
    }

    @Test
    void nonChinesePromptRequestsThePlayersLocale() {
        String prompt = SkriptInfoCommand.aiPrompt(Language.JA_JP, ENVIRONMENT);

        assertTrue(prompt.contains("日本語 (locale ja_jp)"));
        assertTrue(prompt.contains("A documentation link below does not mean"));
        assertTrue(prompt.contains("never claim that it has run, been tested, or passed validation"));
        assertTrue(prompt.contains("use lowercase ASCII kebab-case"));
        assertTrue(prompt.contains("Do not use spaces, underscores, non-ASCII characters"));
        assertTrue(prompt.contains("# File: <normalized-file-name>.sk\n"
                + "# Description: <one or two sentences covering purpose, trigger, and main behavior>\n"
                + "# Author: <normalized author information>"));
        assertTrue(prompt.contains("# Author: NAME[ <TYPE:VALUE> ...]"));
        assertTrue(prompt.contains("Every contact is optional"));
        assertTrue(prompt.contains("Noctiro <email:noctiro@example.com> <qq:123456789>"));
        assertTrue(prompt.contains("The first colon in a tag separates type from value"));
        assertTrue(prompt.contains("website:https://example.com and matrix:@user:example.com"));
        assertTrue(prompt.contains("never infer, complete, or invent them"));
        assertTrue(prompt.contains("File value must exactly match the suggested name"));
        assertTrue(prompt.contains("# Access: <Public, or allowed MIK roles>"));
        assertTrue(prompt.contains("Never create or use permission nodes"));
        assertTrue(prompt.contains("default means a new member, member means a full member"));
        assertTrue(prompt.contains("“Full members only” allows member, helper, and manager"));
        assertFalse(prompt.contains("# Permissions:"));
        assertTrue(prompt.contains("Encode the file as UTF-8 without a BOM"));
        assertTrue(prompt.contains("# --- Configuration ---, # --- Functions ---, "
                + "# --- Commands ---, # --- Events ---, and # --- Scheduled Tasks ---"));
        assertTrue(prompt.contains("ordinary explanatory comments as # <text>"));
        assertTrue(prompt.contains("Do not use end-of-line comments, repeated # markers, emoji, "
                + "commented-out old code, TODO/FIXME markers"));
        assertTrue(prompt.contains("Author information (name required; contacts optional"));
        assertTrue(prompt.contains("[name not provided yet]"));
        assertTrue(prompt.contains("Feature request:\n[not provided yet]"));
        assertTrue(prompt.length() < 8_000, () -> "English prompt length: " + prompt.length());
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
