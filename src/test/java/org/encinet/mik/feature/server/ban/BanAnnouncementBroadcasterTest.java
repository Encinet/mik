package org.encinet.mik.feature.server.ban;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BanAnnouncementBroadcasterTest {

    @Test
    void bulletinUsesFiveOrderedLines() {
        String text = PlainTextComponentSerializer.plainText().serialize(BanAnnouncementBroadcaster.bulletin(
                "[封禁公报]",
                "玩家", "TargetPlayer",
                "解封时间", "LocalizedExpiration",
                "原因", "LocalizedReason",
                "执行者", "Moderator"));

        assertEquals("[封禁公报]\n"
                + "  玩家  TargetPlayer\n"
                + "  解封时间  LocalizedExpiration\n"
                + "  原因  LocalizedReason\n"
                + "  执行者  Moderator", text);
    }
}
