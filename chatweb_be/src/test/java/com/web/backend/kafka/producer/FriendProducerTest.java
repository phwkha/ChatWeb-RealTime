package com.web.backend.kafka.producer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import com.web.backend.common.NotificationsType;
import com.web.backend.kafka.payload.FriendPayload;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FriendProducerTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private FriendProducer friendProducer;

    @BeforeEach
    void setUp() throws Exception {
        Field topicField = FriendProducer.class.getDeclaredField("friendTopic");
        topicField.setAccessible(true);
        topicField.set(friendProducer, "test-friend-topic");

        CompletableFuture<SendResult<String, Object>> future = CompletableFuture
                .completedFuture(mock(SendResult.class, org.mockito.Mockito.RETURNS_DEEP_STUBS));
        when(kafkaTemplate.send(any(), any())).thenReturn(future);
    }

    @Test
    void testSendFriendNoti_Success() {
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
