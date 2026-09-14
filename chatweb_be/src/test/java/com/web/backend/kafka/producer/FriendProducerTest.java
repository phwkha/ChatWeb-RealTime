package com.web.backend.kafka.producer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import com.web.backend.common.NotificationsType;
import com.web.backend.kafka.payload.FriendPayload;

@ExtendWith(MockitoExtension.class)
class FriendProducerTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private FriendProducer friendProducer;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(friendProducer, "friendTopic", "test-friend-topic");
    }

    @Test
    void testSendFriendNoti_Success() {
        @SuppressWarnings("unchecked")
        SendResult<String, Object> sendResult = mock(SendResult.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(sendResult);
        when(kafkaTemplate.send(any(), any())).thenReturn(future);

        FriendPayload payload = FriendPayload.builder()
                .senderUsername("user1")
                .senderDisplayName("User 1")
                .recipientUsernames(List.of("friend1", "friend2"))
                .recipientType(NotificationsType.USER_ONLINE)
                .build();

        friendProducer.sendFriendNoti(payload);

        verify(kafkaTemplate).send(eq("test-friend-topic"), eq(payload));
    }

    @Test
    void testSendFriendNoti_NullPayload() {
        friendProducer.sendFriendNoti(null);

        verify(kafkaTemplate, never()).send(any(), any());
    }

    @Test
    void testSendFriendNoti_KafkaFailure() {
        CompletableFuture<SendResult<String, Object>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka error"));
        when(kafkaTemplate.send(any(), any())).thenReturn(failedFuture);

        FriendPayload payload = FriendPayload.builder()
                .senderUsername("user1")
                .recipientUsername("friend1")
                .recipientType(NotificationsType.FRIEND_REQUEST)
                .build();

        friendProducer.sendFriendNoti(payload);

        verify(kafkaTemplate).send(eq("test-friend-topic"), eq(payload));
    }
}
