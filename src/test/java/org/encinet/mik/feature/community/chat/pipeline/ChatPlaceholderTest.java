package org.encinet.mik.feature.community.chat.pipeline;

import org.encinet.mik.feature.community.chat.model.ChatContent;
import org.encinet.mik.feature.community.chat.model.ChatNode;
import org.encinet.mik.feature.community.chat.model.ChatPlaceholder;
import org.encinet.mik.feature.community.chat.model.ChatReferences;
import org.encinet.mik.feature.community.chat.model.ChatSubmission;
import org.encinet.mik.shared.i18n.FluentTestSupport;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.test.ChatTestMessages;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ChatPlaceholderTest {
    @Test
    void onlyTransportAuthoredSpansAreLocalizedAndSemanticNodesSurviveRouteStripping() {
        String body = "# [image] [image] https://example.org";
        var source = ChatTestMessages.minecraft("Alice", body).submission();
        var references = new ChatReferences(Optional.empty(), Optional.empty(), List.of(),
                List.of(new ChatPlaceholder.Span(2, 9, ChatPlaceholder.IMAGE)));
        var submission = new ChatSubmission(source.id(), source.origin(), source.sender(), source.conversation(),
                body, references, source.receivedAt(), source.processingContext());
        ChatContent content = new ChatProcessor().process(submission).content().withoutLeadingTrigger("#");
        assertInstanceOf(ChatNode.Placeholder.class, content.nodes().getFirst());
        var languages = FluentTestSupport.languages();
        assertTrue(content.localized(key -> languages.t(Language.ZH_CN, key)).plainText().startsWith("[图片] [image] "));
        assertTrue(content.localized(key -> languages.t(Language.DE_DE, key)).plainText().startsWith("[Bild] [image] "));
        assertEquals(1, content.nodes().stream().filter(ChatNode.Link.class::isInstance).count());
        assertTrue(content.plainText().startsWith("[image] [image] "));
        assertEquals("https://example.org", ((ChatNode.Link) content.nodes().getLast()).target().toString());
    }

    @Test
    void translatedDescriptionsRemainAtomicUnderPlatformLengthLimits() {
        var languages = FluentTestSupport.languages();
        var content = new ChatContent(List.of(new ChatNode.Placeholder(ChatPlaceholder.HISTORY,
                org.encinet.mik.feature.community.chat.model.ChatStyle.EMPTY)))
                .localized(key -> languages.t(Language.FR_FR, key));
        var message = ChatTestMessages.minecraft("Alice", "[history]", content);
        String matrix = org.encinet.mik.feature.community.social.platform.matrix.render.MatrixChatRenderer.render(message, 16).plainText();
        String qq = org.encinet.mik.feature.community.social.platform.qq.render.QqChatRenderer.render(message, 16).content();
        assertEquals("Alice: …", matrix);
        assertEquals("Alice: …", qq);
    }

    @Test
    void outOfRangeAndOverlappingTransportSpansAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> ChatPlaceholder.validate(List.of(
                new ChatPlaceholder.Span(0, 7, ChatPlaceholder.IMAGE)), 6));
        assertThrows(IllegalArgumentException.class, () -> ChatPlaceholder.validate(List.of(
                new ChatPlaceholder.Span(0, 7, ChatPlaceholder.IMAGE),
                new ChatPlaceholder.Span(5, 10, ChatPlaceholder.FACE)), 10));
    }
}
