package com.web.backend.kafka.consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.web.backend.common.ActionType;
import com.web.backend.common.NotificationsType;
import com.web.backend.config.localresolverconfig.Translator;
import com.web.backend.controller.response.ChatMessageResponse;
import com.web.backend.controller.response.NotificationResponse;
import com.web.backend.kafka.avro.ChatMessageAvro;
import com.web.backend.mapper.MessageMapper;
import com.web.backend.service.WebSocketRoutingService;

@ExtendWith(MockitoExtension.class)
class ChatConsumerTest {

    @Mock
    private SimpMessagingTemplate simpMessagingTemplate;

    @Mock
    private MessageMapper messageMapper;

    @Mock
    private WebSocketRoutingService webSocketRoutingService;

    @InjectMocks
    private ChatConsumer chatConsumer;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messageSource = mock(ResourceBundleMessageSource.class);
        lenient().when(messageSource.getMessage(anyString(), any(), any())).thenReturn("Mocked Notification");
        Translator.setStaticMessageSource(messageSource);
    }

    @Test
    void testListenChatMessages_CreateAction_RoutesToQueueMessages() throws Exception {
        ChatMessageAvro message = new ChatMessageAvro();
        message.setId("msg1");
        message.setSender("userA");
        message.setRecipient("userB");
        message.setContent("Hello World");
        message.setActionType(ActionType.CREATE.name());

        ChatMessageResponse response = ChatMessageResponse.builder()
                .id("msg1")
                .sender("userA")
                .recipient("userB")
                .content("Hello World")
                .build();

        when(messageMapper.avroToResponse(message)).thenReturn(response);

        chatConsumer.listenChatMessages(message, "conv_key");

        verify(webSocketRoutingService).routeMessage("userB", "/queue/messages", response);
        verify(webSocketRoutingService).routeMessage("userA", "/queue/messages", response);
    }

    @Test
    void testListenChatMessages_NullAction_DefaultsToCreateAndRoutesToQueueMessages() throws Exception {
        ChatMessageAvro message = new ChatMessageAvro();
        message.setId("msg1");
        message.setSender("userA");
        message.setRecipient("userB");
        message.setContent("Hello World");
        message.setActionType(null);

        ChatMessageResponse response = ChatMessageResponse.builder()
                .id("msg1")
                .sender("userA")
                .recipient("userB")
                .content("Hello World")
                .build();

        when(messageMapper.avroToResponse(message)).thenReturn(response);

        chatConsumer.listenChatMessages(message, "conv_key");

        verify(webSocketRoutingService).routeMessage("userB", "/queue/messages", response);
        verify(webSocketRoutingService).routeMessage("userA", "/queue/messages", response);
    }

    @Test
    void testListenChatMessages_EditAction_RoutesToQueueNotifications() throws Exception {
        ChatMessageAvro message = new ChatMessageAvro();
        message.setId("msg1");
        message.setSender("userA");
        message.setRecipient("userB");
        message.setContent("Edited Content");
        message.setActionType(ActionType.EDIT.name());

        ChatMessageResponse response = ChatMessageResponse.builder()
                .id("msg1")
                .sender("userA")
                .recipient("userB")
                .content("Edited Content")
                .isEdited(true)
                .build();

        when(messageMapper.avroToResponse(message)).thenReturn(response);

        chatConsumer.listenChatMessages(message, "conv_key");

        verify(webSocketRoutingService).routeMessage(eq("userA"), eq("/queue/notifications"),
                argThat((NotificationResponse<?> notif) -> notif.getType() == NotificationsType.EDIT_MESSAGE));
        verify(webSocketRoutingService).routeMessage(eq("userB"), eq("/queue/notifications"),
                argThat((NotificationResponse<?> notif) -> notif.getType() == NotificationsType.EDIT_MESSAGE));
    }

    @Test
    void testListenChatMessages_RevokeAction_RoutesToQueueNotifications() throws Exception {
        ChatMessageAvro message = new ChatMessageAvro();
        message.setId("msg1");
        message.setSender("userA");
        message.setRecipient("userB");
        message.setContent("");
        message.setActionType(ActionType.REVOKE.name());

        ChatMessageResponse response = ChatMessageResponse.builder()
                .id("msg1")
                .sender("userA")
                .recipient("userB")
                .isDeleted(true)
                .build();

        when(messageMapper.avroToResponse(message)).thenReturn(response);

        chatConsumer.listenChatMessages(message, "conv_key");

        verify(webSocketRoutingService).routeMessage(eq("userA"), eq("/queue/notifications"),
                argThat((NotificationResponse<?> notif) -> notif.getType() == NotificationsType.REVOKE_MESSAGE));
        verify(webSocketRoutingService).routeMessage(eq("userB"), eq("/queue/notifications"),
                argThat((NotificationResponse<?> notif) -> notif.getType() == NotificationsType.REVOKE_MESSAGE));
    }

    @Test
    void testListenChatMessages_ReactAction_RoutesToQueueNotifications() throws Exception {
        ChatMessageAvro message = new ChatMessageAvro();
        message.setId("msg1");
        message.setSender("userA");
        message.setRecipient("userB");
        message.setActionType(ActionType.REACT.name());

        ChatMessageResponse response = ChatMessageResponse.builder()
                .id("msg1")
                .sender("userA")
                .recipient("userB")
                .isReacted(true)
                .build();

        when(messageMapper.avroToResponse(message)).thenReturn(response);

        chatConsumer.listenChatMessages(message, "conv_key");

        verify(webSocketRoutingService).routeMessage(eq("userA"), eq("/queue/notifications"),
                argThat((NotificationResponse<?> notif) -> notif.getType() == NotificationsType.REACT_MESSAGE));
        verify(webSocketRoutingService).routeMessage(eq("userB"), eq("/queue/notifications"),
                argThat((NotificationResponse<?> notif) -> notif.getType() == NotificationsType.REACT_MESSAGE));
    }
}
