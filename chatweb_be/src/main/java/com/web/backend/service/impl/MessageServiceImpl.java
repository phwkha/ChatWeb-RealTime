package com.web.backend.service.impl;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.bson.types.ObjectId;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisTemplate;

import com.web.backend.kafka.payload.UpdateMessagePayload;
import com.web.backend.kafka.producer.ChatProducer;
import org.springframework.stereotype.Service;
import com.web.backend.common.ContentType;
import com.web.backend.common.MessageStatus;
import com.web.backend.common.MessageType;
import com.web.backend.common.UpdateMessageType;
import com.web.backend.common.UserStatus;
import com.web.backend.config.localresolverconfig.Translator;
import com.web.backend.controller.request.ChatMessageRequest;
import com.web.backend.controller.request.EditMessageRequest;
import com.web.backend.controller.request.MessageSystemRequest;
import com.web.backend.controller.request.ReactionRequest;
import com.web.backend.controller.request.RevokeMessageRequest;
import com.web.backend.controller.response.ChatMessageResponse;
import com.web.backend.controller.response.CursorResponse;
import com.web.backend.controller.response.MessageSystemResponse;
import com.web.backend.controller.response.UnreadCountsResponse;
import com.web.backend.exception.custom.AccessForbiddenException;
import com.web.backend.exception.custom.ResourceNotFoundException;
import com.web.backend.exception.custom.SystemOverloadException;
import com.web.backend.mapper.MessageMapper;
import com.web.backend.model.ChatMessage;
import com.web.backend.model.SystemMessage;
import com.web.backend.model.UserEntity;
import com.web.backend.repository.MessageRepository;
import com.web.backend.repository.SystemMessageRepository;
import com.web.backend.repository.UserRepository;
import com.web.backend.repository.projection.UnreadCountProjection;
import com.web.backend.service.FriendService;
import com.web.backend.service.MessageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.web.backend.exception.WebSocketErrorHandler;
import java.util.Set;
import java.util.function.Consumer;

@Slf4j(topic = "MESSAGE-SERVICE")
@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    private final MessageRepository messageRepository;

    private final UserRepository userRepository;

    private final SystemMessageRepository systemMessageRepository;

    private final FriendService friendService;

    private final RedisTemplate<String, Object> redisTemplate;

    private final MessageMapper messageMapper;

    private final ChatProducer chatProducer;

    private final WebSocketErrorHandler webSocketErrorHandler;

    private static final String TIMESTAMP_STRING = "timestamp";

    private static final String CHAT_RECENT_HASH_STRING = "chat:recent:hash:";
    private static final String CHAT_RECENT_ZSET_STRING = "chat:recent:zset:";
    private static final String UNREAD_COUNTS_STRING = "unread_counts:";

    private static final String ERROR_MSG_RECIPIENT_NOT_FOUND_STRING = "error.msg.recipient_not_found";
    private static final String ERROR_MSG_SEND_DELETED_STRING = "error.msg.send_deleted";
    private static final String ERROR_MSG_SEND_LOCKED_STRING = "error.msg.send_locked";
    private static final String ERROR_MSG_NOT_FRIENDS_STRING = "error.msg.not_friends";
    private static final String ERROR_MSG_NOT_FOUND_STRING = "error.msg.not_found";
    private static final String ERROR_MSG_SYSTEM_OVERLOAD_STRING = "error.msg.system_overload";
    private static final String ERROR_MSG_EDIT_FORBIDDEN_STRING = "error.msg.edit_forbidden";
    private static final String ERROR_MSG_DELETE_FORBIDDEN_STRING = "error.msg.delete_forbidden";

    private String generateConversationId(String user1, String user2) {
        return (user1.compareTo(user2) < 0) ? user1 + "_" + user2 : user2 + "_" + user1;
    }

    @Override
    public void sendPrivateMessage(String sender, ChatMessageRequest request) {
        validatePrivateMessageRequest(sender, request);

        String convId = generateConversationId(sender, request.getRecipient());
        ChatMessage chatMsg = buildChatMessage(sender, request, convId);

        try {
            if (chatMsg.getMessageType() == MessageType.CHAT) {
                cacheMessageToRedis(chatMsg);
            }
            chatProducer.sendChatMessage(chatMsg).whenComplete((result, ex) -> {
                if (ex != null) {
                    if (chatMsg.getMessageType() == MessageType.CHAT) {
                        log.error("Kafka failed for message {}", chatMsg.getId());
                        try {
                            String hashKey = CHAT_RECENT_HASH_STRING + convId;
                            String zsetKey = CHAT_RECENT_ZSET_STRING + convId;
                            redisTemplate.opsForZSet().remove(zsetKey, chatMsg.getId());
                            redisTemplate.opsForHash().delete(hashKey, chatMsg.getId());
                        } catch (Exception redisEx) {
                            log.error("Failed to rollback Redis for message {}", chatMsg.getId(), redisEx);
                        }
                        webSocketErrorHandler.handleChatError(sender, request,
                                Translator.tolocale(ERROR_MSG_SYSTEM_OVERLOAD_STRING));
                    }
                } else if (chatMsg.getMessageType() == MessageType.CHAT) {
                    log.info("Sent message to Kafka successfully");
                    updateMessageInRedisCache(convId, chatMsg.getId(), m -> m.setStatus(MessageStatus.SENT));
                }
            });
        } catch (Exception syncEx) {
            log.error("Kafka producer threw synchronous error", syncEx);
            throw new SystemOverloadException(Translator.tolocale(ERROR_MSG_SYSTEM_OVERLOAD_STRING), request, syncEx);
        }
    }

    private void validatePrivateMessageRequest(String sender, ChatMessageRequest request) {
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
        chatMsg.setStatus(MessageStatus.SENDING);
        chatMsg.setLocalId(request.getLocalId());
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

    @Override
    public void sendSystemMessage(String currentUsername, MessageSystemRequest request) {
        SystemMessage systemMsg = new SystemMessage();
        systemMsg.setSender(currentUsername);
        systemMsg.setTimestamp(Instant.now());
        systemMsg.setExpiresAt(request.getSurvivalTime() == null ? null
                : Instant.now().plus(request.getSurvivalTime(), ChronoUnit.SECONDS));
        systemMsg.setContent(request.getContent());
        systemMessageRepository.save(Objects.requireNonNull(systemMsg));

        chatProducer.sendSystemMessage(systemMsg).whenComplete((result, ex) -> {
            if (ex != null) {
                webSocketErrorHandler.handleChatError(systemMsg.getSender(), systemMsg,
                        Translator.tolocale(ERROR_MSG_SYSTEM_OVERLOAD_STRING));
            }
        });
    }

    @Override
    public void reactToMessage(String senderUsername, ReactionRequest request) {

        if (!friendService.isFriend(Objects.requireNonNull(senderUsername),
                Objects.requireNonNull(request.getRecipient()))) {
            throw new AccessForbiddenException(Translator.tolocale(ERROR_MSG_NOT_FRIENDS_STRING));
        }

        String convId = generateConversationId(senderUsername, request.getRecipient());

        ChatMessage msg = getMessageFromDbOrRedis(request.getMessageId(), convId);

        Map<String, String> oldReactions = msg.getReactions() == null ? null : new HashMap<>(msg.getReactions());
        boolean oldIsReacted = msg.isReacted();

        updateMessageInRedisCache(convId, request.getMessageId(), m -> applyReactionToMessage(m, senderUsername, request));
        applyReactionToMessage(msg, senderUsername, request);

        chatProducer.sendReaction(
                UpdateMessagePayload.builder().relatedUsername(senderUsername).type(UpdateMessageType.REACT)
                        .updateEvent(msg).build())
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Kafka failed for reaction message: {}", request.getMessageId(), ex);
                        updateMessageInRedisCache(convId, request.getMessageId(), m -> {
                            m.setReactions(oldReactions);
                            m.setReacted(oldIsReacted);
                        });
                        webSocketErrorHandler.handleChatError(senderUsername, request,
                                Translator.tolocale(ERROR_MSG_SYSTEM_OVERLOAD_STRING));
                    }
                });
    }

    @Override
    public CursorResponse<ChatMessageResponse> findPrivateMessageWithCursor(String user1, String user2,
            String cursorStr, int size) {
        String conversationId = generateConversationId(user1, user2);
        Pageable pageable = PageRequest.of(0, size + 1, Sort.by(Sort.Direction.DESC, TIMESTAMP_STRING));

        if (cursorStr == null || cursorStr.isEmpty()) {
            List<ChatMessage> cachedMessages = fetchMessagesFromRedisCache(conversationId, 0, size);
            if (cachedMessages.size() >= size + 1) {
                cachedMessages.sort(Comparator.comparing(ChatMessage::getTimestamp).reversed());
                log.info("Fetching private messages (Redis-First Cache Hit)");
                return buildCursorResponse(cachedMessages, size);
            }
        }

        List<ChatMessage> finalMessages = fetchMessagesFromDatabaseAndMerge(conversationId, cursorStr, size, pageable);
        log.info("Fetching private messages (DB Fallback)");
        return buildCursorResponse(finalMessages, size);
    }

    private List<ChatMessage> fetchMessagesFromRedisCache(String conversationId, long start, long end) {
        String hashKey = CHAT_RECENT_HASH_STRING + conversationId;
        String zsetKey = CHAT_RECENT_ZSET_STRING + conversationId;
        Set<Object> messageIds = redisTemplate.opsForZSet().reverseRange(zsetKey, start, end);

        if (messageIds == null || messageIds.isEmpty()) {
            return new ArrayList<>();
        }

        List<Object> redisObjects = redisTemplate.opsForHash().multiGet(hashKey, messageIds);
        List<ChatMessage> messages = new ArrayList<>();
        for (Object obj : redisObjects) {
            if (obj != null) {
                messages.add((ChatMessage) obj);
            }
        }
        return messages;
    }

    private List<ChatMessage> fetchMessagesFromDatabaseAndMerge(String conversationId, String cursorStr, int size,
            Pageable pageable) {
        if (cursorStr != null && !cursorStr.isEmpty()) {
            LocalDateTime cursorTime = LocalDateTime.parse(cursorStr);
            return new ArrayList<>(
                    messageRepository.findByConversationIdAndTimestampBefore(conversationId, cursorTime, pageable));
        }

        List<ChatMessage> dbMessages = messageRepository.findByConversationId(conversationId, pageable);
        List<ChatMessage> redisMessages = fetchMessagesFromRedisCache(conversationId, 0, -1);

        Map<String, ChatMessage> uniqueMessagesMap = new LinkedHashMap<>();
        for (ChatMessage msg : redisMessages) {
            uniqueMessagesMap.put(msg.getId(), msg);
        }
        for (ChatMessage msg : dbMessages) {
            uniqueMessagesMap.putIfAbsent(msg.getId(), msg);
        }

        return uniqueMessagesMap.values().stream()
                .sorted(Comparator.comparing(ChatMessage::getTimestamp).reversed())
                .limit(size + 1L)
                .toList();
    }

    @Override
    public ChatMessageResponse getMessageById(String messageId, String currentUsername) {
        ChatMessage message = messageRepository.findById(Objects.requireNonNull(messageId))
                .orElseThrow(() -> new ResourceNotFoundException(Translator.tolocale(ERROR_MSG_NOT_FOUND_STRING)));

        if (!message.getConversationId().contains(currentUsername)) {
            throw new AccessForbiddenException(Translator.tolocale(ERROR_MSG_RECIPIENT_NOT_FOUND_STRING));
        }

        return messageMapper.toResponse(message);
    }

    @Override
    public void editMessage(String senderUsername, EditMessageRequest request) {
        String convId = generateConversationId(senderUsername, request.getRecipient());

        ChatMessage msg = getMessageFromDbOrRedis(request.getMessageId(), convId);

        if (!msg.getSender().equals(senderUsername)) {
            throw new AccessForbiddenException(Translator.tolocale(ERROR_MSG_EDIT_FORBIDDEN_STRING));
        }

        String oldContent = msg.getContent();
        boolean oldIsEdited = msg.isEdited();

        msg.setContent(request.getNewContent());
        msg.setEdited(true);

        updateMessageInRedisCache(convId, msg.getId(), m -> {
            m.setContent(request.getNewContent());
            m.setEdited(true);
        });

        chatProducer
                .sendEditMessage(UpdateMessagePayload.builder().relatedUsername(senderUsername)
                        .type(UpdateMessageType.EDIT).updateEvent(msg).build())
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Kafka failed for edit message: {}", request.getMessageId(), ex);
                        updateMessageInRedisCache(convId, request.getMessageId(), m -> {
                            m.setContent(oldContent);
                            m.setEdited(oldIsEdited);
                        });
                        webSocketErrorHandler.handleChatError(senderUsername, request,
                                Translator.tolocale(ERROR_MSG_SYSTEM_OVERLOAD_STRING));
                    }
                });
    }

    @Override
    public void revokeMessage(String senderUsername, RevokeMessageRequest request) {
        String convId = generateConversationId(senderUsername, request.getRecipient());

        ChatMessage msg = getMessageFromDbOrRedis(request.getMessageId(), convId);

        if (!msg.getSender().equals(senderUsername)) {
            throw new AccessForbiddenException(Translator.tolocale(ERROR_MSG_DELETE_FORBIDDEN_STRING));
        }

        String oldContent = msg.getContent();
        String oldFileUrl = msg.getFileUrl();
        String oldFileName = msg.getFileName();
        Long oldFileSize = msg.getFileSize();
        Map<String, String> oldReactions = msg.getReactions() == null ? null : new HashMap<>(msg.getReactions());
        boolean oldIsDeleted = msg.isDeleted();

        msg.setContent("");
        msg.setFileUrl(null);
        msg.setFileName(null);
        msg.setFileSize(null);
        msg.setReactions(null);
        msg.setDeleted(true);

        updateMessageInRedisCache(convId, msg.getId(), m -> {
            m.setContent("");
            m.setFileUrl(null);
            m.setFileName(null);
            m.setFileSize(null);
            m.setReactions(null);
            m.setDeleted(true);
        });

        chatProducer.sendRevokeMessage(
                UpdateMessagePayload.builder().relatedUsername(senderUsername).type(UpdateMessageType.REVOKE)
                        .updateEvent(msg).build())
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Kafka failed for revoke message: {}", request.getMessageId(), ex);
                        updateMessageInRedisCache(convId, request.getMessageId(), m -> {
                            m.setContent(oldContent);
                            m.setFileUrl(oldFileUrl);
                            m.setFileName(oldFileName);
                            m.setFileSize(oldFileSize);
                            m.setReactions(oldReactions);
                            m.setDeleted(oldIsDeleted);
                        });
                        webSocketErrorHandler.handleChatError(senderUsername, request,
                                Translator.tolocale(ERROR_MSG_SYSTEM_OVERLOAD_STRING));
                    }
                });
    }

    @Override
    public CursorResponse<MessageSystemResponse> findSystemMessageWithCursor(String cursorStr, int size) {

        Pageable pageable = PageRequest.of(0, size + 1, Sort.by(Sort.Direction.DESC, TIMESTAMP_STRING));
        List<SystemMessage> messages;

        if (cursorStr == null || cursorStr.isEmpty()) {
            messages = new ArrayList<>(systemMessageRepository.findInitialMessage(pageable));
        } else {
            Instant cursorTime = Instant.parse(cursorStr);
            messages = new ArrayList<>(systemMessageRepository.findMessage(cursorTime, pageable));
        }

        boolean hasMore = false;
        if (messages.size() > size) {
            hasMore = true;
            messages.remove(messages.size() - 1);
        }

        String nextCursor = null;
        if (!messages.isEmpty()) {
            Instant lastMessageTime = messages.get(messages.size() - 1).getTimestamp();
            nextCursor = lastMessageTime.toString();
        }

        List<MessageSystemResponse> responseList = messages.stream()
                .map(messageMapper::systemMessageToResponse)
                .toList();

        log.info("Fetching system messages");
        return new CursorResponse<>(responseList, nextCursor, hasMore);
    }

    @Override
    public UnreadCountsResponse getUnreadMessageCounts(String recipientUsername) {

        String key = UNREAD_COUNTS_STRING + recipientUsername;

        Map<Object, Object> redisCounts = redisTemplate.opsForHash().entries(key);

        if (!redisCounts.isEmpty()) {
            if (redisCounts.containsKey("_empty")) {
                return UnreadCountsResponse.builder().unreadCounts(new HashMap<>()).build();
            }
            Map<String, Long> result = redisCounts.entrySet().stream()
                    .collect(Collectors.toMap(
                            e -> (String) e.getKey(),
                            e -> Long.valueOf(e.getValue().toString())));
            log.info("Fetching unread counts for user (Optimized) redis");
            return UnreadCountsResponse.builder().unreadCounts(result).build();
        }

        List<UnreadCountProjection> dbResults = messageRepository.countUnreadMessagesBySender(recipientUsername);

        Map<String, Long> resultMap = new HashMap<>();
        Map<String, Object> redisMap = new HashMap<>();

        if (dbResults.isEmpty()) {
            redisMap.put("_empty", 0L);
        } else {
            for (UnreadCountProjection r : dbResults) {
                resultMap.put(r.sender(), r.count());
                redisMap.put(r.sender(), r.count());
            }
        }

        redisTemplate.opsForHash().putAll(key, redisMap);
        redisTemplate.expire(key, 7, TimeUnit.DAYS);

        log.info("Fetching unread counts for user (From DB)");
        return UnreadCountsResponse.builder()
                .unreadCounts(resultMap)
                .build();
    }

    @Override
    public void markMessagesAsRead(String recipientUsername, String senderUsername) {
        List<ChatMessage> messages = messageRepository.findUnreadMessagesFromSender(recipientUsername, senderUsername);
        if (!messages.isEmpty()) {
            String convId = generateConversationId(recipientUsername, senderUsername);
            messages.forEach(msg -> {
                msg.setStatus(MessageStatus.READ);
                updateMessageInRedisCache(convId, msg.getId(), m -> m.setStatus(MessageStatus.READ));
            });
            messageRepository.saveAll(messages);
        }
        String key = UNREAD_COUNTS_STRING + recipientUsername;
        redisTemplate.opsForHash().delete(key, senderUsername);

        chatProducer.sendStatusMessage(UpdateMessagePayload.builder().relatedUsername(recipientUsername)
                .type(UpdateMessageType.STATUS).updateEvent(senderUsername).build())
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Kafka failed for status message from {} to {}", senderUsername, recipientUsername, ex);
                    }
                });
    }

    private void cacheMessageToRedis(ChatMessage chatMsg) {
        if (chatMsg == null || chatMsg.getMessageType() != MessageType.CHAT) {
            return;
        }
        String convId = chatMsg.getConversationId();
        if (convId == null) {
            return;
        }
        log.info("Caching message from {} to Redis synchronously", chatMsg.getSender());
        try {
            String hashKey = CHAT_RECENT_HASH_STRING + convId;
            String zsetKey = CHAT_RECENT_ZSET_STRING + convId;
            long score = chatMsg.getTimestamp().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

            redisTemplate.opsForHash().put(hashKey, chatMsg.getId(), chatMsg);
            redisTemplate.opsForZSet().add(zsetKey, chatMsg.getId(), score);

            Set<Object> keysToRemove = redisTemplate.opsForZSet().range(zsetKey, 0, -51);
            if (keysToRemove != null && !keysToRemove.isEmpty()) {
                redisTemplate.opsForHash().delete(hashKey, keysToRemove.toArray());
                redisTemplate.opsForZSet().removeRange(zsetKey, 0, -51);
            }

            redisTemplate.expire(hashKey, java.time.Duration.ofMinutes(5));
            redisTemplate.expire(zsetKey, java.time.Duration.ofMinutes(5));

            String key = UNREAD_COUNTS_STRING + chatMsg.getRecipient();
            redisTemplate.opsForHash().increment(key, chatMsg.getSender(), 1);
        } catch (Exception e) {
            log.error("Error caching message to Redis synchronously", e);
        }
    }

    private CursorResponse<ChatMessageResponse> buildCursorResponse(List<ChatMessage> messages, int size) {

        boolean hasMore = false;
        if (messages.size() > size) {
            hasMore = true;
            messages.remove(messages.size() - 1);
        }

        String nextCursor = null;
        if (!messages.isEmpty()) {
            LocalDateTime lastMessageTime = messages.get(messages.size() - 1).getTimestamp();
            nextCursor = lastMessageTime.toString();
        }

        List<ChatMessageResponse> responseList = messages.stream()
                .map(messageMapper::toResponse)
                .toList();

        return new CursorResponse<>(responseList, nextCursor, hasMore);
    }

    private void updateMessageInRedisCache(String conversationId, String messageId,
            Consumer<ChatMessage> updateAction) {
        try {
            String hashKey = CHAT_RECENT_HASH_STRING + conversationId;
            Object obj = redisTemplate.opsForHash().get(hashKey, messageId);
            if (obj != null) {
                ChatMessage msg = (ChatMessage) obj;
                updateAction.accept(msg);
                redisTemplate.opsForHash().put(hashKey, messageId, msg);
            }
        } catch (Exception e) {
            log.warn("Error updating Redis cache for message {}: {}", messageId, e.getMessage());
        }
    }

    private ChatMessage getMessageFromDbOrRedis(String messageId, String convId) {
        Optional<ChatMessage> dbMsgOpt = messageRepository.findById(messageId);
        if (dbMsgOpt.isPresent()) {
            return dbMsgOpt.get();
        }
        String hashKey = CHAT_RECENT_HASH_STRING + convId;
        Object redisObj = redisTemplate.opsForHash().get(hashKey, messageId);
        if (redisObj != null) {
            return (ChatMessage) redisObj;
        }
        throw new ResourceNotFoundException(Translator.tolocale(ERROR_MSG_NOT_FOUND_STRING));
    }

    private void applyReactionToMessage(ChatMessage message, String senderUsername, ReactionRequest request) {
        Map<String, String> reactions = message.getReactions();
        if (reactions == null) {
            reactions = new HashMap<>();
            message.setReactions(reactions);
        }
        if (request.getReactionType() != null) {
            reactions.put(senderUsername, request.getReactionType().toString());
        } else {
            reactions.remove(senderUsername);
        }
        message.setReacted(true);
    }
}
