package org.encinet.mik.module.presentation.motd;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.encinet.mik.module.i18n.Language;
import org.junit.jupiter.api.Test;

class HolidayMotdServiceTest {

    @Test
    void holidayMotdsAreLimitedToModernChineseLocales() {
        assertTrue(HolidayMotdService.supports(Language.ZH_CN));
        assertTrue(HolidayMotdService.supports(Language.ZH_HK));
        assertTrue(HolidayMotdService.supports(Language.ZH_TW));

        assertFalse(HolidayMotdService.supports(Language.LZH));
        assertFalse(HolidayMotdService.supports(Language.EN_US));
        assertFalse(HolidayMotdService.supports(Language.DE_DE));
        assertFalse(HolidayMotdService.supports(Language.JA_JP));
    }
}
