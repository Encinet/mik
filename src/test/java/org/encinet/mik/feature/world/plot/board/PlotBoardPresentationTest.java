package org.encinet.mik.feature.world.plot.board;

import org.encinet.mik.shared.i18n.Language;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlotBoardPresentationTest {
    @Test
    void longMixedLanguageRecordsRemainCompleteAcrossMenuPages() {
        String body = "建筑😀".repeat(180) + " route and entrance".repeat(18);
        List<String> pages = PlotCommunityBoard.textPages(body);
        assertTrue(pages.size() > 1);
        assertEquals(body, String.join("", pages).replace("\n", ""));
        assertTrue(pages.stream().allMatch(page -> page.split("\n", -1).length <= 6));
    }

    @Test
    void alertLinksUseTheRecipientsAvailableWebsiteLocale() {
        String id = "00000000-0000-0000-0000-000000000001";
        assertEquals("https://mcmik.top/zh-CN/community/notices#notice-" + id,
                PlotBoardNotifications.boardUrl(Language.ZH_HK, id));
        assertEquals("https://mcmik.top/en/community/notices#notice-" + id,
                PlotBoardNotifications.boardUrl(Language.JA_JP, id));
    }
}
