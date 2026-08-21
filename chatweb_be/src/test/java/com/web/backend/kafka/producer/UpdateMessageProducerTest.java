package com.web.backend.kafka.producer;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import com.web.backend.controller.response.ReadReceiptData;

@ExtendWith(MockitoExtension.class)
class UpdateMessageProducerTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private UpdateMessageProducer updateMessageProducer;

    @BeforeEach
    void setUp() throws Exception {
        Field topicField = UpdateMessageProducer.class.getDeclaredField("chatTopicUpdate");
        topicField.setAccessible(true);
        topicField.set(updateMessageProducer, "chat-update-topic");

        CompletableFuture<SendResult<String, Object>> future = CompletableFuture
                .completedFuture(mock(SendResult.class, org.mockito.Mockito.RETURNS_DEEP_STUBS));
        when(kafkaTemplate.send(any(), any())).thenReturn(future);
    }

    @Test
    void testHandleReadReceiptEvent() {
        ReadReceiptData data = ReadReceiptData.builder()
                .conversationId("conv1")
                .reader("reader1")
                .sender("sender1")
                .readTimestamp(LocalDateTime.now())
                .build();

        updateMessageProducer.handleReadReceiptEvent(data);

        verify(kafkaTemplate).send(eq("chat-update-topic"), any());
    }

    @Test
    void testSendEditMessage() {
        CompletableFuture<?> future = updateMessageProducer.sendEditMessage("editPayload");
        assertNotNull(future);
        verify(kafkaTemplate).send(eq("chat-update-topic"), eq("editPayload"));
    }

    @Test
    void testSendRevokeMessage() {
        CompletableFuture<?> future = updateMessageProducer.sendRevokeMessage("revokePayload");
        assertNotNull(future);
        verify(kafkaTemplate).send(eq("chat-update-topic"), eq("revokePayload"));
    }

    @Test
    void testSendReaction() {
        CompletableFuture<?> future = updateMessageProducer.sendReaction("reactionPayload");
        assertNotNull(future);
        verify(kafkaTemplate).send(eq("chat-update-topic"), eq("reactionPayload"));
    }

    @Test
    void testHandleUpdateMessageEvent() {
        com.web.backend.kafka.payload.UpdateMessagePayload payload = com.web.backend.kafka.payload.UpdateMessagePayload.builder()
                .type(com.web.backend.common.UpdateMessageType.EDIT)
                .relatedUsername("sender1")
                .updateEvent(new com.web.backend.model.mongo.ChatMessage())
                .build();

        updateMessageProducer.handleUpdateMessageEvent(payload);

        verify(kafkaTemplate).send(eq("chat-update-topic"), eq(payload));
    }
}
