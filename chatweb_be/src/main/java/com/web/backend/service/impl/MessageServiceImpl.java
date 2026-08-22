package com.web.backend.service.impl;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.web.backend.common.MessageStatus;
import com.web.backend.common.MessageType;
import com.web.backend.common.UpdateMessageType;
import com.web.backend.config.localresolverconfig.Translator;
import com.web.backend.controller.request.EditMessageRequest;
import com.web.backend.controller.request.MarkReadRequest;
import com.web.backend.controller.request.ReactionRequest;
import com.web.backend.controller.request.RevokeMessageRequest;
import com.web.backend.controller.response.ChatMessageResponse;
import com.web.backend.controller.response.CursorResponse;
import com.web.backend.controller.response.MessageSystemResponse;
import com.web.backend.controller.response.ReadReceiptResponse;
import com.web.backend.controller.response.UnreadCountsResponse;
import com.web.backend.exception.custom.AccessForbiddenException;
import com.web.backend.exception.custom.InvalidDataException;
import com.web.backend.exception.custom.ResourceNotFoundException;
import com.web.backend.exception.custom.SystemOverloadException;
import com.web.backend.kafka.payload.UpdateMessagePayload;
import com.web.backend.mapper.MessageMapper;
import com.web.backend.model.mongo.ChatMessage;
import com.web.backend.model.mongo.ReadReceipt;
import com.web.backend.model.mongo.SystemMessage;
import com.web.backend.repository.MessageRepository;
import com.web.backend.repository.ReadReceiptRepository;
import com.web.backend.repository.SystemMessageRepository;
import com.web.backend.repository.projection.UnreadCountProjection;
import com.web.backend.service.FriendService;
import com.web.backend.service.MessageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j(topic = "MESSAGE-SERVICE")
@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    private final MessageRepository messageRepository;
    private final ReadReceiptRepository readReceiptRepository;
    private final SystemMessageRepository systemMessageRepository;
    private final FriendService friendService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final MongoTemplate mongoTemplate;
    private final MessageMapper messageMapper;
    private final ApplicationEventPublisher eventPublisher;

    private static final String TIMESTAMP_STRING = "timestamp";
    private static final String SENTINEL_EMPTY_STRING = "_empty";
    private static final String EMPTY_STRING = "";
    private static final String DELIMITER_COLON_STRING = ":";
    private static final String DELIMITER_UNDERSCORE_STRING = "_";

    private static final String FIELD_ID_STRING = "id";
    private static final String FIELD_CONVERSATION_ID_STRING = "conversationId";
    private static final String FIELD_SENDER_STRING = "sender";
    private static final String FIELD_IS_DELETED_STRING = "isDeleted";
    private static final String FIELD_CONTENT_STRING = "content";
    private static final String FIELD_IS_EDITED_STRING = "isEdited";
    private static final String FIELD_IV_STRING = "iv";
    private static final String FIELD_WRAPPED_KEY_RECIPIENT_STRING = "wrappedKeyRecipient";
    private static final String FIELD_WRAPPED_KEY_SENDER_STRING = "wrappedKeySender";
    private static final String FIELD_FILE_URL_STRING = "fileUrl";
    private static final String FIELD_FILE_NAME_STRING = "fileName";
    private static final String FIELD_FILE_SIZE_STRING = "fileSize";
    private static final String FIELD_REACTIONS_STRING = "reactions";
    private static final String FIELD_REACTIONS_PREFIX_STRING = "reactions.";
    private static final String FIELD_IS_REACTED_STRING = "isReacted";
    private static final String FIELD_LAST_READ_TIMESTAMP_STRING = "lastReadTimestamp";
    private static final String FIELD_USERNAME_STRING = "username";

    private static final String CHAT_RECENT_HASH_STRING = "chat:recent:hash:";
    private static final String CHAT_RECENT_ZSET_STRING = "chat:recent:zset:";
    private static final String UNREAD_COUNTS_STRING = "unread_counts:";
    private static final String READ_RECEIPT_KEY_STRING = "read_receipt:";

    private static final String ERROR_MSG_RECIPIENT_NOT_FOUND_STRING = "error.msg.recipient_not_found";
    private static final String ERROR_MSG_NOT_FRIENDS_STRING = "error.msg.not_friends";
    private static final String ERROR_MSG_NOT_FOUND_STRING = "error.msg.not_found";
    private static final String ERROR_MSG_SYNCING_STRING = "error.msg.syncing";
    private static final String ERROR_MSG_EDIT_FORBIDDEN_STRING = "error.msg.edit_forbidden";
    private static final String ERROR_MSG_DELETE_FORBIDDEN_STRING = "error.msg.delete_forbidden";
    private static final String ERROR_MSG_EDIT_DELETED_STRING = "error.msg.edit_deleted";
    private static final String ERROR_MSG_INVALID_TYPE_STRING = "error.msg.invalid_type";
    private static final String ERROR_MSG_SYSTEM_OVERLOAD_STRING = "error.msg.system_overload";

    @Override
    public ChatMessageResponse getMessageById(String messageId, String currentUsername) {
        ChatMessage message = messageRepository.findById(Objects.requireNonNull(messageId))
                .orElseThrow(() -> new ResourceNotFoundException(Translator.tolocale(ERROR_MSG_NOT_FOUND_STRING)));

        if (!currentUsername.equals(message.getSender()) && !currentUsername.equals(message.getRecipient())) {
            throw new AccessForbiddenException(Translator.tolocale(ERROR_MSG_RECIPIENT_NOT_FOUND_STRING));
        }

        return messageMapper.toResponse(message);
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
                return buildCursorResponse(cachedMessages, size, conversationId, user1, user2);
            }
        }

        List<ChatMessage> finalMessages = fetchMessagesFromDatabaseAndMerge(conversationId, cursorStr, size, pageable);
        return buildCursorResponse(finalMessages, size, conversationId, user1, user2);
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

        return new CursorResponse<>(responseList, nextCursor, hasMore);
    }

    @Override
    public UnreadCountsResponse getUnreadMessageCounts(String recipientUsername) {

        String key = UNREAD_COUNTS_STRING + recipientUsername;

        Map<Object, Object> redisCounts = redisTemplate.opsForHash().entries(key);

        if (!redisCounts.isEmpty()) {
            if (redisCounts.containsKey(SENTINEL_EMPTY_STRING)) {
                return UnreadCountsResponse.builder().unreadCounts(new HashMap<>()).build();
            }
            Map<String, Long> result = redisCounts.entrySet().stream()
                    .collect(Collectors.toMap(
                            e -> (String) e.getKey(),
                            e -> Long.valueOf(e.getValue().toString())));
            return UnreadCountsResponse.builder().unreadCounts(result).build();
        }

        List<UnreadCountProjection> dbResults = messageRepository.countUnreadMessagesBySender(recipientUsername);

        Map<String, Long> resultMap = new HashMap<>();
        Map<String, Object> redisMap = new HashMap<>();

        if (dbResults.isEmpty()) {
            redisMap.put(SENTINEL_EMPTY_STRING, 0L);
        } else {
            for (UnreadCountProjection r : dbResults) {
                resultMap.put(r.sender(), r.count());
                redisMap.put(r.sender(), r.count());
            }
        }

        redisTemplate.opsForHash().putAll(key, redisMap);
        redisTemplate.expire(key, getRandomTtl(7 * 24 * 3600L, 12 * 3600L));

        return UnreadCountsResponse.builder()
                .unreadCounts(resultMap)
                .build();
    }

    @Override
    public void markMessagesAsRead(String recipientUsername, MarkReadRequest request) {
        String senderUsername = request.getSender();
        if (recipientUsername.equals(senderUsername)) {
            return;
        }
        String convId = generateConversationId(recipientUsername, senderUsername);
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        String receiptId = convId + DELIMITER_COLON_STRING + recipientUsername;

        try {
            Query query = new Query(Criteria.where(FIELD_ID_STRING).is(receiptId));
            Update update = new Update()
                    .max(FIELD_LAST_READ_TIMESTAMP_STRING, now)
                    .setOnInsert(FIELD_CONVERSATION_ID_STRING, convId)
                    .setOnInsert(FIELD_USERNAME_STRING, recipientUsername);

            mongoTemplate.upsert(query, update, ReadReceipt.class);
            log.debug("Persisted ReadReceipt with $max to MongoDB for conv '{}' and user '{}'", convId, recipientUsername);
        } catch (Exception ex) {
            log.error("Failed to persist ReadReceipt to MongoDB for conv '{}'", convId, ex);
            throw new SystemOverloadException(Translator.tolocale(ERROR_MSG_SYSTEM_OVERLOAD_STRING), request, ex);
        }

        try {
            String readReceiptKey = READ_RECEIPT_KEY_STRING + convId + DELIMITER_COLON_STRING + recipientUsername;
            redisTemplate.opsForValue().set(readReceiptKey, now.toString(), Duration.ofDays(7));

            String unreadKey = UNREAD_COUNTS_STRING + recipientUsername;
            redisTemplate.opsForHash().delete(unreadKey, senderUsername);
        } catch (Exception e) {
            log.warn("Failed to update read receipt in Redis for conv '{}'", convId, e);
        }

        eventPublisher.publishEvent(ReadReceiptResponse.builder()
                .conversationId(convId)
                .reader(recipientUsername)
                .sender(senderUsername)
                .readTimestamp(now)
                .build());
    }

    @Override
    public ChatMessageResponse editMessage(String senderUsername, EditMessageRequest request) {
        String convId = generateConversationId(senderUsername, request.getRecipient());

        ChatMessage msg = getMessageFromDbOrRedis(request.getMessageId(), convId);

        if (!msg.getSender().equals(senderUsername) || !convId.equals(msg.getConversationId())) {
            throw new AccessForbiddenException(Translator.tolocale(ERROR_MSG_EDIT_FORBIDDEN_STRING));
        }

        if (msg.getMessageType() != MessageType.CHAT) {
            throw new InvalidDataException(Translator.tolocale(ERROR_MSG_INVALID_TYPE_STRING));
        }

        if (msg.isDeleted()) {
            throw new InvalidDataException(Translator.tolocale(ERROR_MSG_EDIT_DELETED_STRING));
        }

        Query query = new Query(Criteria.where(FIELD_ID_STRING).is(request.getMessageId())
                .and(FIELD_CONVERSATION_ID_STRING).is(convId)
                .and(FIELD_SENDER_STRING).is(senderUsername)
                .and(FIELD_IS_DELETED_STRING).is(false));

        Update update = new Update();
        update.set(FIELD_CONTENT_STRING, request.getNewContent());
        update.set(FIELD_IS_EDITED_STRING, true);
        if (request.getIv() != null) {
            update.set(FIELD_IV_STRING, request.getIv());
        }
        if (request.getWrappedKeyRecipient() != null) {
            update.set(FIELD_WRAPPED_KEY_RECIPIENT_STRING, request.getWrappedKeyRecipient());
        }
        if (request.getWrappedKeySender() != null) {
            update.set(FIELD_WRAPPED_KEY_SENDER_STRING, request.getWrappedKeySender());
        }

        FindAndModifyOptions options = new FindAndModifyOptions().returnNew(true);
        ChatMessage updatedMsg = mongoTemplate.findAndModify(query, update, options, ChatMessage.class);

        if (updatedMsg == null) {
            handleMissingMongoMessage(convId, request.getMessageId(), request);
            return null;
        }

        putMessageIfCached(convId, updatedMsg);

        eventPublisher.publishEvent(UpdateMessagePayload.builder()
                .relatedUsername(senderUsername)
                .type(UpdateMessageType.EDIT)
                .updateEvent(updatedMsg)
                .build());

        return messageMapper.toResponse(updatedMsg);
    }

    @Override
    public void revokeMessage(String senderUsername, RevokeMessageRequest request) {
        String convId = generateConversationId(senderUsername, request.getRecipient());

        ChatMessage msg = getMessageFromDbOrRedis(request.getMessageId(), convId);

        if (!msg.getSender().equals(senderUsername) || !convId.equals(msg.getConversationId())) {
            throw new AccessForbiddenException(Translator.tolocale(ERROR_MSG_DELETE_FORBIDDEN_STRING));
        }

        if (msg.getMessageType() != MessageType.CHAT) {
            throw new InvalidDataException(Translator.tolocale(ERROR_MSG_INVALID_TYPE_STRING));
        }

        if (msg.isDeleted()) {
            return;
        }

        Query query = new Query(Criteria.where(FIELD_ID_STRING).is(request.getMessageId())
                .and(FIELD_CONVERSATION_ID_STRING).is(convId)
                .and(FIELD_SENDER_STRING).is(senderUsername)
                .and(FIELD_IS_DELETED_STRING).is(false));

        Update update = new Update();
        update.set(FIELD_CONTENT_STRING, EMPTY_STRING);
        update.set(FIELD_FILE_URL_STRING, null);
        update.set(FIELD_FILE_NAME_STRING, null);
        update.set(FIELD_FILE_SIZE_STRING, null);
        update.set(FIELD_REACTIONS_STRING, null);
        update.set(FIELD_IV_STRING, null);
        update.set(FIELD_WRAPPED_KEY_RECIPIENT_STRING, null);
        update.set(FIELD_WRAPPED_KEY_SENDER_STRING, null);
        update.set(FIELD_IS_DELETED_STRING, true);

        FindAndModifyOptions options = new FindAndModifyOptions().returnNew(true);
        ChatMessage updatedMsg = mongoTemplate.findAndModify(query, update, options, ChatMessage.class);

        if (updatedMsg == null) {
            handleMissingMongoMessage(convId, request.getMessageId(), request);
            return;
        }

        if (msg.getStatus() != MessageStatus.READ && msg.getRecipient() != null) {
            String unreadKey = UNREAD_COUNTS_STRING + msg.getRecipient();
            try {
                redisTemplate.opsForHash().increment(unreadKey, senderUsername, -1);
            } catch (Exception e) {
                log.warn("Failed to decrement unread count for recipient '{}'", msg.getRecipient(), e);
            }
        }

        putMessageIfCached(convId, updatedMsg);

        eventPublisher.publishEvent(UpdateMessagePayload.builder()
                .relatedUsername(senderUsername)
                .type(UpdateMessageType.REVOKE)
                .updateEvent(updatedMsg)
                .build());
    }

    @Override
    public ChatMessageResponse reactToMessage(String senderUsername, ReactionRequest request) {

        if (!friendService.isFriend(Objects.requireNonNull(senderUsername),
                Objects.requireNonNull(request.getRecipient()))) {
            throw new AccessForbiddenException(Translator.tolocale(ERROR_MSG_NOT_FRIENDS_STRING));
        }

        String convId = generateConversationId(senderUsername, request.getRecipient());

        ChatMessage msg = getMessageFromDbOrRedis(request.getMessageId(), convId);

        if (!convId.equals(msg.getConversationId())) {
            throw new AccessForbiddenException(Translator.tolocale(ERROR_MSG_EDIT_FORBIDDEN_STRING));
        }

        if (msg.isDeleted()) {
            throw new InvalidDataException(Translator.tolocale(ERROR_MSG_EDIT_DELETED_STRING));
        }

        Query query = new Query(Criteria.where(FIELD_ID_STRING).is(request.getMessageId())
                .and(FIELD_CONVERSATION_ID_STRING).is(convId)
                .and(FIELD_IS_DELETED_STRING).is(false));

        Update update = new Update();
        if (request.getReactionType() != null) {
            update.set(FIELD_REACTIONS_PREFIX_STRING + senderUsername, request.getReactionType().toString());
            update.set(FIELD_IS_REACTED_STRING, true);
        } else {
            update.unset(FIELD_REACTIONS_PREFIX_STRING + senderUsername);
            Map<String, String> currentReactions = msg.getReactions();
            if (currentReactions == null || currentReactions.size() <= 1) {
                update.set(FIELD_IS_REACTED_STRING, false);
            }
        }

        FindAndModifyOptions options = new FindAndModifyOptions().returnNew(true);
        ChatMessage updatedMsg = mongoTemplate.findAndModify(query, update, options, ChatMessage.class);

        if (updatedMsg == null) {
            handleMissingMongoMessage(convId, request.getMessageId(), request);
            return null;
        }

        putMessageIfCached(convId, updatedMsg);

        eventPublisher.publishEvent(UpdateMessagePayload.builder()
                .relatedUsername(senderUsername)
                .type(UpdateMessageType.REACT)
                .updateEvent(updatedMsg)
                .build());

        return messageMapper.toResponse(updatedMsg);
    }

    private void handleMissingMongoMessage(String convId, String messageId, Object requestData) {
        String hashKey = CHAT_RECENT_HASH_STRING + convId;
        Object redisObj = null;
        try {
            redisObj = redisTemplate.opsForHash().get(hashKey, messageId);
        } catch (Exception e) {
            log.warn("Failed to check Redis cache for message '{}'", messageId, e);
        }
        if (redisObj != null) {
            throw new SystemOverloadException(Translator.tolocale(ERROR_MSG_SYNCING_STRING), requestData);
        }
        throw new ResourceNotFoundException(Translator.tolocale(ERROR_MSG_NOT_FOUND_STRING));
    }

    private String generateConversationId(String user1, String user2) {
        return (user1.compareTo(user2) < 0) ? user1 + DELIMITER_UNDERSCORE_STRING + user2
                : user2 + DELIMITER_UNDERSCORE_STRING + user1;
    }

    private LocalDateTime getLastReadTimestamp(String conversationId, String username) {
        if (conversationId == null || username == null) {
            return null;
        }
        String key = READ_RECEIPT_KEY_STRING + conversationId + DELIMITER_COLON_STRING + username;
        try {
            Object val = redisTemplate.opsForValue().get(key);
            if (val != null) {
                return LocalDateTime.parse(val.toString());
            }
        } catch (Exception e) {
            log.warn("Failed to get read receipt from Redis for key '{}'", key, e);
        }

        try {
            Optional<ReadReceipt> receiptOpt = readReceiptRepository.findByConversationIdAndUsername(conversationId,
                    username);
            if (receiptOpt.isPresent() && receiptOpt.get().getLastReadTimestamp() != null) {
                LocalDateTime ts = receiptOpt.get().getLastReadTimestamp();
                cacheReadTimestamp(key, ts);
                return ts;
            }
        } catch (Exception e) {
            log.warn("Failed to get read receipt from MongoDB for conv '{}' and user '{}'", conversationId, username,
                    e);
        }
        return null;
    }

    private void cacheReadTimestamp(String key, LocalDateTime timestamp) {
        try {
            redisTemplate.opsForValue().set(key, timestamp.toString(), Duration.ofDays(7));
        } catch (Exception e) {
            log.warn("Failed to populate read receipt in Redis for key '{}'", key, e);
        }
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

    private Duration getRandomTtl(long baseSeconds, long jitterSeconds) {
        long jitter = ThreadLocalRandom.current().nextLong(-jitterSeconds, jitterSeconds + 1);
        return Duration.ofSeconds(Math.max(10, baseSeconds + jitter));
    }

    private CursorResponse<ChatMessageResponse> buildCursorResponse(List<ChatMessage> messages, int size,
            String conversationId, String user1, String user2) {

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

        LocalDateTime user1LastRead = getLastReadTimestamp(conversationId, user1);
        LocalDateTime user2LastRead = getLastReadTimestamp(conversationId, user2);

        List<ChatMessageResponse> responseList = messages.stream()
                .map(msg -> {
                    ChatMessageResponse response = messageMapper.toResponse(msg);
                    String recipient = msg.getRecipient();
                    LocalDateTime recipientReadTime = recipient != null && recipient.equals(user1) ? user1LastRead
                            : user2LastRead;
                    if (recipientReadTime != null && msg.getTimestamp() != null
                            && !msg.getTimestamp().isAfter(recipientReadTime)) {
                        response.setStatus(MessageStatus.READ);
                    } else if (response.getStatus() == null) {
                        response.setStatus(MessageStatus.SENT);
                    }
                    return response;
                })
                .toList();

        return new CursorResponse<>(responseList, nextCursor, hasMore);
    }

    private void putMessageIfCached(String conversationId, ChatMessage updatedMsg) {
        if (updatedMsg == null || updatedMsg.getId() == null) {
            return;
        }
        try {
            String hashKey = CHAT_RECENT_HASH_STRING + conversationId;
            Boolean exists = redisTemplate.opsForHash().hasKey(hashKey, updatedMsg.getId());
            if (Boolean.TRUE.equals(exists)) {
                redisTemplate.opsForHash().put(hashKey, updatedMsg.getId(), updatedMsg);
                log.debug("Synchronized updated message '{}' to Redis cache for conv '{}'", updatedMsg.getId(),
                        conversationId);
            }
        } catch (Exception e) {
            log.warn("Failed to update message '{}' in Redis cache for conv '{}'", updatedMsg.getId(), conversationId,
                    e);
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
}
