package org.encinet.mik.module.presentation.motd.holiday;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.encinet.mik.module.i18n.Language;
import org.junit.jupiter.api.Test;

import java.util.Random;

class HolidayMessageCatalogTest {

    @Test
    void convertsKnownHolidayNamesForTraditionalChinese() {
        assertEquals("春節與國慶", HolidayMessageCatalog.toTraditional("春节与国庆"));
        assertEquals("兒童節", HolidayMessageCatalog.toTraditional("儿童节"));
    }

    @Test
    void rendersHongKongAndTaiwanMessages() {
        HolidayOccurrence holiday = new HolidayOccurrence("春节");
        String hongKong = HolidayMessageCatalog.render(Language.ZH_HK, holiday, new Random(1));
        String taiwan = HolidayMessageCatalog.render(Language.ZH_TW, holiday, new Random(1));
        assertTrue(hongKong.contains("春節"));
        assertTrue(taiwan.contains("春節"));
        MiniMessage.miniMessage().deserialize(hongKong);
        MiniMessage.miniMessage().deserialize(taiwan);
        MiniMessage.miniMessage().deserialize(
                HolidayMessageCatalog.render(Language.ZH_CN, holiday, new Random(1)));
    }

    @Test
    void rejectsNonChineseLocales() {
        HolidayOccurrence holiday = new HolidayOccurrence("春节");
        assertThrows(IllegalArgumentException.class,
                () -> HolidayMessageCatalog.render(Language.EN_US, holiday, new Random(1)));
    }
}
