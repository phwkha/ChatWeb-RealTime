package com.web.backend.kafka.producer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
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

import com.web.backend.common.UpdateMessageType;
import com.web.backend.controller.response.ReadReceiptResponse;
import com.web.backend.kafka.payload.UpdateMessagePayload;
import com.web.backend.model.mongodb.ChatMessage;

@ExtendWith(MockitoExtension.class)
class UpdateMessageProducerTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private UpdateMessageProducer updateMessageProducer;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(updateMessageProducer, "chatTopicUpdate", "chat-update-topic");
    }

    @Test
    void testHandleReadReceiptEvent() {
        @SuppressWarnings("unchecked")
        SendResult<String, Object> sendResult = mock(SendResult.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(sendResult);
        when(kafkaTemplate.send(any(), any())).thenReturn(future);

        ReadReceiptResponse data = ReadReceiptResponse.builder()
                .conversationId("conv1")
                .reader("reader1")
                .sender("sender1")
                .readTimestamp(Instant.now())
                .build();

        updateMessageProducer.handleReadReceiptEvent(data);

        verify(kafkaTemplate).send(eq("chat-update-topic"), any(UpdateMessagePayload.class));
    }

    @Test
    void testHandleReadReceiptEvent_NullData() {
        updateMessageProducer.handleReadReceiptEvent(null);

        verify(kafkaTemplate, never()).send(any(), any());
    }

    @Test
    void testHandleUpdateMessageEvent() {
        @SuppressWarnings("unchecked")
        SendResult<String, Object> sendResult = mock(SendResult.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(sendResult);
        when(kafkaTemplate.send(any(), any())).thenReturn(future);

        UpdateMessagePayload payload = UpdateMessagePayload.builder()
                .type(UpdateMessageType.EDIT)
                .relatedUsername("sender1")
                .updateEvent(new ChatMessage())
                .build();

        updateMessageProducer.handleUpdateMessageEvent(payload);

        verify(kafkaTemplate).send(eq("chat-update-topic"), eq(payload));
    }

    @Test
    void testSendUpdateMessage_NullPayload() {
        updateMessageProducer.sendUpdateMessage(null);

        verify(kafkaTemplate, never()).send(any(), any());
    }
}
