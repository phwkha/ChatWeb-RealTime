package com.web.backend.kafka.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.retrytopic.SameIntervalTopicReuseStrategy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.web.backend.common.NotificationsType;
import com.web.backend.common.UpdateMessageType;
import com.web.backend.config.localresolverconfig.Translator;
import com.web.backend.controller.response.ChatMessageResponse;
import com.web.backend.controller.response.NotificationResponse;
import com.web.backend.controller.response.ReadReceiptResponse;
import com.web.backend.exception.custom.MessageProcessingException;
import com.web.backend.kafka.payload.UpdateMessagePayload;
import com.web.backend.mapper.MessageMapper;
import com.web.backend.model.mongodb.ChatMessage;
import com.web.backend.service.WebSocketRoutingService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j(topic = "CHAT-UPDATE-CONSUMER")
public class UpdateMessageConsumer {

    private final MessageMapper messageMapper;
    private final WebSocketRoutingService webSocketRoutingService;
    private final ObjectMapper objectMapper;

    private static final String QUEUE_NOTIFICATIONS_STRING = "/queue/notifications";

    private static final String SYS_MSG_EDIT_MESSAGE_STRING = "sys.msg.edit_message";
    private static final String SYS_MSG_REVOKE_MESSAGE_STRING = "sys.msg.revoke_message";
    private static final String SYS_MSG_REACT_MESSAGE_STRING = "sys.msg.react_message";
    private static final String SYS_MSG_STATUS_MESSAGE_STRING = "sys.msg.status_message";

    @RetryableTopic(attempts = "5", backoff = @Backoff(delay = 500), sameIntervalTopicReuseStrategy = SameIntervalTopicReuseStrategy.SINGLE_TOPIC, dltStrategy = DltStrategy.NO_DLT, autoCreateTopics = "true")
    @KafkaListener(topics = "${spring.kafka.topic.update-message.update}", groupId = "${spring.kafka.topic.update-message.group-id}", containerFactory = "jsonKafkaListenerContainerFactory")
    public void handleMessageUpdates(UpdateMessagePayload updateEvent) {
        if (updateEvent == null || updateEvent.updateEvent() == null) {
            return;
        }
        if (updateEvent.type() == UpdateMessageType.STATUS) {
            processStatusUpdate(updateEvent);
        } else {
            processMessageNotification(updateEvent);
        }
    }

    private void processMessageNotification(UpdateMessagePayload updateEvent) {
        ChatMessage msg = extractChatMessage(updateEvent);
        if (msg == null || msg.getId() == null) {
            return;
        }
        dispatchUpdateNotifications(updateEvent, msg);
    }

    private void processStatusUpdate(UpdateMessagePayload updateEvent) {
        ReadReceiptResponse receiptData = extractReadReceiptData(updateEvent);
        if (receiptData == null || receiptData.getConversationId() == null || receiptData.getReader() == null) {
            log.warn("Invalid read receipt status update event: {}", updateEvent);
            return;
        }

        String convId = receiptData.getConversationId();
        String reader = receiptData.getReader();
        String sender = receiptData.getSender();

        try {
            NotificationResponse<ReadReceiptResponse> notification = NotificationResponse.<ReadReceiptResponse>builder()
                    .type(NotificationsType.STATUS_MESSAGE)
                    .relatedUsername(reader)
                    .message(Translator.tolocale(SYS_MSG_STATUS_MESSAGE_STRING))
                    .data(receiptData)
                    .build();

            if (sender != null) {
                webSocketRoutingService.routeMessage(sender, QUEUE_NOTIFICATIONS_STRING, notification);
            }
            webSocketRoutingService.routeMessage(reader, QUEUE_NOTIFICATIONS_STRING, notification);
            log.debug("Dispatched read receipt notification for reader '{}' and sender '{}'", reader, sender);
        } catch (Exception e) {
            log.error("Failed to route read receipt WebSocket notification for conv '{}'", convId, e);
            throw new MessageProcessingException("Failed to process message in UpdateMessageConsumer", e);
        }
    }

    private ReadReceiptResponse extractReadReceiptData(UpdateMessagePayload updateEvent) {
        if (updateEvent == null || updateEvent.updateEvent() == null) {
            return null;
        }
        if (updateEvent.updateEvent() instanceof ReadReceiptResponse data) {
            return data;
        }
        return objectMapper.convertValue(updateEvent.updateEvent(), ReadReceiptResponse.class);
    }

    private ChatMessage extractChatMessage(UpdateMessagePayload updateEvent) {
        if (updateEvent == null || updateEvent.updateEvent() == null) {
            return null;
        }
        if (updateEvent.updateEvent() instanceof ChatMessage chatMessage) {
            return chatMessage;
        }
        return objectMapper.convertValue(updateEvent.updateEvent(), ChatMessage.class);
    }

    private void dispatchUpdateNotifications(UpdateMessagePayload updateEvent, ChatMessage updatedMsg) {
        try {
            NotificationResponse<?> response = buildResponse(updateEvent.relatedUsername(), updateEvent.type(),
                    updatedMsg);
            if (updatedMsg.getSender() != null) {
                webSocketRoutingService.routeMessage(updatedMsg.getSender(), QUEUE_NOTIFICATIONS_STRING, response);
            }
            if (updatedMsg.getRecipient() != null) {
                webSocketRoutingService.routeMessage(updatedMsg.getRecipient(), QUEUE_NOTIFICATIONS_STRING, response);
            }
            log.debug("Dispatched update notifications to sender '{}' and recipient '{}' for message '{}'",
                    updatedMsg.getSender(), updatedMsg.getRecipient(), updatedMsg.getId());
        } catch (Exception e) {
            log.error("Failed to route WebSocket update notification for message '{}'", updatedMsg.getId(), e);
            throw new MessageProcessingException("Failed to process message in UpdateMessageConsumer", e);
        }
    }

    private NotificationResponse<?> buildResponse(String relatedUsername, UpdateMessageType type,
            ChatMessage chatMessage) {
        NotificationResponse<ChatMessageResponse> response = new NotificationResponse<>();
        switch (type) {
            case EDIT:
                response.setType(NotificationsType.EDIT_MESSAGE);
                response.setMessage(Translator.tolocale(SYS_MSG_EDIT_MESSAGE_STRING));
                break;
            case REVOKE:
                response.setType(NotificationsType.REVOKE_MESSAGE);
                response.setMessage(Translator.tolocale(SYS_MSG_REVOKE_MESSAGE_STRING));
                break;
            case REACT:
                response.setType(NotificationsType.REACT_MESSAGE);
                response.setMessage(Translator.tolocale(SYS_MSG_REACT_MESSAGE_STRING));
                break;
            default:
                break;
        }
        response.setRelatedUsername(relatedUsername);
        if (chatMessage != null) {
            response.setData(messageMapper.toResponse(chatMessage));
        }
        return response;
    }
}
