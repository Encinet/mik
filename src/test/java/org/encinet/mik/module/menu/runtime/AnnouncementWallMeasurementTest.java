package org.encinet.mik.module.menu.runtime;

import net.kyori.adventure.text.Component;
import org.encinet.mik.module.menu.FloatingMenuDefinition;
import org.encinet.mik.module.menu.FloatingMenuTextScale;
import org.encinet.mik.module.menu.FloatingMenuTextWidth;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AnnouncementWallMeasurementTest {
    @Test
    void translatedControlsFitTheFixedWallBandsAtEveryTextScale() throws Exception {
        int locales = 0;
        try (var paths = Files.list(Path.of("src/main/resources/lang"))) {
            for (var file : paths.filter(candidate -> candidate.toString().endsWith(".ftl")).toList()) {
                locales++;
                Map<String, String> messages = new HashMap<>();
                for (String line : Files.readAllLines(file)) {
                    int separator = line.indexOf(" = ");
                    if (separator > 0) messages.put(line.substring(0, separator), line.substring(separator + 3));
                }
                var menu = FloatingMenuDefinition.screen("announcements");
                menu.information("heading", Component.text(messages.get("main-announcements") + "\n"
                        + messages.get("announcement-scroll-hint"))).textWidth(FloatingMenuTextWidth.WIDE);
                menu.navigation("newer", Component.text("‹ " + messages.get("announcement-newer")));
                menu.navigation("older", Component.text(messages.get("announcement-older") + " ›"));
                menu.control("reload", Component.text(messages.get("announcement-reload"))).textWidth(FloatingMenuTextWidth.RING);
                menu.dismiss(Component.text(messages.get("back-to-main"))).textWidth(FloatingMenuTextWidth.RING);
                var definition = menu.build();
                for (var scale : FloatingMenuTextScale.values()) {
                    Map<String, Double> heights = new HashMap<>();
                    for (var entry : definition.entries().values()) {
                        var size = FloatingMenuNodeSizing.measure(entry, scale.factor()).footprint();
                        heights.put(entry.id(), size.height() / scale.factor());
                        assertTrue(size.width() / scale.factor() <= 4.2, file + ":" + entry.id());
                    }
                    assertTrue(heights.get("heading") / 2 < 0.62, file.toString());
                    double browseHeight = Math.max(heights.get("newer"), heights.get("older"));
                    double footerHeight = definition.entries().values().stream()
                            .filter(entry -> !entry.id().equals("heading") && !entry.id().equals("newer") && !entry.id().equals("older"))
                            .mapToDouble(entry -> heights.get(entry.id())).max().orElseThrow();
                    assertTrue(browseHeight / 2 < 0.52, file.toString());
                    assertTrue((browseHeight + footerHeight) / 2 < 0.60, file.toString());
                    assertTrue(footerHeight / 2 + 1.12 < 1.5, file.toString());
                }
            }
        }
        assertEquals(16, locales);
    }
}
