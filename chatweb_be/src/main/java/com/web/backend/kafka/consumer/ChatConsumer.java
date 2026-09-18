package com.web.backend.kafka.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.retrytopic.SameIntervalTopicReuseStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import com.web.backend.common.ActionType;
import com.web.backend.common.NotificationsType;
import com.web.backend.config.localresolverconfig.Translator;
import com.web.backend.controller.response.ChatMessageResponse;
import com.web.backend.controller.response.MessageSystemResponse;
import com.web.backend.controller.response.NotificationResponse;
import com.web.backend.exception.custom.MessageProcessingException;
import com.web.backend.kafka.avro.ChatMessageAvro;
import com.web.backend.mapper.MessageMapper;
import com.web.backend.model.mongodb.SystemMessage;
import com.web.backend.service.WebSocketRoutingService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j(topic = "CHAT-KAFKA-CONSUMER")
public class ChatConsumer {

    private final SimpMessagingTemplate simpMessagingTemplate;

    private final MessageMapper messageMapper;

    private final WebSocketRoutingService webSocketRoutingService;

    private static final String QUEUE_MESSAGES_STRING = "/queue/messages";

    private static final String QUEUE_NOTIFICATIONS_STRING = "/queue/notifications";

    private static final String TOPIC_PUBLIC_STRING = "/topic/public";

    private static final String SYS_MSG_EDIT_MESSAGE_STRING = "sys.msg.edit_message";
    private static final String SYS_MSG_REVOKE_MESSAGE_STRING = "sys.msg.revoke_message";
    private static final String SYS_MSG_REACT_MESSAGE_STRING = "sys.msg.react_message";

    @RetryableTopic(attempts = "5", backoff = @Backoff(delay = 200), sameIntervalTopicReuseStrategy = SameIntervalTopicReuseStrategy.SINGLE_TOPIC, dltStrategy = DltStrategy.NO_DLT, autoCreateTopics = "true")
    @KafkaListener(topics = "${spring.kafka.topic.chat.messages}", groupId = "${spring.kafka.topic.chat.messages-group-id}", containerFactory = "chatAvroListenerContainerFactory")
    public void listenChatMessages(
            @Payload ChatMessageAvro message,
            @Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String conversationKey) {
        if (message == null) {
            return;
        }
        String recipient = message.getRecipient();
        String sender = message.getSender();
        String actionTypeStr = message.getActionType();
        ActionType action = parseActionType(actionTypeStr);
        log.debug("Consumed chat message (Avro) [key='{}', action='{}']: sender='{}', recipient='{}'",
                conversationKey, action, sender, recipient);
        try {
            ChatMessageResponse messageResponse = messageMapper.avroToResponse(message);
            if (action == ActionType.CREATE) {
                webSocketRoutingService.routeMessage(recipient, QUEUE_MESSAGES_STRING, messageResponse);
                webSocketRoutingService.routeMessage(sender, QUEUE_MESSAGES_STRING, messageResponse);
                log.debug("Dispatched chat message to WebSocket sender '{}' and recipient '{}'", sender, recipient);
            } else {
                UpdateMetadata metadata = resolveUpdateMetadata(action);

                NotificationResponse<ChatMessageResponse> notification = NotificationResponse.<ChatMessageResponse>builder()
                        .type(metadata.type())
                        .relatedUsername(sender)
                        .message(Translator.tolocale(metadata.messageKey()))
                        .data(messageResponse)
                        .build();

                webSocketRoutingService.routeMessage(sender, QUEUE_NOTIFICATIONS_STRING, notification);
                webSocketRoutingService.routeMessage(recipient, QUEUE_NOTIFICATIONS_STRING, notification);
                log.debug("Dispatched update notification [action='{}'] to WebSocket sender '{}' and recipient '{}'",
                        action, sender, recipient);
            }
        } catch (Exception e) {
            log.error("Failed to route WebSocket chat message for sender '{}' and recipient '{}'", sender, recipient,
                    e);
            throw new MessageProcessingException("Failed to process message in ChatConsumer", e);
        }
    }

    private ActionType parseActionType(String actionStr) {
        if (actionStr == null || actionStr.isBlank()) {
            return ActionType.CREATE;
        }
        try {
            return ActionType.valueOf(actionStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ActionType.CREATE;
        }
    }

    private UpdateMetadata resolveUpdateMetadata(ActionType action) {
        return switch (action) {
            case REVOKE -> new UpdateMetadata(NotificationsType.REVOKE_MESSAGE, SYS_MSG_REVOKE_MESSAGE_STRING);
            case REACT -> new UpdateMetadata(NotificationsType.REACT_MESSAGE, SYS_MSG_REACT_MESSAGE_STRING);
            default -> new UpdateMetadata(NotificationsType.EDIT_MESSAGE, SYS_MSG_EDIT_MESSAGE_STRING);
        };
    }

    private record UpdateMetadata(NotificationsType type, String messageKey) {}

    @RetryableTopic(attempts = "5", backoff = @Backoff(delay = 200), sameIntervalTopicReuseStrategy = SameIntervalTopicReuseStrategy.SINGLE_TOPIC, dltStrategy = DltStrategy.NO_DLT, autoCreateTopics = "true")
    @KafkaListener(topics = "${spring.kafka.topic.chat.system-messages}", groupId = "${spring.kafka.topic.chat.system-messages-group-id}-${random.uuid}", containerFactory = "jsonKafkaListenerContainerFactory")
    public void listenSystemMessages(SystemMessage systemMessage) {
        if (systemMessage == null)
            return;

        MessageSystemResponse response = messageMapper.systemMessageToResponse(systemMessage);

        log.debug("Consumed system message from sender '{}'", response.getSender());

        try {
            simpMessagingTemplate.convertAndSend(TOPIC_PUBLIC_STRING, response);
            log.debug("Broadcasted system message to topic '{}'", TOPIC_PUBLIC_STRING);
        } catch (Exception e) {
            log.error("Failed to broadcast system message to '{}'", TOPIC_PUBLIC_STRING, e);
            throw new MessageProcessingException("Failed to process message in ChatConsumer", e);
        }
    }
}