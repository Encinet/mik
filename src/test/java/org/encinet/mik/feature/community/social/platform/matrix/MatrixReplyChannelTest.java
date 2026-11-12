package org.encinet.mik.feature.community.social.platform.matrix;

import org.encinet.mik.feature.community.social.api.SocialDeliveryResult;
import org.encinet.mik.feature.community.social.document.SocialDocument;
import org.encinet.mik.feature.community.social.platform.matrix.client.MatrixClient;
import org.encinet.mik.feature.community.social.platform.matrix.sync.MatrixRoomMessage;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MatrixReplyChannelTest {
    @ParameterizedTest
    @EnumSource(SocialDeliveryResult.Status.class)
    void preparesWithoutIoAndNeverSendsTextAfterAnUncertainImage(SocialDeliveryResult.Status imageStatus) {
        var client = mock(MatrixClient.class);
        var event = mock(MatrixRoomMessage.class);
        when(event.roomId()).thenReturn("!room:example.org");
        when(event.eventId()).thenReturn("$event");
        when(event.threadRootEventId()).thenReturn(Optional.empty());
        var imageResult = imageStatus == SocialDeliveryResult.Status.ACCEPTED ? SocialDeliveryResult.accepted()
                : new SocialDeliveryResult(imageStatus, Optional.of(new TimeoutException("network")));
        when(client.replyImage(anyString(), anyString(), any(), anyString(), any(), anyInt(), anyInt(), anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(imageResult));
        when(client.replyText(anyString(), anyString(), any(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
        var document = SocialDocument.message(SocialDocument.Moderation.REQUIRED, SocialDocument.Tone.INFO,
                SocialDocument.paragraph("Player Alice"), SocialDocument.image("Avatar", "image/png", new byte[]{1}, 1, 1));
        var prepared = new MatrixReplyChannel(client, event).prepare(document, 500);
        verifyNoInteractions(client);
        assertEquals(document, prepared.document());
        var delivery = prepared.send();
        assertSame(delivery, prepared.send());
        assertEquals(imageStatus == SocialDeliveryResult.Status.NOT_SENT ? SocialDeliveryResult.Status.ACCEPTED : imageStatus,
                delivery.join().status());
        verify(client, times(1)).replyImage(anyString(), anyString(), any(), anyString(), any(), anyInt(), anyInt(), anyString(), anyString(), anyString());
        verify(client, times(imageStatus == SocialDeliveryResult.Status.NOT_SENT ? 1 : 0)).replyText(anyString(), anyString(), any(), anyString(), anyString());
    }
}
