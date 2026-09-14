package com.web.backend.kafka.consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ResourceBundleMessageSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.web.backend.common.UpdateMessageType;
import com.web.backend.config.localresolverconfig.Translator;
import com.web.backend.controller.response.ChatMessageResponse;
import com.web.backend.controller.response.NotificationResponse;
import com.web.backend.controller.response.ReadReceiptResponse;
import com.web.backend.kafka.payload.UpdateMessagePayload;
import com.web.backend.mapper.MessageMapper;
import com.web.backend.model.mongodb.ChatMessage;
import com.web.backend.service.WebSocketRoutingService;

@ExtendWith(MockitoExtension.class)
class UpdateMessageConsumerTest {

    @Mock
    private MessageMapper messageMapper;
    @Mock
    private WebSocketRoutingService webSocketRoutingService;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private UpdateMessageConsumer updateMessageConsumer;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messageSource = mock(ResourceBundleMessageSource.class);
        lenient().when(messageSource.getMessage(anyString(), any(), any())).thenReturn("Mocked Notification");
        Translator.setStaticMessageSource(messageSource);
    }

    @Test
    void testHandleMessageUpdates_Edit() throws Exception {
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setId("msg1");
        chatMessage.setSender("sender1");
        chatMessage.setRecipient("recipient1");
        chatMessage.setContent("New Content");

        ChatMessageResponse response = new ChatMessageResponse();
        response.setId("msg1");
        response.setContent("New Content");

        when(messageMapper.toResponse(chatMessage)).thenReturn(response);

        UpdateMessagePayload payload = UpdateMessagePayload.builder()
                .type(UpdateMessageType.EDIT)
                .relatedUsername("sender1")
                .updateEvent(chatMessage)
                .build();

        updateMessageConsumer.handleMessageUpdates(payload);

        verify(webSocketRoutingService).routeMessage(eq("sender1"), eq("/queue/notifications"),
                ArgumentMatchers.<NotificationResponse<?>>any());
        verify(webSocketRoutingService).routeMessage(eq("recipient1"), eq("/queue/notifications"),
                ArgumentMatchers.<NotificationResponse<?>>any());
    }

    @Test
    void testHandleMessageUpdates_Revoke() throws Exception {
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setId("msg1");
        chatMessage.setSender("sender1");
        chatMessage.setRecipient("recipient1");
        chatMessage.setDeleted(true);

        ChatMessageResponse response = new ChatMessageResponse();
        response.setId("msg1");

        when(messageMapper.toResponse(chatMessage)).thenReturn(response);

        UpdateMessagePayload payload = UpdateMessagePayload.builder()
                .type(UpdateMessageType.REVOKE)
                .relatedUsername("sender1")
                .updateEvent(chatMessage)
                .build();

        updateMessageConsumer.handleMessageUpdates(payload);

        verify(webSocketRoutingService).routeMessage(eq("sender1"), eq("/queue/notifications"),
                ArgumentMatchers.<NotificationResponse<?>>any());
        verify(webSocketRoutingService).routeMessage(eq("recipient1"), eq("/queue/notifications"),
                ArgumentMatchers.<NotificationResponse<?>>any());
    }

    @Test
    void testHandleMessageUpdates_React() throws Exception {
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setId("msg1");
        chatMessage.setSender("sender1");
        chatMessage.setRecipient("recipient1");

        ChatMessageResponse response = new ChatMessageResponse();
        response.setId("msg1");

        when(messageMapper.toResponse(chatMessage)).thenReturn(response);

        UpdateMessagePayload payload = UpdateMessagePayload.builder()
                .type(UpdateMessageType.REACT)
                .relatedUsername("sender1")
                .updateEvent(chatMessage)
                .build();

        updateMessageConsumer.handleMessageUpdates(payload);

        verify(webSocketRoutingService).routeMessage(eq("sender1"), eq("/queue/notifications"),
                ArgumentMatchers.<NotificationResponse<?>>any());
        verify(webSocketRoutingService).routeMessage(eq("recipient1"), eq("/queue/notifications"),
                ArgumentMatchers.<NotificationResponse<?>>any());
    }

    @Test
    void testHandleMessageUpdates_Status() throws Exception {
        ReadReceiptResponse data = ReadReceiptResponse.builder()
                .conversationId("sender1_recipient1")
                .reader("recipient1")
                .sender("sender1")
                .readTimestamp(Instant.now())
                .build();

        UpdateMessagePayload payload = UpdateMessagePayload.builder()
                .type(UpdateMessageType.STATUS)
                .relatedUsername("recipient1")
                .updateEvent(data)
                .build();

        updateMessageConsumer.handleMessageUpdates(payload);

        verify(webSocketRoutingService).routeMessage(eq("sender1"), eq("/queue/notifications"),
                ArgumentMatchers.<NotificationResponse<?>>any());
        verify(webSocketRoutingService).routeMessage(eq("recipient1"), eq("/queue/notifications"),
                ArgumentMatchers.<NotificationResponse<?>>any());
    }
}
