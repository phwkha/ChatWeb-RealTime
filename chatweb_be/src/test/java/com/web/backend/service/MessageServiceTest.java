package com.web.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.support.SendResult;

import com.web.backend.config.localresolverconfig.Translator;
import com.web.backend.controller.request.EditMessageRequest;
import com.web.backend.controller.request.MarkReadRequest;
import com.web.backend.controller.request.ReactionRequest;
import com.web.backend.controller.request.RevokeMessageRequest;
import com.web.backend.exception.WebSocketErrorHandler;
import com.web.backend.exception.custom.AccessForbiddenException;
import com.web.backend.exception.custom.InvalidDataException;
import com.web.backend.exception.custom.ResourceNotFoundException;
import com.web.backend.kafka.avro.ChatMessageAvro;
import com.web.backend.mapper.MessageMapper;
import com.web.backend.model.ChatMessage;
import com.web.backend.model.ReadReceipt;
import com.web.backend.model.SystemMessage;
import com.web.backend.repository.MessageRepository;
import com.web.backend.repository.ReadReceiptRepository;
import com.web.backend.repository.SystemMessageRepository;
import com.web.backend.repository.projection.UnreadCountProjection;
import com.web.backend.service.impl.MessageServiceImpl;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import org.springframework.data.domain.Pageable;
import com.web.backend.common.MessageStatus;
import com.web.backend.controller.response.ChatMessageResponse;
import com.web.backend.controller.response.CursorResponse;
import com.web.backend.controller.response.MessageSystemResponse;
import com.web.backend.controller.response.UnreadCountsResponse;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class MessageServiceTest {

    @Mock
    private MessageRepository messageRepository;
    @Mock
    private ReadReceiptRepository readReceiptRepository;
    @Mock
    private SystemMessageRepository systemMessageRepository;
    @Mock
    private FriendService friendService;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private MongoTemplate mongoTemplate;
    @Mock
    private MessageMapper messageMapper;

    @Mock
    private com.web.backend.kafka.producer.ChatProducer chatProducer;

    @Mock
    private WebSocketRoutingService webSocketRoutingService;

    @Mock
    private WebSocketErrorHandler webSocketErrorHandler;

    @Mock
    private ListOperations<String, Object> listOperations;
    @Mock
    private HashOperations<String, Object, Object> hashOperations;
    @Mock
    private ZSetOperations<String, Object> zSetOperations;
    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private MessageServiceImpl messageService;

    @BeforeEach
    void setUp() {
        // Mock Translator to avoid NullPointerException for multi-language errors
        ResourceBundleMessageSource messageSource = mock(ResourceBundleMessageSource.class);
        lenient().when(messageSource.getMessage(anyString(), any(), any())).thenReturn("Mocked Error Message");
        Translator.setStaticMessageSource(messageSource);

        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        lenient().when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        lenient().when(redisTemplate.opsForList()).thenReturn(listOperations);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(messageMapper.toAvro(any())).thenAnswer(inv -> {
            ChatMessage entity = inv.getArgument(0);
            ChatMessageAvro payload = new ChatMessageAvro();
            if (entity != null) {
                payload.setId(entity.getId());
                payload.setContent(entity.getContent());
                payload.setSender(entity.getSender());
                payload.setRecipient(entity.getRecipient());
                payload.setMessageType(entity.getMessageType() != null ? entity.getMessageType().name() : null);
                payload.setContentType(entity.getContentType() != null ? entity.getContentType().name() : null);
                payload.setTimestamp(entity.getTimestamp() != null ? entity.getTimestamp().toString() : null);
            }
            return payload;
        });

    }

    @Test
    void testReactToMessage_NotFriends() {
        com.web.backend.controller.request.ReactionRequest request = new com.web.backend.controller.request.ReactionRequest();
        request.setRecipient("recipient");

        when(friendService.isFriend("sender", "recipient")).thenReturn(false);

        assertThrows(AccessForbiddenException.class, () -> messageService.reactToMessage("sender", request));
    }

    @Test
    void testReactToMessage_Success() {
        com.web.backend.controller.request.ReactionRequest request = new com.web.backend.controller.request.ReactionRequest();
        request.setRecipient("recipient");
        request.setMessageId("msg123");
        request.setReactionType(com.web.backend.common.ReactionType.HEART);

        when(friendService.isFriend("sender", "recipient")).thenReturn(true);

        ChatMessage message = new ChatMessage();
        message.setId("msg123");
        message.setSender("sender");
        message.setRecipient("recipient");
        message.setConversationId("recipient_sender");
        when(messageRepository.findById("msg123")).thenReturn(Optional.of(message));

        CompletableFuture<SendResult<String, Object>> future = CompletableFuture
                .completedFuture(mock(SendResult.class, RETURNS_DEEP_STUBS));
        when(chatProducer.sendReaction(any())).thenReturn(future);

        messageService.reactToMessage("sender", request);

        // Verify Redis updated
        verify(redisTemplate, atLeastOnce()).opsForHash();

        // Verify Kafka push
        verify(chatProducer).sendReaction(any());
    }

    @Test
    void testReactToMessage_Forbidden_DifferentConversation() {
        com.web.backend.controller.request.ReactionRequest request = new com.web.backend.controller.request.ReactionRequest();
        request.setRecipient("recipient");
        request.setMessageId("msg123");
        request.setReactionType(com.web.backend.common.ReactionType.HEART);

        when(friendService.isFriend("sender", "recipient")).thenReturn(true);

        ChatMessage message = new ChatMessage();
        message.setId("msg123");
        message.setSender("other1");
        message.setRecipient("other2");
        message.setConversationId("other1_other2");
        when(messageRepository.findById("msg123")).thenReturn(Optional.of(message));

        assertThrows(AccessForbiddenException.class, () -> messageService.reactToMessage("sender", request));
    }

    @Test
    void testReactToMessage_AlreadyDeleted() {
        com.web.backend.controller.request.ReactionRequest request = new com.web.backend.controller.request.ReactionRequest();
        request.setRecipient("recipient");
        request.setMessageId("msg123");
        request.setReactionType(com.web.backend.common.ReactionType.HEART);

        when(friendService.isFriend("sender", "recipient")).thenReturn(true);

        ChatMessage message = new ChatMessage();
        message.setId("msg123");
        message.setSender("sender");
        message.setRecipient("recipient");
        message.setConversationId("recipient_sender");
        message.setDeleted(true);
        when(messageRepository.findById("msg123")).thenReturn(Optional.of(message));

        assertThrows(InvalidDataException.class, () -> messageService.reactToMessage("sender", request));
    }

    // ==========================================
    // TESTS FOR EDIT, REVOKE & GET BY ID
    // ==========================================

    @Test
    void testEditMessage_Success() {
        EditMessageRequest request = new EditMessageRequest();
        request.setMessageId("msg1");
        request.setNewContent("Edited text");
        request.setRecipient("recipient");

        ChatMessage message = new ChatMessage();
        message.setId("msg1");
        message.setSender("sender");
        message.setRecipient("recipient");
        message.setConversationId("recipient_sender");
        message.setMessageType(com.web.backend.common.MessageType.CHAT);

        when(messageRepository.findById("msg1")).thenReturn(Optional.of(message));
        CompletableFuture<SendResult<String, Object>> future = CompletableFuture
                .completedFuture(mock(SendResult.class, RETURNS_DEEP_STUBS));
        when(chatProducer.sendEditMessage(any())).thenReturn(future);

        messageService.editMessage("sender", request);

        assertTrue(message.isEdited());
        assertEquals("Edited text", message.getContent());
        verify(chatProducer).sendEditMessage(any());
    }

    @Test
    void testEditMessage_Success_WithE2EE() {
        EditMessageRequest request = new EditMessageRequest();
        request.setMessageId("msg1");
        request.setNewContent("Encrypted text");
        request.setRecipient("recipient");
        request.setIv("new_iv");
        request.setWrappedKeyRecipient("new_wrapped_recipient");
        request.setWrappedKeySender("new_wrapped_sender");

        ChatMessage message = new ChatMessage();
        message.setId("msg1");
        message.setSender("sender");
        message.setRecipient("recipient");
        message.setConversationId("recipient_sender");
        message.setIv("old_iv");
        message.setMessageType(com.web.backend.common.MessageType.CHAT);

        when(messageRepository.findById("msg1")).thenReturn(Optional.of(message));
        CompletableFuture<SendResult<String, Object>> future = CompletableFuture
                .completedFuture(mock(SendResult.class, RETURNS_DEEP_STUBS));
        when(chatProducer.sendEditMessage(any())).thenReturn(future);

        messageService.editMessage("sender", request);

        assertTrue(message.isEdited());
        assertEquals("Encrypted text", message.getContent());
        assertEquals("new_iv", message.getIv());
        assertEquals("new_wrapped_recipient", message.getWrappedKeyRecipient());
        assertEquals("new_wrapped_sender", message.getWrappedKeySender());
        verify(chatProducer).sendEditMessage(any());
    }

    @Test
    void testEditMessage_Forbidden() {
        EditMessageRequest request = new EditMessageRequest();
        request.setMessageId("msg1");
        request.setRecipient("recipient");

        ChatMessage message = new ChatMessage();
        message.setId("msg1");
        message.setSender("other_user");
        message.setRecipient("recipient");
        message.setConversationId("recipient_other_user");

        when(messageRepository.findById("msg1")).thenReturn(Optional.of(message));

        assertThrows(AccessForbiddenException.class, () -> messageService.editMessage("sender", request));
    }

    @Test
    void testEditMessage_NonChatMessage_ThrowsInvalidDataException() {
        EditMessageRequest request = new EditMessageRequest();
        request.setMessageId("msg1");
        request.setRecipient("recipient");
        request.setNewContent("New text");

        ChatMessage message = new ChatMessage();
        message.setId("msg1");
        message.setSender("sender");
        message.setRecipient("recipient");
        message.setConversationId("recipient_sender");
        message.setMessageType(null);

        when(messageRepository.findById("msg1")).thenReturn(Optional.of(message));

        assertThrows(InvalidDataException.class, () -> messageService.editMessage("sender", request));
    }

    @Test
    void testEditMessage_AlreadyDeleted_ThrowsInvalidDataException() {
        EditMessageRequest request = new EditMessageRequest();
        request.setMessageId("msg1");
        request.setRecipient("recipient");
        request.setNewContent("New text");

        ChatMessage message = new ChatMessage();
        message.setId("msg1");
        message.setSender("sender");
        message.setRecipient("recipient");
        message.setConversationId("recipient_sender");
        message.setDeleted(true);
        message.setMessageType(com.web.backend.common.MessageType.CHAT);

        when(messageRepository.findById("msg1")).thenReturn(Optional.of(message));

        assertThrows(InvalidDataException.class, () -> messageService.editMessage("sender", request));
    }

    @Test
    void testRevokeMessage_Success() {
        RevokeMessageRequest request = new RevokeMessageRequest();
        request.setMessageId("msg1");
        request.setRecipient("recipient");

        ChatMessage message = new ChatMessage();
        message.setId("msg1");
        message.setSender("sender");
        message.setRecipient("recipient");
        message.setConversationId("recipient_sender");
        message.setContent("Secret");
        message.setFileUrl("url");
        message.setIv("iv123");
        message.setWrappedKeyRecipient("key_r");
        message.setWrappedKeySender("key_s");
        message.setStatus(MessageStatus.SENT);
        message.setMessageType(com.web.backend.common.MessageType.CHAT);

        when(messageRepository.findById("msg1")).thenReturn(Optional.of(message));
        CompletableFuture<SendResult<String, Object>> future = CompletableFuture
                .completedFuture(mock(SendResult.class, RETURNS_DEEP_STUBS));
        when(chatProducer.sendRevokeMessage(any())).thenReturn(future);

        messageService.revokeMessage("sender", request);

        assertTrue(message.isDeleted());
        assertEquals("", message.getContent());
        assertNull(message.getFileUrl());
        assertNull(message.getIv());
        assertNull(message.getWrappedKeyRecipient());
        assertNull(message.getWrappedKeySender());
        verify(hashOperations).increment(eq("unread_counts:recipient"), eq("sender"), eq(-1L));
        verify(chatProducer).sendRevokeMessage(any());
    }

    @Test
    void testRevokeMessage_NonChatMessage_ThrowsInvalidDataException() {
        RevokeMessageRequest request = new RevokeMessageRequest();
        request.setMessageId("msg1");
        request.setRecipient("recipient");

        ChatMessage message = new ChatMessage();
        message.setId("msg1");
        message.setSender("sender");
        message.setRecipient("recipient");
        message.setConversationId("recipient_sender");
        message.setMessageType(null);

        when(messageRepository.findById("msg1")).thenReturn(Optional.of(message));

        assertThrows(InvalidDataException.class, () -> messageService.revokeMessage("sender", request));
    }

    @Test
    void testRevokeMessage_AlreadyDeleted_Idempotent() {
        RevokeMessageRequest request = new RevokeMessageRequest();
        request.setMessageId("msg1");
        request.setRecipient("recipient");

        ChatMessage message = new ChatMessage();
        message.setId("msg1");
        message.setSender("sender");
        message.setRecipient("recipient");
        message.setConversationId("recipient_sender");
        message.setDeleted(true);
        message.setMessageType(com.web.backend.common.MessageType.CHAT);

        when(messageRepository.findById("msg1")).thenReturn(Optional.of(message));

        messageService.revokeMessage("sender", request);

        verify(chatProducer, never()).sendRevokeMessage(any());
    }

    @Test
    void testGetMessageById_Success() {
        ChatMessage message = new ChatMessage();
        message.setId("msg1");
        message.setSender("sender");
        message.setRecipient("userB");
        message.setConversationId("sender_userB");

        when(messageRepository.findById("msg1")).thenReturn(Optional.of(message));
        ChatMessageResponse response = ChatMessageResponse.builder().build();
        when(messageMapper.toResponse(message)).thenReturn(response);

        ChatMessageResponse result = messageService.getMessageById("msg1", "sender");
        assertNotNull(result);
    }

    @Test
    void testGetMessageById_Forbidden() {
        ChatMessage message = new ChatMessage();
        message.setSender("userB");
        message.setRecipient("userC");
        message.setConversationId("userB_userC");

        when(messageRepository.findById("msg1")).thenReturn(Optional.of(message));

        assertThrows(AccessForbiddenException.class, () -> messageService.getMessageById("msg1", "sender"));
    }

    @Test
    void testGetMessageById_Forbidden_SubstringMatch() {
        ChatMessage message = new ChatMessage();
        message.setSender("anh");
        message.setRecipient("hoang");
        message.setConversationId("anh_hoang");

        when(messageRepository.findById("msg1")).thenReturn(Optional.of(message));

        assertThrows(AccessForbiddenException.class, () -> messageService.getMessageById("msg1", "an"));
    }

    // ==========================================
    // TESTS FOR UNREAD COUNTS & READ STATUS
    // ==========================================

    @Test
    void testMarkMessagesAsRead_SelfSender_Ignored() {
        MarkReadRequest request = new MarkReadRequest();
        request.setSender("recipient");

        messageService.markMessagesAsRead("recipient", request);

        verify(chatProducer, never()).sendStatusMessage(any());
    }

    @Test
    void testMarkMessagesAsRead_Success() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);

        CompletableFuture<SendResult<String, Object>> future = CompletableFuture
                .completedFuture(mock(SendResult.class, RETURNS_DEEP_STUBS));
        when(chatProducer.sendStatusMessage(any())).thenReturn(future);

        MarkReadRequest request = new MarkReadRequest();
        request.setSender("sender");

        messageService.markMessagesAsRead("recipient", request);

        verify(valueOperations).set(eq("read_receipt:recipient_sender:recipient"), anyString(), any());
        verify(hashOperations).delete("unread_counts:recipient", "sender");
        verify(chatProducer).sendStatusMessage(any());
    }

    @Test
    void testMarkMessagesAsRead_KafkaFailure() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);

        CompletableFuture<SendResult<String, Object>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka down"));
        when(chatProducer.sendStatusMessage(any())).thenReturn(failedFuture);

        MarkReadRequest request = new MarkReadRequest();
        request.setSender("sender");

        messageService.markMessagesAsRead("recipient", request);

        verify(webSocketErrorHandler).handleChatError(eq("recipient"), eq(request), anyString());
    }

    @Test
    void testGetUnreadMessageCounts_RedisHit() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        Map<Object, Object> cachedCounts = new HashMap<>();
        cachedCounts.put("senderA", "5");
        when(hashOperations.entries("unread_counts:recipient")).thenReturn(cachedCounts);

        UnreadCountsResponse response = messageService.getUnreadMessageCounts("recipient");
        assertEquals(5L, response.getUnreadCounts().get("senderA"));
        verify(messageRepository, never()).countUnreadMessagesBySender(anyString());
    }

    @Test
    void testGetUnreadMessageCounts_DbFallback() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries("unread_counts:recipient")).thenReturn(Collections.emptyMap());

        UnreadCountProjection proj = mock(UnreadCountProjection.class);
        when(proj.sender()).thenReturn("senderB");
        when(proj.count()).thenReturn(3L);

        when(messageRepository.countUnreadMessagesBySender("recipient")).thenReturn(List.of(proj));

        UnreadCountsResponse response = messageService.getUnreadMessageCounts("recipient");
        assertEquals(3L, response.getUnreadCounts().get("senderB"));
        verify(hashOperations).putAll(eq("unread_counts:recipient"), anyMap());
    }

    @Test
    void testFindSystemMessageWithCursor_Initial() {
        SystemMessage sysMsg1 = new SystemMessage();
        sysMsg1.setContent("Msg 1");
        sysMsg1.setTimestamp(Instant.now());

        when(systemMessageRepository.findInitialMessage(any(Pageable.class))).thenReturn(List.of(sysMsg1));
        MessageSystemResponse response = MessageSystemResponse.builder().content("Msg 1").build();
        when(messageMapper.systemMessageToResponse(sysMsg1)).thenReturn(response);

        CursorResponse<MessageSystemResponse> result = messageService.findSystemMessageWithCursor(null, 10);

        assertFalse(result.isHasMore());
        assertEquals(1, result.getContent().size());
    }

    @Test
    void testFindPrivateMessageWithCursor_FirstPage_MergeRedis() {
        ChatMessage dbMsg = new ChatMessage();
        dbMsg.setId("msg1");
        dbMsg.setSender("user1");
        dbMsg.setRecipient("user2");
        dbMsg.setTimestamp(LocalDateTime.now().minusDays(1));

        ChatMessage redisMsg = new ChatMessage();
        redisMsg.setId("msg2");
        redisMsg.setSender("user2");
        redisMsg.setRecipient("user1");
        redisMsg.setTimestamp(LocalDateTime.now());

        when(messageRepository.findByConversationId(eq("user1_user2"), any(Pageable.class)))
                .thenReturn(List.of(dbMsg));

        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        java.util.Set<Object> mockSet = java.util.Collections.singleton((Object) redisMsg.getId());
        when(zSetOperations.reverseRange(anyString(), anyLong(), anyLong())).thenReturn(mockSet);
        when(hashOperations.multiGet(anyString(), anyCollection()))
                .thenReturn(java.util.Collections.singletonList(redisMsg));

        when(messageMapper.toResponse(any())).thenAnswer(inv -> {
            ChatMessage msg = inv.getArgument(0);
            return ChatMessageResponse.builder()
                    .id(msg.getId())
                    .sender(msg.getSender())
                    .recipient(msg.getRecipient())
                    .timestamp(msg.getTimestamp())
                    .build();
        });

        CursorResponse<ChatMessageResponse> result = messageService.findPrivateMessageWithCursor("user2", "user1", null,
                10);

        assertEquals(2, result.getContent().size());
    }

    @Test
    void testFindPrivateMessageWithCursor_CalculatesReadStatusFromWatermark() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime user2ReadTime = now.minusMinutes(5);

        ChatMessage oldMsg = new ChatMessage();
        oldMsg.setId("msg1");
        oldMsg.setSender("user1");
        oldMsg.setRecipient("user2");
        oldMsg.setTimestamp(now.minusMinutes(10)); // Before user2 read time -> READ

        ChatMessage newMsg = new ChatMessage();
        newMsg.setId("msg2");
        newMsg.setSender("user1");
        newMsg.setRecipient("user2");
        newMsg.setTimestamp(now.minusMinutes(1)); // After user2 read time -> SENT

        when(messageRepository.findByConversationId(eq("user1_user2"), any(Pageable.class)))
                .thenReturn(List.of(newMsg, oldMsg));

        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(zSetOperations.reverseRange(anyString(), anyLong(), anyLong())).thenReturn(Collections.emptySet());

        // Mock watermark for user2:
        when(valueOperations.get("read_receipt:user1_user2:user2")).thenReturn(user2ReadTime.toString());

        when(messageMapper.toResponse(any())).thenAnswer(inv -> {
            ChatMessage msg = inv.getArgument(0);
            return ChatMessageResponse.builder()
                    .id(msg.getId())
                    .sender(msg.getSender())
                    .recipient(msg.getRecipient())
                    .timestamp(msg.getTimestamp())
                    .build();
        });

        CursorResponse<ChatMessageResponse> result = messageService.findPrivateMessageWithCursor("user1", "user2", null, 10);

        assertEquals(2, result.getContent().size());
        assertEquals(MessageStatus.SENT, result.getContent().get(0).getStatus()); // newMsg
        assertEquals(MessageStatus.READ, result.getContent().get(1).getStatus()); // oldMsg
    }

    @Test
    void testReactToMessage_RemoveReaction() {
        com.web.backend.controller.request.ReactionRequest request = new com.web.backend.controller.request.ReactionRequest();
        request.setRecipient("recipient");
        request.setMessageId("msg1");
        request.setReactionType(null); // Removes reaction

        ChatMessage message = new ChatMessage();
        message.setId("msg1");
        message.setSender("sender");
        message.setRecipient("recipient");
        message.setConversationId("recipient_sender");
        when(messageRepository.findById("msg1")).thenReturn(Optional.of(message));

        when(friendService.isFriend("sender", "recipient")).thenReturn(true);

        CompletableFuture<SendResult<String, Object>> future = CompletableFuture
                .completedFuture(mock(SendResult.class, RETURNS_DEEP_STUBS));
        when(chatProducer.sendReaction(any())).thenReturn(future);

        messageService.reactToMessage("sender", request);
    }

    @Test
    void testFindPrivateMessageWithCursor_WithCursorAndHasMore() {
        ChatMessage dbMsg = new ChatMessage();
        dbMsg.setId("msg1");
        dbMsg.setSender("user1");
        dbMsg.setRecipient("user2");
        dbMsg.setTimestamp(LocalDateTime.now().minusDays(1));

        List<ChatMessage> mockResult = new java.util.ArrayList<>();
        for (int i = 0; i < 11; i++)
            mockResult.add(dbMsg); // 11 elements means hasMore = true

        when(messageRepository.findByConversationIdAndTimestampBefore(anyString(), any(), any()))
                .thenReturn(mockResult);
        when(messageMapper.toResponse(any())).thenReturn(ChatMessageResponse.builder().build());

        CursorResponse<ChatMessageResponse> result = messageService.findPrivateMessageWithCursor("user2", "user1",
                LocalDateTime.now().toString(), 10);

        assertTrue(result.isHasMore());
        assertEquals(10, result.getContent().size()); // should have removed the 11th
    }

    @Test
    void testRevokeMessage_Forbidden() {
        RevokeMessageRequest request = new RevokeMessageRequest();
        request.setMessageId("msg1");
        request.setRecipient("recipient");

        ChatMessage dbMsg = new ChatMessage();
        dbMsg.setSender("other_user");
        dbMsg.setMessageType(com.web.backend.common.MessageType.CHAT);

        when(messageRepository.findById("msg1")).thenReturn(Optional.of(dbMsg));

        assertThrows(AccessForbiddenException.class, () -> messageService.revokeMessage("sender", request));
    }

    @Test
    void testFindSystemMessageWithCursor_WithCursor() {
        SystemMessage msg = new SystemMessage();
        msg.setTimestamp(Instant.now());
        when(systemMessageRepository.findMessage(any(), any())).thenReturn(List.of(msg));
        when(messageMapper.systemMessageToResponse(any())).thenReturn(MessageSystemResponse.builder().build());

        CursorResponse<MessageSystemResponse> result = messageService.findSystemMessageWithCursor(Instant.now().toString(), 10);
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
    }

    @Test
    void testFetchMessagesFromRedisCache_NullOrEmptyMessageIds() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.reverseRange(anyString(), anyLong(), anyLong())).thenReturn(null);

        when(messageMapper.toResponse(any())).thenReturn(ChatMessageResponse.builder().build());

        CursorResponse<ChatMessageResponse> result = messageService.findPrivateMessageWithCursor("user2", "user1", null, 10);
        assertNotNull(result);
    }
}
