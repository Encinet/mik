package org.encinet.mik.module.presentation.motd;

import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.presentation.motd.holiday.ChinaHolidayCalendar;
import org.encinet.mik.module.presentation.motd.holiday.HolidayMessageCatalog;
import org.encinet.mik.module.presentation.motd.holiday.HolidayOccurrence;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.random.RandomGenerator;

public final class HolidayMotdService {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    private final ChinaHolidayCalendar chinaCalendar;

    public HolidayMotdService(JavaPlugin plugin) {
        this.chinaCalendar = new ChinaHolidayCalendar(plugin);
    }

    public void enable() {
        chinaCalendar.enable();
    }

    public void disable() {
        chinaCalendar.disable();
    }

    public void setRefreshListener(Runnable refreshListener) {
        chinaCalendar.setRefreshListener(refreshListener);
    }

    public Optional<String> resolveLine(Language language, RandomGenerator rng) {
        return resolveLine(LocalDate.now(SHANGHAI), language, rng);
    }

    public Optional<String> resolveLine(LocalDate today, Language language, RandomGenerator rng) {
        if (!supports(language)) {
            return Optional.empty();
        }
        Optional<HolidayOccurrence> holiday = chinaCalendar.resolve(today);
        return holiday.map(value -> HolidayMessageCatalog.render(language, value, rng));
    }

    static boolean supports(Language language) {
        return language == Language.ZH_CN || language == Language.ZH_HK || language == Language.ZH_TW;
    }
}
