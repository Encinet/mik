package org.encinet.mik.module.ban;

import net.quickwrite.fluent4j.container.ArgumentListBuilder;
import net.quickwrite.fluent4j.container.FluentBundle;
import net.quickwrite.fluent4j.container.FluentBundleBuilder;
import net.quickwrite.fluent4j.container.FluentResource;
import net.quickwrite.fluent4j.iterator.FluentIteratorFactory;
import net.quickwrite.fluent4j.parser.ResourceParserBuilder;
import net.quickwrite.fluent4j.result.StringResultFactory;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.Message;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BanDialogControllerTest {

    private static final List<Message> BAN_DIALOG_MESSAGES = Arrays.stream(Message.values())
            .filter(message -> message.name().startsWith("BAN_DIALOG_")
                    || message.name().startsWith("BAN_SEVERITY_")
                    || message == Message.BAN_REASON_REQUIRED)
            .toList();

    @Test
    void acceptsPaperDialogInputNames() {
        assertDoesNotThrow(() -> DialogInputKeys.requireValid("manual_player"));
        assertDoesNotThrow(() -> DialogInputKeys.requireValid("online.player"));
    }

    @Test
    void rejectsHyphenatedInputNames() {
        assertThrows(IllegalArgumentException.class,
                () -> DialogInputKeys.requireValid("manual-player"));
    }

    @Test
    void onlyPlayersWhoNeverJoinedNeedAnotherConfirmation() {
        assertTrue(BanDialogController.needsNeverJoinedConfirmation(false));
        assertFalse(BanDialogController.needsNeverJoinedConfirmation(true));
    }

    @Test
    void everyLanguageResolvesEveryBanDialogMessage() throws IOException {
        assertFalse(BAN_DIALOG_MESSAGES.isEmpty());
        var arguments = ArgumentListBuilder.builder().add("arg0", "TestPlayer").build();

        for (Language language : Language.values()) {
            FluentBundle bundle = FluentBundleBuilder.builder(language.locale())
                    .addResource(resource(language))
                    .addDefaultFunctions()
                    .build();
            for (Message message : BAN_DIALOG_MESSAGES) {
                String rendered = bundle.resolveMessage(message.key(), arguments, StringResultFactory.construct())
                        .map(Object::toString)
                        .orElseThrow(() -> new AssertionError(
                                language.id() + " is missing " + message.key()));
                assertFalse(rendered.isBlank(),
                        () -> language.id() + " has a blank " + message.key());
            }
        }
    }

    private FluentResource resource(Language language) throws IOException {
        String resourcePath = "lang/" + language.id() + ".ftl";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            assertTrue(input != null, () -> "Missing language resource " + resourcePath);
            String source = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            return ResourceParserBuilder.defaultParser().parse(FluentIteratorFactory.fromString(source));
        }
    }
}
