package com.web.backend.kafka.consumer;

import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.SameIntervalTopicReuseStrategy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import com.web.backend.common.NotificationsType;
import com.web.backend.common.UpdateMessageType;
import com.web.backend.controller.response.ChatMessageResponse;
import com.web.backend.controller.response.ErrorSocketResponse;
import com.web.backend.controller.response.NotificationResponse;
import com.web.backend.controller.response.ReadReceiptData;
import com.web.backend.config.localresolverconfig.Translator;
import com.web.backend.kafka.payload.UpdateMessagePayload;
import com.web.backend.mapper.MessageMapper;
import com.web.backend.model.mongo.ChatMessage;
import com.web.backend.service.WebSocketRoutingService;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.SQLException;
import java.net.SocketTimeoutException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j(topic = "CHAT-UPDATE-CONSUMER")
public class UpdateMessageConsumer {

    private final MessageMapper messageMapper;
    private final WebSocketRoutingService webSocketRoutingService;
    private final MongoTemplate mongoTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String QUEUE_NOTIFICATIONS_STRING = "/queue/notifications";
    private static final String QUEUE_ERRORS_STRING = "/queue/errors";
    private static final String CHAT_RECENT_HASH_STRING = "chat:recent:hash:";

    private static final String SYS_MSG_EDIT_MESSAGE_STRING = "sys.msg.edit_message";
    private static final String SYS_MSG_REVOKE_MESSAGE_STRING = "sys.msg.revoke_message";
    private static final String SYS_MSG_REACT_MESSAGE_STRING = "sys.msg.react_message";
    private static final String SYS_MSG_STATUS_MESSAGE_STRING = "sys.msg.status_message";
    private static final String ERROR_SYS_PROCESSING_REQ_STRING = "error.sys.processing_req";

    @RetryableTopic(attempts = "6", backoff = @Backoff(delay = 500), sameIntervalTopicReuseStrategy = SameIntervalTopicReuseStrategy.SINGLE_TOPIC, include = {
            SQLException.class, SocketTimeoutException.class, IllegalStateException.class })
    @KafkaListener(topics = "${spring.kafka.topic.update-message.update}", groupId = "${spring.kafka.topic.update-message.group-id}", containerFactory = "jsonKafkaListenerContainerFactory")
    public void handleMessageUpdates(UpdateMessagePayload updateEvent) {
        if (updateEvent != null && updateEvent.updateEvent() != null) {
            if (updateEvent.type() == UpdateMessageType.STATUS) {
                processStatusUpdate(updateEvent);
            } else {
                processMessageNotification(updateEvent);
            }
        }
    }

    private void processMessageNotification(UpdateMessagePayload updateEvent) {
        ChatMessage msg = extractChatMessage(updateEvent);
        if (msg == null || msg.getId() == null) {
            return;
        }
        syncMessageToRedis(msg);
        dispatchUpdateNotifications(updateEvent, msg);
    }

    private void processStatusUpdate(UpdateMessagePayload updateEvent) {
        ReadReceiptData receiptData = extractReadReceiptData(updateEvent);
        if (receiptData == null || receiptData.getConversationId() == null || receiptData.getReader() == null) {
            log.warn("Invalid read receipt status update event: {}", updateEvent);
            return;
        }

        String convId = receiptData.getConversationId();
        String reader = receiptData.getReader();
        String sender = receiptData.getSender();

        try {
            NotificationResponse<ReadReceiptData> notification = NotificationResponse.<ReadReceiptData>builder()
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
        }
    }

    private ReadReceiptData extractReadReceiptData(UpdateMessagePayload updateEvent) {
        if (updateEvent == null || updateEvent.updateEvent() == null) {
            return null;
        }
        if (updateEvent.updateEvent() instanceof ReadReceiptData data) {
            return data;
        }
        return objectMapper.convertValue(updateEvent.updateEvent(), ReadReceiptData.class);
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
            webSocketRoutingService.routeMessage(updatedMsg.getSender(), QUEUE_NOTIFICATIONS_STRING, response);
            webSocketRoutingService.routeMessage(updatedMsg.getRecipient(), QUEUE_NOTIFICATIONS_STRING, response);
            log.debug("Dispatched update notifications to sender '{}' and recipient '{}' for message '{}'",
                    updatedMsg.getSender(), updatedMsg.getRecipient(), updatedMsg.getId());
        } catch (Exception e) {
            log.error("Failed to route WebSocket update notification for message '{}'", updatedMsg.getId(), e);
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

    private void syncMessageToRedis(ChatMessage updatedMsg) {
        if (updatedMsg == null || updatedMsg.getConversationId() == null) {
            return;
        }
        try {
            String hashKey = CHAT_RECENT_HASH_STRING + updatedMsg.getConversationId();
            Boolean hasKey = redisTemplate.hasKey(hashKey);
            if (Boolean.TRUE.equals(hasKey)) {
                redisTemplate.opsForHash().put(hashKey, updatedMsg.getId(), updatedMsg);
                log.debug("Synced updated message '{}' to Redis cache", updatedMsg.getId());
            }
        } catch (Exception e) {
            log.warn("Failed to sync updated message '{}' to Redis cache", updatedMsg.getId(), e);
        }
    }

    @DltHandler
    public void handleChatDlt(UpdateMessagePayload updateEvent) {
        if (updateEvent == null) {
            return;
        }
        log.error("Dead Letter Topic: Failed to process update event [type='{}', user='{}'] after retries exhausted",
                updateEvent.type(), updateEvent.relatedUsername());

        notifyUserDltFailure(updateEvent.relatedUsername(), updateEvent.updateEvent());
        restoreRedisCacheFromDb(updateEvent);
    }

    private void notifyUserDltFailure(String user, Object request) {
        if (user == null) {
            return;
        }
        try {
            String errorMsg = Translator.tolocale(ERROR_SYS_PROCESSING_REQ_STRING);
            webSocketRoutingService.routeMessage(user, QUEUE_ERRORS_STRING,
                    ErrorSocketResponse.builder()
                            .message(errorMsg)
                            .request(request)
                            .build());
            log.debug("Dispatched update failure notification to user '{}' via WebSocket", user);
        } catch (Exception e) {
            log.error("Failed to route DLT error notification to user '{}'", user, e);
        }
    }

    private void restoreRedisCacheFromDb(UpdateMessagePayload updateEvent) {
        if (updateEvent == null || updateEvent.updateEvent() == null
                || updateEvent.type() == UpdateMessageType.STATUS) {
            return;
        }
        try {
            ChatMessage msg = extractChatMessage(updateEvent);
            if (msg == null || msg.getId() == null) {
                return;
            }
            ChatMessage dbMsg = mongoTemplate.findById(msg.getId(), ChatMessage.class);
            if (dbMsg == null || dbMsg.getConversationId() == null) {
                if (msg.getConversationId() != null) {
                    String hashKey = CHAT_RECENT_HASH_STRING + msg.getConversationId();
                    redisTemplate.opsForHash().delete(hashKey, msg.getId());
                    log.debug("Evicted ghost message '{}' from Redis cache during DLT reconciliation", msg.getId());
                }
                return;
            }
            String hashKey = CHAT_RECENT_HASH_STRING + dbMsg.getConversationId();
            if (Boolean.TRUE.equals(redisTemplate.hasKey(hashKey))) {
                redisTemplate.opsForHash().put(hashKey, dbMsg.getId(), dbMsg);
                log.debug("Restored Redis cache from DB for message '{}'", dbMsg.getId());
            }
        } catch (Exception e) {
            log.warn("Failed to reconcile Redis cache during DLT processing", e);
        }
    }
}
