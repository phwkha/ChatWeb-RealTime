package com.web.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import com.web.backend.common.MessageStatus;
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
import com.web.backend.kafka.avro.ChatMessageAvro;
import com.web.backend.kafka.producer.ChatProducer;
import com.web.backend.mapper.MessageMapper;
import com.web.backend.model.mongodb.ChatMessage;
import com.web.backend.model.mongodb.ReadReceipt;
import com.web.backend.model.mongodb.SystemMessage;
import com.web.backend.repository.MessageRepository;
import com.web.backend.repository.ReadReceiptRepository;
import com.web.backend.repository.SystemMessageRepository;
import com.web.backend.repository.projection.UnreadCountProjection;
import com.web.backend.service.NotificationService;
import com.web.backend.service.impl.MessageServiceImpl;

@ExtendWith(MockitoExtension.class)
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
    private ChatProducer chatProducer;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private ListOperations<String, Object> listOperations;
    @Mock
    private HashOperations<String, Object, Object> hashOperations;
    @Mock
    private ZSetOperations<String, Object> zSetOperations;
    @Mock
    private ValueOperations<String, Object> valueOperations;
    @Mock
    private NotificationService notificationService;

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

        lenient().when(messageMapper.toResponse(any(ChatMessage.class))).thenAnswer(inv -> {
            ChatMessage entity = inv.getArgument(0);
            if (entity == null) return null;
            return ChatMessageResponse.builder()
                    .id(entity.getId())
                    .sender(entity.getSender())
                    .recipient(entity.getRecipient())
                    .content(entity.getContent())
                    .build();
        });

        lenient().when(friendService.isFriend(anyString(), anyString())).thenReturn(true);
    }

    @Test
    void testReactToMessage_NotFriends() {
        ReactionRequest request = new ReactionRequest();
        request.setRecipient("recipient");

        when(friendService.isFriend("sender", "recipient")).thenReturn(false);

        assertThatThrownBy(() -> messageService.reactToMessage("sender", request))
                .isInstanceOf(AccessForbiddenException.class);
    }

    @Test
    void testReactToMessage_Success() {
        ReactionRequest request = new ReactionRequest();
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

        messageService.reactToMessage("sender", request);

        // Verify Redis updated
        verify(redisTemplate, atLeastOnce()).opsForHash();

        // Verify Kafka event published
        verify(chatProducer).sendChatMessage(any(ChatMessageAvro.class));
    }

    @Test
    void testReactToMessage_Forbidden_DifferentConversation() {
        ReactionRequest request = new ReactionRequest();
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

        assertThatThrownBy(() -> messageService.reactToMessage("sender", request))
                .isInstanceOf(AccessForbiddenException.class);
    }

    @Test
    void testReactToMessage_AlreadyDeleted() {
        ReactionRequest request = new ReactionRequest();
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

        assertThatThrownBy(() -> messageService.reactToMessage("sender", request))
                .isInstanceOf(InvalidDataException.class);
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

        messageService.editMessage("sender", request);

        verify(chatProducer).sendChatMessage(any(ChatMessageAvro.class));
    }

    @Test
    void testEditMessage_NotFoundInDb_ThrowsResourceNotFoundException() {
        EditMessageRequest request = new EditMessageRequest();
        request.setMessageId("msg1");
        request.setNewContent("Edited text");
        request.setRecipient("recipient");

        when(messageRepository.findById("msg1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> messageService.editMessage("sender", request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void testEditMessage_InRedis_SucceedsImmediately() {
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

        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.get("chat:recent:hash:recipient_sender", "msg1")).thenReturn(message);

        ChatMessageResponse response = messageService.editMessage("sender", request);
        assertThat(response).isNotNull();
        verify(chatProducer).sendChatMessage(any(ChatMessageAvro.class));
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

        assertThatThrownBy(() -> messageService.editMessage("sender", request))
                .isInstanceOf(AccessForbiddenException.class);
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

        assertThatThrownBy(() -> messageService.editMessage("sender", request))
                .isInstanceOf(InvalidDataException.class);
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

        assertThatThrownBy(() -> messageService.editMessage("sender", request))
                .isInstanceOf(InvalidDataException.class);
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
        message.setStatus(MessageStatus.SENT);
        message.setMessageType(com.web.backend.common.MessageType.CHAT);

        when(messageRepository.findById("msg1")).thenReturn(Optional.of(message));

        messageService.revokeMessage("sender", request);

        verify(redisTemplate).delete("unread_counts:recipient");
        verify(hashOperations, never()).increment(anyString(), anyString(), anyLong());
        verify(chatProducer).sendChatMessage(any(ChatMessageAvro.class));
    }

    @Test
    void testRevokeMessage_AlreadyReadStatus_DoesNotDecrementUnreadCount() {
        RevokeMessageRequest request = new RevokeMessageRequest();
        request.setMessageId("msg1");
        request.setRecipient("recipient");

        ChatMessage message = new ChatMessage();
        message.setId("msg1");
        message.setConversationId("recipient_sender");
        message.setSender("sender");
        message.setRecipient("recipient");
        message.setContent("Hello");
        message.setStatus(MessageStatus.READ);
        message.setMessageType(com.web.backend.common.MessageType.CHAT);

        when(messageRepository.findById("msg1")).thenReturn(Optional.of(message));

        messageService.revokeMessage("sender", request);

        verify(hashOperations, never()).increment(anyString(), anyString(), anyLong());
        verify(chatProducer).sendChatMessage(any(ChatMessageAvro.class));
    }

    @Test
    void testRevokeMessage_AlreadyReadViaWatermark_DoesNotDecrementUnreadCount() {
        RevokeMessageRequest request = new RevokeMessageRequest();
        request.setMessageId("msg1");
        request.setRecipient("recipient");

        Instant msgTime = Instant.parse("2026-09-17T10:00:00Z");
        ChatMessage message = new ChatMessage();
        message.setId("msg1");
        message.setConversationId("recipient_sender");
        message.setSender("sender");
        message.setRecipient("recipient");
        message.setContent("Hello");
        message.setTimestamp(msgTime);
        message.setStatus(MessageStatus.SENT); // Still SENT in stale cache
        message.setMessageType(com.web.backend.common.MessageType.CHAT);

        when(messageRepository.findById("msg1")).thenReturn(Optional.of(message));

        messageService.revokeMessage("sender", request);

        verify(hashOperations, never()).increment(anyString(), anyString(), anyLong());
        verify(chatProducer).sendChatMessage(any(ChatMessageAvro.class));
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

        assertThatThrownBy(() -> messageService.revokeMessage("sender", request))
                .isInstanceOf(InvalidDataException.class);
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

        verify(eventPublisher, never()).publishEvent(any());
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
        assertThat(result).isNotNull();
    }

    @Test
    void testGetMessageById_Forbidden() {
        ChatMessage message = new ChatMessage();
        message.setSender("userB");
        message.setRecipient("userC");
        message.setConversationId("userB_userC");

        when(messageRepository.findById("msg1")).thenReturn(Optional.of(message));

        assertThatThrownBy(() -> messageService.getMessageById("msg1", "sender"))
                .isInstanceOf(AccessForbiddenException.class);
    }

    @Test
    void testGetMessageById_Forbidden_SubstringMatch() {
        ChatMessage message = new ChatMessage();
        message.setSender("anh");
        message.setRecipient("hoang");
        message.setConversationId("anh_hoang");

        when(messageRepository.findById("msg1")).thenReturn(Optional.of(message));

        assertThatThrownBy(() -> messageService.getMessageById("msg1", "an"))
                .isInstanceOf(AccessForbiddenException.class);
    }

    // ==========================================
    // TESTS FOR UNREAD COUNTS & READ STATUS
    // ==========================================

    @Test
    void testMarkMessagesAsRead_SelfSender_Ignored() {
        MarkReadRequest request = new MarkReadRequest();
        request.setSender("recipient");

        messageService.markMessagesAsRead("recipient", request);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void testMarkMessagesAsRead_Success() {
        when(friendService.isFriend("recipient", "sender")).thenReturn(true);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        MarkReadRequest request = new MarkReadRequest();
        request.setSender("sender");

        messageService.markMessagesAsRead("recipient", request);

        verify(valueOperations).set(eq("read_receipt:recipient_sender:recipient"), anyString(), any());
        verify(redisTemplate).delete("unread_counts:recipient");
        verify(mongoTemplate).upsert(any(Query.class), any(Update.class), eq(ReadReceipt.class));
        verify(eventPublisher).publishEvent(any(ReadReceiptResponse.class));
    }

    @Test
    void testMarkMessagesAsRead_NotFriends_ThrowsException() {
        when(friendService.isFriend("recipient", "stranger")).thenReturn(false);

        MarkReadRequest request = new MarkReadRequest();
        request.setSender("stranger");

        assertThatThrownBy(() -> messageService.markMessagesAsRead("recipient", request))
                .isInstanceOf(AccessForbiddenException.class);
    }

    @Test
    void testGetUnreadMessageCounts_RedisHit() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        Map<Object, Object> cachedCounts = new HashMap<>();
        cachedCounts.put("senderA", "5");
        when(hashOperations.entries("unread_counts:recipient")).thenReturn(cachedCounts);

        UnreadCountsResponse response = messageService.getUnreadMessageCounts("recipient");
        assertThat(response.getUnreadCounts().get("senderA")).isEqualTo(5L);
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
        assertThat(response.getUnreadCounts().get("senderB")).isEqualTo(3L);
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

        assertThat(result.isHasMore()).isFalse();
        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void testFindPrivateMessageWithCursor_FirstPage_MergeRedis() {
        ChatMessage dbMsg = new ChatMessage();
        dbMsg.setId("msg1");
        dbMsg.setSender("user1");
        dbMsg.setRecipient("user2");
        dbMsg.setTimestamp(Instant.now().minusSeconds(86400));

        ChatMessage redisMsg = new ChatMessage();
        redisMsg.setId("msg2");
        redisMsg.setSender("user2");
        redisMsg.setRecipient("user1");
        redisMsg.setTimestamp(Instant.now());

        when(messageRepository.findByConversationId(eq("user1_user2"), any(Pageable.class)))
                .thenReturn(List.of(dbMsg));

        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        java.util.Set<Object> mockSet = Collections.singleton(redisMsg.getId());
        when(zSetOperations.reverseRange(anyString(), anyLong(), anyLong())).thenReturn(mockSet);
        when(hashOperations.multiGet(anyString(), anyCollection()))
                .thenReturn(Collections.singletonList(redisMsg));

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

        assertThat(result.getContent()).hasSize(2);
    }

    @Test
    void testFindPrivateMessageWithCursor_HasMoreMessages_TrimsExtraMessage() {
        ChatMessage dbMsg1 = new ChatMessage();
        dbMsg1.setId("msg1");
        dbMsg1.setSender("user1");
        dbMsg1.setRecipient("user2");
        dbMsg1.setTimestamp(Instant.now().minusSeconds(100));

        ChatMessage dbMsg2 = new ChatMessage();
        dbMsg2.setId("msg2");
        dbMsg2.setSender("user1");
        dbMsg2.setRecipient("user2");
        dbMsg2.setTimestamp(Instant.now().minusSeconds(200));

        when(messageRepository.findByConversationId(eq("user1_user2"), any(Pageable.class)))
                .thenReturn(List.of(dbMsg1, dbMsg2));

        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(zSetOperations.reverseRange(anyString(), anyLong(), anyLong())).thenReturn(Collections.emptySet());

        when(messageMapper.toResponse(any())).thenAnswer(inv -> {
            ChatMessage msg = inv.getArgument(0);
            return ChatMessageResponse.builder()
                    .id(msg.getId())
                    .sender(msg.getSender())
                    .recipient(msg.getRecipient())
                    .timestamp(msg.getTimestamp())
                    .build();
        });

        // Request pageSize = 1 with 2 messages available in DB -> hasMore should be
        // true and trimmed to 1
        CursorResponse<ChatMessageResponse> result = messageService.findPrivateMessageWithCursor("user2", "user1", null,
                1);

        assertThat(result.isHasMore()).isTrue();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo("msg1");
        assertThat(result.getNextCursor()).isNotNull();
    }

    @Test
    void testFindPrivateMessageWithCursor_CalculatesReadStatusFromWatermark() {
        Instant now = Instant.now();
        Instant user2ReadTime = now.minusSeconds(300);

        ChatMessage oldMsg = new ChatMessage();
        oldMsg.setId("msg1");
        oldMsg.setSender("user1");
        oldMsg.setRecipient("user2");
        oldMsg.setTimestamp(now.minusSeconds(600)); // Before user2 read time -> READ

        ChatMessage newMsg = new ChatMessage();
        newMsg.setId("msg2");
        newMsg.setSender("user1");
        newMsg.setRecipient("user2");
        newMsg.setTimestamp(now.minusSeconds(60)); // After user2 read time -> SENT

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

        CursorResponse<ChatMessageResponse> result = messageService.findPrivateMessageWithCursor("user1", "user2", null,
                10);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).getStatus()).isEqualTo(MessageStatus.SENT); // newMsg
        assertThat(result.getContent().get(1).getStatus()).isEqualTo(MessageStatus.READ); // oldMsg
    }

    @Test
    void testReactToMessage_RemoveReaction() {
        ReactionRequest request = new ReactionRequest();
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

        messageService.reactToMessage("sender", request);

        verify(chatProducer).sendChatMessage(any(ChatMessageAvro.class));
    }

    @Test
    void testFindPrivateMessageWithCursor_WithCursorAndHasMore() {
        ChatMessage dbMsg = new ChatMessage();
        dbMsg.setId("msg1");
        dbMsg.setSender("user1");
        dbMsg.setRecipient("user2");
        dbMsg.setTimestamp(Instant.now().minusSeconds(86400));

        List<ChatMessage> mockResult = new java.util.ArrayList<>();
        for (int i = 0; i < 11; i++) {
            mockResult.add(dbMsg); // 11 elements means hasMore = true
        }

        when(messageRepository.findByConversationIdAndTimestampBefore(anyString(), any(), any()))
                .thenReturn(mockResult);
        when(messageMapper.toResponse(any())).thenReturn(ChatMessageResponse.builder().build());

        CursorResponse<ChatMessageResponse> result = messageService.findPrivateMessageWithCursor("user2", "user1",
                Instant.now().toString(), 10);

        assertThat(result.isHasMore()).isTrue();
        assertThat(result.getContent()).hasSize(10); // should have removed the 11th
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

        assertThatThrownBy(() -> messageService.revokeMessage("sender", request))
                .isInstanceOf(AccessForbiddenException.class);
    }

    @Test
    void testFindSystemMessageWithCursor_WithCursor() {
        SystemMessage msg = new SystemMessage();
        msg.setTimestamp(Instant.now());
        when(systemMessageRepository.findMessage(any(), any())).thenReturn(List.of(msg));
        when(messageMapper.systemMessageToResponse(any())).thenReturn(MessageSystemResponse.builder().build());

        CursorResponse<MessageSystemResponse> result = messageService
                .findSystemMessageWithCursor(Instant.now().toString(), 10);
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void testFetchMessagesFromRedisCache_NullOrEmptyMessageIds() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.reverseRange(anyString(), anyLong(), anyLong())).thenReturn(null);

        CursorResponse<ChatMessageResponse> result = messageService.findPrivateMessageWithCursor("user2", "user1", null,
                10);
        assertThat(result).isNotNull();
    }

    @Test
    void testSearchMessages_EmptyKeyword() {
        CursorResponse<ChatMessageResponse> result = messageService.searchMessages("user1", "user2", "   ", null, 20);
        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEmpty();
        assertThat(result.isHasMore()).isFalse();
        assertThat(result.getNextCursor()).isNull();
        verifyNoInteractions(mongoTemplate);
    }

    @Test
    void testSearchMessages_Success() {
        ChatMessage msg = new ChatMessage();
        msg.setId("msg1");
        msg.setContent("Hello there");
        msg.setTimestamp(Instant.now());
        msg.setSender("user1");
        msg.setRecipient("user2");

        when(mongoTemplate.find(any(Query.class), eq(ChatMessage.class))).thenReturn(List.of(msg));
        when(messageMapper.toResponse(any()))
                .thenReturn(ChatMessageResponse.builder().id("msg1").content("Hello there").build());

        CursorResponse<ChatMessageResponse> result = messageService.searchMessages("user1", "user2", "Hello", null, 20);
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo("msg1");
        assertThat(result.isHasMore()).isFalse();
    }

    @Test
    void testSearchMessages_WithCursor() {
        ChatMessage msg = new ChatMessage();
        msg.setId("msg2");
        msg.setContent("Testing cursor");
        msg.setTimestamp(Instant.now());
        msg.setSender("user2");
        msg.setRecipient("user1");

        when(mongoTemplate.find(any(Query.class), eq(ChatMessage.class))).thenReturn(List.of(msg));
        when(messageMapper.toResponse(any()))
                .thenReturn(ChatMessageResponse.builder().id("msg2").content("Testing cursor").build());

        CursorResponse<ChatMessageResponse> result = messageService.searchMessages("user1", "user2", "cursor",
                Instant.now().toString(), 20);
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo("msg2");
    }
}
