package com.web.backend.kafka.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.SameIntervalTopicReuseStrategy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import com.web.backend.common.MessageStatus;
import com.web.backend.common.MessageType;
import com.web.backend.common.NotificationsType;
import com.web.backend.common.UpdateMessageType;
import com.web.backend.controller.response.ChatMessageResponse;
import com.web.backend.controller.response.NotificationResponse;
import com.web.backend.config.localresolverconfig.Translator;
import com.web.backend.kafka.payload.UpdateMessagePayload;
import com.web.backend.mapper.MessageMapper;
import com.web.backend.model.ChatMessage;
import com.web.backend.service.WebSocketRoutingService;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.RedisTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.SQLException;
import java.net.SocketTimeoutException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j(topic = "CHAT-UPDATE-CONSUMER")
public class ChatUpdateConsumer {

    private final MessageMapper messageMapper;
    private final WebSocketRoutingService webSocketRoutingService;
    private final MongoTemplate mongoTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String QUEUE_NOTIFICATIONS_STRING = "/queue/notifications";
    private static final String CHAT_RECENT_HASH_STRING = "chat:recent:hash:";

    private static final String SYS_MSG_EDIT_MESSAGE_STRING = "sys.msg.edit_message";
    private static final String SYS_MSG_REVOKE_MESSAGE_STRING = "sys.msg.revoke_message";
    private static final String SYS_MSG_REACT_MESSAGE_STRING = "sys.msg.react_message";
    private static final String SYS_MSG_STATUS_MESSAGE_STRING = "sys.msg.status_message";

    @RetryableTopic(attempts = "-1", backoff = @Backoff(delay = 5000), sameIntervalTopicReuseStrategy = SameIntervalTopicReuseStrategy.SINGLE_TOPIC, include = {
            SQLException.class, SocketTimeoutException.class, IllegalStateException.class })
    @KafkaListener(topics = "${spring.kafka.topic.update-message.update}", groupId = "${spring.kafka.topic.update-message.group-id}", containerFactory = "jsonKafkaListenerContainerFactory")
    public void handleMessageUpdates(UpdateMessagePayload updateEvent) {
        if (updateEvent.type() != UpdateMessageType.STATUS && updateEvent.updateEvent() != null) {
            processContentUpdate(updateEvent);
        } else {
            processStatusUpdate(updateEvent);
        }
    }

    private void processContentUpdate(UpdateMessagePayload updateEvent) {
        ChatMessage msg = extractChatMessage(updateEvent);

        Query query = new Query(Criteria.where("id").is(msg.getId()));
        if (updateEvent.type() == UpdateMessageType.EDIT) {
            query.addCriteria(Criteria.where("isDeleted").is(false));
        }

        Update update = buildMongoUpdate(updateEvent, msg);
        FindAndModifyOptions options = new FindAndModifyOptions().returnNew(true);
        ChatMessage updatedMsg = mongoTemplate.findAndModify(query, update, options, ChatMessage.class);

        if (updatedMsg == null) {
            handleMissingMessage(updateEvent, msg.getId());
            return;
        }

        log.debug("Updated message '{}' in MongoDB [type={}]", updatedMsg.getId(), updateEvent.type());
        syncMessageToRedis(updatedMsg);
        dispatchUpdateNotifications(updateEvent, updatedMsg);
    }

    private ChatMessage extractChatMessage(UpdateMessagePayload updateEvent) {
        if (updateEvent.updateEvent() instanceof ChatMessage chatMessage) {
            return chatMessage;
        }
        return objectMapper.convertValue(updateEvent.updateEvent(), ChatMessage.class);
    }

    private Update buildMongoUpdate(UpdateMessagePayload updateEvent, ChatMessage msg) {
        Update update = new Update();
        switch (updateEvent.type()) {
            case EDIT:
                update.set("content", msg.getContent());
                update.set("isEdited", true);
                if (msg.getIv() != null) {
                    update.set("iv", msg.getIv());
                }
                if (msg.getWrappedKeyRecipient() != null) {
                    update.set("wrappedKeyRecipient", msg.getWrappedKeyRecipient());
                }
                if (msg.getWrappedKeySender() != null) {
                    update.set("wrappedKeySender", msg.getWrappedKeySender());
                }
                break;
            case REVOKE:
                update.set("content", "");
                update.set("fileUrl", null);
                update.set("fileName", null);
                update.set("fileSize", null);
                update.set("reactions", null);
                update.set("iv", null);
                update.set("wrappedKeyRecipient", null);
                update.set("wrappedKeySender", null);
                update.set("isDeleted", true);
                break;
            case REACT:
                applyReactionUpdate(update, updateEvent.relatedUsername(), msg);
                break;
            default:
                break;
        }
        return update;
    }

    private void applyReactionUpdate(Update update, String reactor, ChatMessage msg) {
        String reactionType = (msg.getReactions() != null) ? msg.getReactions().get(reactor) : null;
        if (reactionType != null) {
            update.set("reactions." + reactor, reactionType);
            update.set("isReacted", true);
        } else {
            update.unset("reactions." + reactor);
            if (msg.getReactions() == null || msg.getReactions().isEmpty()) {
                update.set("isReacted", false);
            }
        }
    }

    private void handleMissingMessage(UpdateMessagePayload updateEvent, String messageId) {
        if (updateEvent.type() == UpdateMessageType.EDIT) {
            ChatMessage existing = mongoTemplate.findById(messageId, ChatMessage.class);
            if (existing != null && existing.isDeleted()) {
                log.warn("Ignoring edit on already revoked message '{}'", messageId);
                return;
            }
        }
        throw new IllegalStateException("Original message not found in DB yet, retrying update...");
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

    private void processStatusUpdate(UpdateMessagePayload updateEvent) {
        String reader = updateEvent.relatedUsername();
        String sender = (updateEvent.updateEvent() instanceof String r) ? r : null;
        if (reader == null || sender == null) {
            return;
        }

        try {
            Query query = new Query(Criteria.where("recipient").is(reader)
                    .and("sender").is(sender)
                    .and("status").is(MessageStatus.SENT)
                    .and("messageType").is(MessageType.CHAT));

            Update update = new Update().set("status", MessageStatus.READ);
            mongoTemplate.updateMulti(query, update, ChatMessage.class);

            log.debug("Updated read status in MongoDB for reader '{}' and sender '{}'", reader, sender);

            NotificationResponse<?> response = buildResponse(reader, updateEvent.type(), null);
            webSocketRoutingService.routeMessage(sender, QUEUE_NOTIFICATIONS_STRING, response);
            webSocketRoutingService.routeMessage(reader, QUEUE_NOTIFICATIONS_STRING, response);
            log.debug("Dispatched read status notifications for reader '{}' and sender '{}'", reader, sender);
        } catch (Exception e) {
            log.error("Failed to process read status update for reader '{}' and sender '{}'", reader,
                    sender, e);
        }
    }

    private NotificationResponse<?> buildResponse(String relatedUsername, UpdateMessageType type,
            ChatMessage chatMessage) {
        if (type != UpdateMessageType.STATUS && chatMessage != null) {
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
            response.setData(messageMapper.toResponse(chatMessage));
            return response;
        } else {
            return NotificationResponse.notificationData(NotificationsType.STATUS_MESSAGE, relatedUsername,
                    Translator.tolocale(SYS_MSG_STATUS_MESSAGE_STRING));
        }
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

}
