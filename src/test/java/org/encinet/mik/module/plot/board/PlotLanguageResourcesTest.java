package org.encinet.mik.module.plot.board;

import net.quickwrite.fluent4j.container.ArgumentListBuilder;
import net.quickwrite.fluent4j.container.FluentBundleBuilder;
import net.quickwrite.fluent4j.iterator.FluentIteratorFactory;
import net.quickwrite.fluent4j.parser.ResourceParserBuilder;
import net.quickwrite.fluent4j.result.StringResultFactory;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.Message;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlotLanguageResourcesTest {
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

    @Test
    void everyLanguageResolvesEveryPlotMessage() throws Exception {
        var messages = Arrays.stream(Message.values())
                .filter(message -> message.name().startsWith("PLOT_"))
                .toList();
        assertFalse(messages.isEmpty());
        for (Language language : Language.values()) {
            String resource = "lang/" + language.id() + ".ftl";
            try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
                if (input == null) throw new AssertionError("Missing " + resource);
                var parsed = ResourceParserBuilder.defaultParser().parse(
                        FluentIteratorFactory.fromString(new String(input.readAllBytes(), StandardCharsets.UTF_8)));
                var bundle = FluentBundleBuilder.builder(language.locale())
                        .addResource(parsed).addDefaultFunctions().build();
                var args = ArgumentListBuilder.builder().add("arg0", "Bridge").add("arg1", "ID")
                        .add("arg2", 8L).add("arg3", 4L).add("arg4", 4L).add("arg5", 4L)
                        .add("arg6", 4L).add("arg7", "expiry").build();
                for (Message message : messages) {
                    String value = bundle.resolveMessage(message.key(), args, StringResultFactory.construct())
                            .map(Object::toString)
                            .orElseThrow(() -> new AssertionError(language.id() + " missing " + message.key()));
                    assertFalse(value.isBlank(), () -> language.id() + " blank " + message.key());
                }
                var regionArgs = ArgumentListBuilder.builder().add("arg0", 17L).build();
                for (Message message : List.of(Message.PLOT_REGION_OPEN, Message.PLOT_REGION_DELETE_CONFIRM)) {
                    String value = bundle.resolveMessage(message.key(), regionArgs, StringResultFactory.construct())
                            .map(Object::toString).orElseThrow();
                    assertTrue(value.contains("17"), () -> language.id() + " missing rectangle number in " + message.key());
                    assertFalse(value.contains("$arg0"));
                }
            }
        }
    }
}
