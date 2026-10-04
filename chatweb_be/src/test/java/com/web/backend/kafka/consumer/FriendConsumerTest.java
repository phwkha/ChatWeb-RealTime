package com.web.backend.kafka.consumer;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ResourceBundleMessageSource;

import com.web.backend.common.NotificationsType;
import com.web.backend.config.localresolverconfig.Translator;
import com.web.backend.controller.response.SocketNotificationResponse;
import com.web.backend.exception.custom.MessageProcessingException;
import com.web.backend.kafka.payload.FriendPayload;
import com.web.backend.service.ConsumerDeduplicationService;
import com.web.backend.service.WebSocketRoutingService;

@ExtendWith(MockitoExtension.class)
class FriendConsumerTest {

    @Mock
    private WebSocketRoutingService webSocketRoutingService;

    @Mock
    private ConsumerDeduplicationService dedupService;

    @InjectMocks
    private FriendConsumer friendConsumer;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messageSource = mock(ResourceBundleMessageSource.class);
        lenient().when(messageSource.getMessage(anyString(), any(), any())).thenReturn("Mocked Notification Message");
        Translator.setStaticMessageSource(messageSource);
    }

    @Test
    void testListenFriendNotifications_NullEvent() throws Exception {
        friendConsumer.listenFriendNotifications(null);
        verify(webSocketRoutingService, never()).routeMessage(any(), any(), any());
    }

    @Test
    void testListenFriendNotifications_FriendRequest() throws Exception {
        FriendPayload payload = FriendPayload.builder()
                .notificationId(101L)
                .senderUsername("sender_user")
                .senderDisplayName("Sender User")
                .recipientUsername("recipient_user")
                .recipientDisplayName("Recipient User")
                .recipientType(NotificationsType.FRIEND_REQUEST)
                .senderType(NotificationsType.REQUEST_SENT_SUCCESS)
                .build();

        friendConsumer.listenFriendNotifications(payload);

        verify(webSocketRoutingService).routeMessage(
                eq("recipient_user"),
                eq("/queue/notifications"),
                argThat((SocketNotificationResponse<?> resp) -> resp != null && Long.valueOf(101L).equals(resp.getId()))
        );
        verify(webSocketRoutingService).routeMessage(
                eq("sender_user"),
                eq("/queue/notifications"),
                argThat((SocketNotificationResponse<?> resp) -> resp != null && resp.getId() == null)
        );
    }

    @Test
    void testListenFriendNotifications_FriendAccepted() throws Exception {
        FriendPayload payload = FriendPayload.builder()
                .notificationId(102L)
                .senderUsername("sender_user")
                .senderDisplayName("Sender User")
                .recipientUsername("recipient_user")
                .recipientDisplayName("Recipient User")
                .recipientType(NotificationsType.FRIEND_ACCEPTED)
                .senderType(NotificationsType.YOU_ACCEPTED)
                .build();

        friendConsumer.listenFriendNotifications(payload);

        verify(webSocketRoutingService).routeMessage(
                eq("recipient_user"),
                eq("/queue/notifications"),
                argThat((SocketNotificationResponse<?> resp) -> resp != null && Long.valueOf(102L).equals(resp.getId()))
        );
        verify(webSocketRoutingService).routeMessage(
                eq("sender_user"),
                eq("/queue/notifications"),
                argThat((SocketNotificationResponse<?> resp) -> resp != null && resp.getId() == null)
        );
    }

    @Test
    void testListenFriendNotifications_MultipleRecipients() throws Exception {
        FriendPayload payload = FriendPayload.builder()
                .senderUsername("sender_user")
                .senderDisplayName("Sender User")
                .recipientUsernames(List.of("user_a", "user_b"))
                .recipientType(NotificationsType.USER_ONLINE)
                .build();

        friendConsumer.listenFriendNotifications(payload);

        verify(webSocketRoutingService).routeMessage(eq("user_a"), eq("/queue/notifications"), any());
        verify(webSocketRoutingService).routeMessage(eq("user_b"), eq("/queue/notifications"), any());
    }

    @Test
    void testListenFriendNotifications_UnfriendedAndCancelled() throws Exception {
        FriendPayload payloadUnfriend = FriendPayload.builder()
                .senderUsername("sender_user")
                .recipientUsername("recipient_user")
                .recipientType(NotificationsType.UNFRIENDED)
                .senderType(NotificationsType.REQUEST_CANCELLED)
                .build();

        friendConsumer.listenFriendNotifications(payloadUnfriend);

        verify(webSocketRoutingService).routeMessage(eq("recipient_user"), eq("/queue/notifications"), any());
        verify(webSocketRoutingService).routeMessage(eq("sender_user"), eq("/queue/notifications"), any());
    }

    @Test
    void testListenFriendNotifications_RejectedAndOffline() throws Exception {
        FriendPayload payload = FriendPayload.builder()
                .senderUsername("sender_user")
                .recipientUsername("recipient_user")
                .recipientType(NotificationsType.REQUEST_REJECTED)
                .senderType(NotificationsType.USER_OFFLINE)
                .build();

        friendConsumer.listenFriendNotifications(payload);

        verify(webSocketRoutingService).routeMessage(eq("recipient_user"), eq("/queue/notifications"), any());
        verify(webSocketRoutingService).routeMessage(eq("sender_user"), eq("/queue/notifications"), any());
    }

    @Test
    void testListenFriendNotifications_RoutingException_ThrowsMessageProcessingException() throws Exception {
        FriendPayload payload = FriendPayload.builder()
                .senderUsername("sender_user")
                .recipientUsername("recipient_user")
                .recipientType(NotificationsType.UNFRIENDED)
                .build();

        doThrow(new RuntimeException("Simulated socket error"))
                .when(webSocketRoutingService).routeMessage(anyString(), anyString(), any());

        assertThrows(MessageProcessingException.class, () -> friendConsumer.listenFriendNotifications(payload));
    }

    @Test
    void testListenFriendNotifications_RetryAfterRoutingFailure_PurelyStatelessAndIdempotent() throws Exception {
        FriendPayload payload = FriendPayload.builder()
                .notificationId(101L)
                .senderUsername("sender_user")
                .senderDisplayName("Sender User")
                .recipientUsername("recipient_user")
                .recipientDisplayName("Recipient User")
                .recipientType(NotificationsType.FRIEND_REQUEST)
                .senderType(NotificationsType.REQUEST_SENT_SUCCESS)
                .build();

        doThrow(new RuntimeException("Simulated socket error"))
                .doNothing()
                .when(webSocketRoutingService).routeMessage(eq("recipient_user"), eq("/queue/notifications"), any());

        assertThrows(MessageProcessingException.class, () -> friendConsumer.listenFriendNotifications(payload));

        friendConsumer.listenFriendNotifications(payload);

        verify(webSocketRoutingService, times(2)).routeMessage(
                eq("recipient_user"),
                eq("/queue/notifications"),
                any()
        );
        verify(dedupService).clearOnFailure("friend_push", payload.eventId());
    }

    @Test
    void testListenFriendNotifications_DuplicateEvent_Skipped() throws Exception {
        FriendPayload payload = FriendPayload.builder()
                .notificationId(101L)
                .senderUsername("sender_user")
                .recipientUsername("recipient_user")
                .recipientType(NotificationsType.FRIEND_REQUEST)
                .build();

        org.mockito.Mockito.when(dedupService.isDuplicate(eq("friend_push"), eq(payload.eventId()), any(java.time.Duration.class)))
                .thenReturn(true);

        friendConsumer.listenFriendNotifications(payload);

        verify(webSocketRoutingService, never()).routeMessage(any(), any(), any());
    }
}
