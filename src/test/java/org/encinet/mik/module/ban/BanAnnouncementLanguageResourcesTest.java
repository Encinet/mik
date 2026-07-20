package org.encinet.mik.module.ban;

import net.quickwrite.fluent4j.container.ArgumentListBuilder;
import net.quickwrite.fluent4j.container.FluentBundle;
import net.quickwrite.fluent4j.container.FluentBundleBuilder;
import net.quickwrite.fluent4j.container.FluentResource;
import net.quickwrite.fluent4j.iterator.FluentIteratorFactory;
import net.quickwrite.fluent4j.parser.ResourceParserBuilder;
import net.quickwrite.fluent4j.result.StringResultFactory;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.Message;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BanAnnouncementLanguageResourcesTest {

    @Test
    void everyLanguageResolvesBanBulletinLabels() throws IOException {
        for (Language language : Language.values()) {
            FluentBundle bundle = bundle(language);
            String prefix = resolve(bundle, Message.BAN_BROADCAST_PREFIX,
                    ArgumentListBuilder.builder().build());
            String operator = resolve(bundle, Message.BAN_BROADCAST_OPERATOR,
                    ArgumentListBuilder.builder().build());

            assertFalse(prefix.isBlank(), () -> language.id() + " has a blank ban bulletin prefix");
            assertFalse(operator.isBlank(), () -> language.id() + " has a blank operator label");
        }
    }

    @Test
    void simplifiedChineseUsesBanBulletinTitle() throws IOException {
        assertEquals("[封禁公报]", resolve(bundle(Language.ZH_CN), Message.BAN_BROADCAST_PREFIX,
                ArgumentListBuilder.builder().build()));
    }

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

    private FluentBundle bundle(Language language) throws IOException {
        String resourcePath = "lang/" + language.id() + ".ftl";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new AssertionError("Missing language resource " + resourcePath);
            }
            String source = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            FluentResource resource = ResourceParserBuilder.defaultParser()
                    .parse(FluentIteratorFactory.fromString(source));
            return FluentBundleBuilder.builder(language.locale())
                    .addResource(resource)
                    .addDefaultFunctions()
                    .build();
        }
    }

    private String resolve(
            FluentBundle bundle,
            Message message,
            net.quickwrite.fluent4j.ast.pattern.ArgumentList arguments
    ) {
        return bundle.resolveMessage(message.key(), arguments, StringResultFactory.construct())
                .map(Object::toString)
                .orElseThrow(() -> new AssertionError("Missing " + message.key()));
    }
}
