package org.encinet.mik.module.communication.tip;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TipIntentMatcherTest {

    private final TipIntentMatcher matcher = new TipIntentMatcher();

    @Test
    void recognizesActionableChineseQuestions() {
        assertTopic("home", "请问怎么回家？");
        assertTopic("website", "官网在哪");
        assertTopic("music", "音乐菜单怎么打开？");
        assertTopic("music-search", "请问怎么搜索歌曲？");
        assertTopic("jukebox", "唱片机菜单怎么打开？");
        assertTopic("menu", "主菜单在哪里？");
        assertTopic("flight", "请问怎么飞？");
        assertTopic("private-chat", "如何私聊其他玩家？");
        assertTopic("chat-settings", "怎么关闭提及提醒？");
        assertTopic("nametag", "怎么改名称标签？");
        assertTopic("hat", "怎么把物品戴头上？");
    }

    @Test
    void normalizesFullWidthLatinAndUnderstandsExplicitCommands() {
        assertTopic("music", "ＨＯＷ do I use /music?");
        assertTopic("spawn", "what does /spawn do?");
    }

    @Test
    void avoidsSubstringAndCasualConversationFalsePositives() {
        assertTrue(matcher.match("大家好").isEmpty());
        assertTrue(matcher.match("homework is difficult").isEmpty());
        assertTrue(matcher.match("这音乐真好听").isEmpty());
        assertTrue(matcher.match("performance art is interesting").isEmpty());
    }

    private void assertTopic(String expected, String message) {
        assertEquals(expected, matcher.match(message).orElseThrow().topic());
    }
}
