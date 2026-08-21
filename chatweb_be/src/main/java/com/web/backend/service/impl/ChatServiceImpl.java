package com.web.backend.service.impl;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import org.bson.types.ObjectId;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.web.backend.common.ContentType;
import com.web.backend.common.MessageStatus;
import com.web.backend.common.MessageType;
import com.web.backend.common.UserStatus;
import com.web.backend.config.localresolverconfig.Translator;
import com.web.backend.controller.request.ChatMessageRequest;
import com.web.backend.controller.request.MessageSystemRequest;
import com.web.backend.exception.WebSocketErrorHandler;
import com.web.backend.exception.custom.AccessForbiddenException;
import com.web.backend.exception.custom.InvalidDataException;
import com.web.backend.exception.custom.ResourceNotFoundException;
import com.web.backend.exception.custom.SystemOverloadException;
import com.web.backend.kafka.avro.ChatMessageAvro;
import com.web.backend.kafka.producer.ChatProducer;
import com.web.backend.mapper.MessageMapper;
import com.web.backend.model.mongo.ChatMessage;
import com.web.backend.model.mongo.SystemMessage;
import com.web.backend.model.postgres.UserEntity;
import com.web.backend.repository.SystemMessageRepository;
import com.web.backend.repository.UserRepository;
import com.web.backend.service.ChatService;
import com.web.backend.service.FriendService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j(topic = "CHAT-SERVICE")
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final UserRepository userRepository;
    private final SystemMessageRepository systemMessageRepository;
    private final FriendService friendService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final MessageMapper messageMapper;
    private final ChatProducer chatProducer;
    private final WebSocketErrorHandler webSocketErrorHandler;

    private static final String CHAT_RECENT_HASH_STRING = "chat:recent:hash:";
    private static final String CHAT_RECENT_ZSET_STRING = "chat:recent:zset:";
    private static final String UNREAD_COUNTS_STRING = "unread_counts:";

    private static final String ERROR_MSG_RECIPIENT_NOT_FOUND_STRING = "error.msg.recipient_not_found";
    private static final String ERROR_MSG_SEND_DELETED_STRING = "error.msg.send_deleted";
    private static final String ERROR_MSG_SEND_LOCKED_STRING = "error.msg.send_locked";
    private static final String ERROR_MSG_NOT_FRIENDS_STRING = "error.msg.not_friends";
    private static final String ERROR_MSG_SYSTEM_OVERLOAD_STRING = "error.msg.system_overload";
    private static final String ERROR_MSG_SELF_SEND_STRING = "error.msg.self_send";
    private static final String ERROR_MSG_EMPTY_CONTENT_STRING = "error.msg.empty_content";

    @Override
    public void sendPrivateMessage(String sender, ChatMessageRequest request) {
        validatePrivateMessageRequest(sender, request);
        String convId = generateConversationId(sender, request.getRecipient());
        ChatMessage chatMsg = buildChatMessage(sender, request, convId);
        try {
            if (chatMsg.getMessageType() == MessageType.CHAT) {
                cacheMessageToRedis(chatMsg);
            }
            ChatMessageAvro payload = messageMapper.toAvro(chatMsg);
            payload.setLocalId(request.getLocalId());
            chatProducer.sendChatMessage(payload).whenComplete((result, ex) -> {
                if (ex != null) {
                    if (chatMsg.getMessageType() == MessageType.CHAT) {
                        log.error("Failed to publish message '{}' to Kafka", chatMsg.getId(), ex);
                        rollbackRedisCache(convId, chatMsg);
                        webSocketErrorHandler.handleChatError(sender, request,
                                Translator.tolocale(ERROR_MSG_SYSTEM_OVERLOAD_STRING));
                    }
                } else if (chatMsg.getMessageType() == MessageType.CHAT) {
                    log.debug("Published message '{}' to Kafka successfully", chatMsg.getId());
                }
            });
        } catch (Exception syncEx) {
            log.error("Synchronous error publishing message '{}' to Kafka", chatMsg.getId(), syncEx);
            if (chatMsg.getMessageType() == MessageType.CHAT) {
                rollbackRedisCache(convId, chatMsg);
            }
            throw new SystemOverloadException(Translator.tolocale(ERROR_MSG_SYSTEM_OVERLOAD_STRING), request, syncEx);
        }
    }

    @Override
    public void sendSystemMessage(String currentUsername, MessageSystemRequest request) {
        SystemMessage systemMsg = new SystemMessage();
        systemMsg.setSender(currentUsername);
        systemMsg.setTimestamp(Instant.now());
        systemMsg.setExpiresAt(request.getSurvivalTime() == null ? null
                : Instant.now().plus(request.getSurvivalTime(), ChronoUnit.SECONDS));
        systemMsg.setContent(request.getContent());

        try {
            systemMessageRepository.save(Objects.requireNonNull(systemMsg));
            chatProducer.sendSystemMessage(systemMsg)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish system message to Kafka for user '{}'", currentUsername, ex);
                            webSocketErrorHandler.handleChatError(currentUsername, request,
                                    Translator.tolocale(ERROR_MSG_SYSTEM_OVERLOAD_STRING));
                        }
                    });
        } catch (Exception e) {
            log.error("Failed to save system message to database for user '{}'", currentUsername, e);
            webSocketErrorHandler.handleChatError(currentUsername, request,
                    Translator.tolocale(ERROR_MSG_SYSTEM_OVERLOAD_STRING));
            throw new SystemOverloadException(Translator.tolocale(ERROR_MSG_SYSTEM_OVERLOAD_STRING), request, e);
        }
    }

    private void validatePrivateMessageRequest(String sender, ChatMessageRequest request) {
        if (sender.equals(request.getRecipient())) {
            throw new InvalidDataException(Translator.tolocale(ERROR_MSG_SELF_SEND_STRING), request);
        }

        boolean hasContent = request.getContent() != null && !request.getContent().trim().isEmpty();
        boolean hasFile = request.getFileUrl() != null && !request.getFileUrl().trim().isEmpty();
        if (!hasContent && !hasFile) {
            throw new InvalidDataException(Translator.tolocale(ERROR_MSG_EMPTY_CONTENT_STRING), request);
        }

        UserEntity recipientEntity = userRepository.findByUsername(request.getRecipient())
                .orElseThrow(
                        () -> new ResourceNotFoundException(Translator.tolocale(ERROR_MSG_RECIPIENT_NOT_FOUND_STRING),
                                request));

        if (recipientEntity.getUserStatus() == UserStatus.INACTIVE) {
            throw new AccessForbiddenException(Translator.tolocale(ERROR_MSG_SEND_DELETED_STRING), request);
        }
        if (recipientEntity.getUserStatus() == UserStatus.LOCKED) {
            throw new AccessForbiddenException(Translator.tolocale(ERROR_MSG_SEND_LOCKED_STRING), request);
        }
        if (!friendService.isFriend(Objects.requireNonNull(sender),
                Objects.requireNonNull(request.getRecipient()))) {
            throw new AccessForbiddenException(Translator.tolocale(ERROR_MSG_NOT_FRIENDS_STRING), request);
        }
    }

    private ChatMessage buildChatMessage(String sender, ChatMessageRequest request, String convId) {
        ChatMessage chatMsg = messageMapper.toEntity(request);
        chatMsg.setConversationId(convId);
        chatMsg.setSender(sender);
        chatMsg.setId(new ObjectId().toHexString());
        chatMsg.setStatus(MessageStatus.SENT);
        chatMsg.setEdited(false);
        chatMsg.setDeleted(false);
        chatMsg.setReacted(false);
        chatMsg.setReactions(null);
        if (chatMsg.getTimestamp() == null) {
            chatMsg.setTimestamp(LocalDateTime.now(ZoneId.systemDefault()));
        }
        if (chatMsg.getContent() == null) {
            chatMsg.setContent("");
        }
        if (chatMsg.getContentType() == null) {
            chatMsg.setContentType(ContentType.TEXT);
        }
        return chatMsg;
    }

    private void rollbackRedisCache(String convId, ChatMessage chatMsg) {
        if (chatMsg == null) {
            return;
        }
        try {
            String hashKey = CHAT_RECENT_HASH_STRING + convId;
            String zsetKey = CHAT_RECENT_ZSET_STRING + convId;
            redisTemplate.opsForZSet().remove(zsetKey, chatMsg.getId());
            redisTemplate.opsForHash().delete(hashKey, chatMsg.getId());
            if (chatMsg.getRecipient() != null && chatMsg.getSender() != null) {
                String unreadKey = UNREAD_COUNTS_STRING + chatMsg.getRecipient();
                Long count = redisTemplate.opsForHash().increment(unreadKey, chatMsg.getSender(), -1);
                if (count != null && count <= 0) {
                    redisTemplate.opsForHash().delete(unreadKey, chatMsg.getSender());
                }
            }
        } catch (Exception redisEx) {
            log.error("Failed to rollback Redis cache for message '{}' in conversation '{}'",
                    chatMsg.getId(), convId, redisEx);
        }
    }

    private void cacheMessageToRedis(ChatMessage chatMsg) {
        if (chatMsg == null || chatMsg.getMessageType() != MessageType.CHAT) {
            return;
        }
        String convId = chatMsg.getConversationId();
        if (convId == null) {
            return;
        }
        log.debug("Caching message '{}' from sender '{}' to Redis", chatMsg.getId(), chatMsg.getSender());
        try {
            String hashKey = CHAT_RECENT_HASH_STRING + convId;
            String zsetKey = CHAT_RECENT_ZSET_STRING + convId;
            long score = chatMsg.getTimestamp().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            Duration chatTtl = getRandomTtl(300, 30);

            redisTemplate.opsForHash().put(hashKey, chatMsg.getId(), chatMsg);
            redisTemplate.opsForZSet().add(zsetKey, chatMsg.getId(), score);

            redisTemplate.expire(hashKey, chatTtl);
            redisTemplate.expire(zsetKey, chatTtl);

            if (chatMsg.getRecipient() != null && chatMsg.getSender() != null) {
                String key = UNREAD_COUNTS_STRING + chatMsg.getRecipient();
                redisTemplate.opsForHash().increment(key, chatMsg.getSender(), 1);
            }

            Set<Object> keysToRemove = redisTemplate.opsForZSet().range(zsetKey, 0, -51);
            if (keysToRemove != null && !keysToRemove.isEmpty()) {
                redisTemplate.opsForHash().delete(hashKey, keysToRemove.toArray());
                redisTemplate.opsForZSet().removeRange(zsetKey, 0, -51);
            }
        } catch (Exception e) {
            log.warn("Failed to cache message '{}' to Redis, continuing pipeline", chatMsg.getId(), e);
        }
    }

    private Duration getRandomTtl(long baseSeconds, long jitterSeconds) {
        long jitter = ThreadLocalRandom.current().nextLong(-jitterSeconds, jitterSeconds + 1);
        return Duration.ofSeconds(Math.max(10, baseSeconds + jitter));
    }

    private String generateConversationId(String user1, String user2) {
        return (user1.compareTo(user2) < 0) ? user1 + "_" + user2 : user2 + "_" + user1;
    }
}
