package com.web.backend.kafka.consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
import com.web.backend.controller.response.SocketNotificationResponse;
import com.web.backend.controller.response.ReadReceiptResponse;
import com.web.backend.kafka.payload.UpdateMessagePayload;
import com.web.backend.service.WebSocketRoutingService;

@ExtendWith(MockitoExtension.class)
class UpdateMessageConsumerTest {

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
    void testHandleMessageUpdates_NullEvent() {
        updateMessageConsumer.handleMessageUpdates(null);
        verify(webSocketRoutingService, never()).routeMessageToSession(any(), any(), any());
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
                ArgumentMatchers.<SocketNotificationResponse<?>>any());
        verify(webSocketRoutingService).routeMessage(eq("recipient1"), eq("/queue/notifications"),
                ArgumentMatchers.<SocketNotificationResponse<?>>any());
    }
}
