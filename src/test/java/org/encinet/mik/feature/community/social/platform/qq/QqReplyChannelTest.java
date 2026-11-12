package org.encinet.mik.feature.community.social.platform.qq;

import org.encinet.mik.feature.community.social.api.SocialDeliveryResult;
import org.encinet.mik.feature.community.social.document.SocialDocument;
import org.encinet.mik.feature.community.social.platform.qq.client.QqOpenApiClient;
import org.encinet.mik.feature.community.social.platform.qq.gateway.QqGatewayGroupMessage;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class QqReplyChannelTest {
    @ParameterizedTest
    @EnumSource(SocialDeliveryResult.Status.class)
    void preparesWithoutIoAndFallsBackOnlyWhenTheImageWasNotSent(SocialDeliveryResult.Status imageStatus) {
        var api = mock(QqOpenApiClient.class);
        var event = mock(QqGatewayGroupMessage.class);
        when(event.groupOpenId()).thenReturn("group");
        when(event.messageId()).thenReturn("event");
        var imageResult = imageStatus == SocialDeliveryResult.Status.ACCEPTED ? SocialDeliveryResult.accepted()
                : new SocialDeliveryResult(imageStatus, java.util.Optional.of(new TimeoutException("network")));
        when(api.replyRemoteImage(anyString(), anyString(), anyInt(), any(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(imageResult));
        when(api.replyMarkdown(anyString(), anyString(), anyInt(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
        var document = SocialDocument.message(SocialDocument.Moderation.REQUIRED, SocialDocument.Tone.WARNING,
                SocialDocument.instruction("Unbind Alice. You may lose access. Confirm:", "/unbind confirm token"),
                SocialDocument.image("Avatar", URI.create("https://example.org/avatar.png"), 64, 64));
        var prepared = new QqReplyChannel(api, event).prepare(document, 500);
        verifyNoInteractions(api);
        assertEquals(document, prepared.document());
        var delivery = prepared.send();
        assertSame(delivery, prepared.send(), "a prepared reply cannot start a second send");
        assertEquals(imageStatus == SocialDeliveryResult.Status.NOT_SENT ? SocialDeliveryResult.Status.ACCEPTED : imageStatus,
                delivery.join().status());
        verify(api, times(1)).replyRemoteImage(anyString(), anyString(), anyInt(), any(), anyString());
        verify(api, times(imageStatus == SocialDeliveryResult.Status.NOT_SENT ? 1 : 0))
                .replyMarkdown(anyString(), anyString(), anyInt(), anyString());
    }
}
