package com.web.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.kafka.support.SendResult;

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
import com.web.backend.exception.custom.TooManyRequestsException;
import com.web.backend.kafka.avro.ChatMessageAvro;
import com.web.backend.kafka.producer.ChatProducer;
import com.web.backend.mapper.MessageMapper;
import com.web.backend.model.mongodb.ChatMessage;
import com.web.backend.model.mongodb.SystemMessage;
import com.web.backend.model.postgres.UserEntity;
import com.web.backend.repository.SystemMessageRepository;
import com.web.backend.repository.UserRepository;
import com.web.backend.service.RateLimitingService;
import com.web.backend.service.impl.ChatServiceImpl;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class ChatServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private SystemMessageRepository systemMessageRepository;
    @Mock
    private FriendService friendService;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private MessageMapper messageMapper;
    @Mock
    private ChatProducer chatProducer;
    @Mock
    private WebSocketErrorHandler webSocketErrorHandler;
    @Mock
    private RateLimitingService rateLimitingService;

    @Mock
    private ListOperations<String, Object> listOperations;
    @Mock
    private HashOperations<String, Object, Object> hashOperations;
    @Mock
    private ZSetOperations<String, Object> zSetOperations;

    @InjectMocks
    private ChatServiceImpl chatService;

    private UserEntity recipientUser;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messageSource = mock(ResourceBundleMessageSource.class);
        lenient().when(messageSource.getMessage(anyString(), any(), any())).thenReturn("Mocked Error Message");
        Translator.setStaticMessageSource(messageSource);

        recipientUser = new UserEntity();
        recipientUser.setUsername("recipient");
        recipientUser.setUserStatus(UserStatus.ACTIVE);

        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        lenient().when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        lenient().when(redisTemplate.opsForList()).thenReturn(listOperations);
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
        lenient().when(rateLimitingService.isAllowed(anyString(), anyInt(), anyLong())).thenReturn(true);
    }

    @Test
    void testSendPrivateMessage_RecipientNotFound() {
        when(userRepository.findUserStatusByUsername("recipient")).thenReturn(Optional.empty());

        ChatMessageRequest request = new ChatMessageRequest();
        request.setRecipient("recipient");
        request.setContent("Hello!");
        request.setMessageType(MessageType.CHAT);

        assertThrows(ResourceNotFoundException.class, () -> chatService.sendPrivateMessage("sender", request));
    }

    @Test
    void testSendPrivateMessage_SelfSend_ThrowsInvalidDataException() {
        ChatMessageRequest request = new ChatMessageRequest();
        request.setRecipient("sender");
        request.setContent("Hello myself!");

        assertThrows(InvalidDataException.class, () -> chatService.sendPrivateMessage("sender", request));
    }

    @Test
    void testSendPrivateMessage_EmptyContentAndFile_ThrowsInvalidDataException() {
        ChatMessageRequest request = new ChatMessageRequest();
        request.setRecipient("recipient");
        request.setContent("   ");
        request.setFileUrl(null);
        request.setMessageType(MessageType.CHAT);

        assertThrows(InvalidDataException.class, () -> chatService.sendPrivateMessage("sender", request));
    }

    @Test
    void testSendPrivateMessage_RecipientInactive() {
        when(userRepository.findUserStatusByUsername("recipient")).thenReturn(Optional.of(UserStatus.INACTIVE));

        ChatMessageRequest request = new ChatMessageRequest();
        request.setRecipient("recipient");
        request.setContent("Hello!");
        request.setMessageType(MessageType.CHAT);

        assertThrows(AccessForbiddenException.class, () -> chatService.sendPrivateMessage("sender", request));
    }

    @Test
    void testSendPrivateMessage_RecipientLocked() {
        when(userRepository.findUserStatusByUsername("recipient")).thenReturn(Optional.of(UserStatus.LOCKED));

        ChatMessageRequest request = new ChatMessageRequest();
        request.setRecipient("recipient");
        request.setContent("Hello!");
        request.setMessageType(MessageType.CHAT);

        assertThrows(AccessForbiddenException.class, () -> chatService.sendPrivateMessage("sender", request));
    }

    @Test
    void testSendPrivateMessage_NotFriends() {
        when(userRepository.findUserStatusByUsername("recipient")).thenReturn(Optional.of(UserStatus.ACTIVE));
        when(friendService.isFriend("sender", "recipient")).thenReturn(false);

        ChatMessageRequest request = new ChatMessageRequest();
        request.setRecipient("recipient");
        request.setContent("Hello!");
        request.setMessageType(MessageType.CHAT);

        assertThrows(AccessForbiddenException.class, () -> chatService.sendPrivateMessage("sender", request));
    }

    @Test
    void testSendPrivateMessage_Success() {
        when(userRepository.findUserStatusByUsername("recipient")).thenReturn(Optional.of(UserStatus.ACTIVE));
        when(friendService.isFriend("sender", "recipient")).thenReturn(true);

        ChatMessageRequest request = new ChatMessageRequest();
        request.setRecipient("recipient");
        request.setContent("Hello!");
        request.setMessageType(MessageType.CHAT);

        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setMessageType(MessageType.CHAT);
        when(messageMapper.toEntity(request)).thenReturn(chatMessage);

        CompletableFuture<SendResult<String, ChatMessageAvro>> future = CompletableFuture
                .completedFuture(mock(SendResult.class, RETURNS_DEEP_STUBS));
        when(chatProducer.sendChatMessage(any())).thenReturn(future);

        chatService.sendPrivateMessage("sender", request);

        verify(chatProducer).sendChatMessage(any(ChatMessageAvro.class));
        assertFalse(chatMessage.isEdited());
        assertFalse(chatMessage.isDeleted());
        assertFalse(chatMessage.isReacted());
        assertNull(chatMessage.getReactions());
    }

    @Test
    void testSendSystemMessage_Success() {
        MessageSystemRequest request = new MessageSystemRequest();
        request.setContent("System rebooting in 5 mins");
        request.setSurvivalTime(300L);

        when(systemMessageRepository.save(any(SystemMessage.class))).thenReturn(new SystemMessage());

        CompletableFuture<SendResult<String, Object>> future = CompletableFuture
                .completedFuture(mock(SendResult.class, RETURNS_DEEP_STUBS));
        when(chatProducer.sendSystemMessage(any())).thenReturn(future);

        chatService.sendSystemMessage("admin", request);

        verify(systemMessageRepository).save(any(SystemMessage.class));
        verify(chatProducer).sendSystemMessage(any(SystemMessage.class));
    }

    @Test
    void testSendPrivateMessage_KafkaException() {
        when(userRepository.findUserStatusByUsername("recipient")).thenReturn(Optional.of(UserStatus.ACTIVE));
        when(friendService.isFriend("sender", "recipient")).thenReturn(true);

        ChatMessageRequest request = new ChatMessageRequest();
        request.setRecipient("recipient");
        request.setContent("Hello!");
        request.setMessageType(MessageType.CHAT);

        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setId("msg123");
        chatMessage.setMessageType(MessageType.CHAT);
        chatMessage.setTimestamp(Instant.now());
        when(messageMapper.toEntity(request)).thenReturn(chatMessage);

        CompletableFuture<SendResult<String, ChatMessageAvro>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka down"));
        when(chatProducer.sendChatMessage(any())).thenReturn(failedFuture);

        chatService.sendPrivateMessage("sender", request);

        verify(webSocketErrorHandler).handleChatError(eq("sender"), eq(request), anyString());
    }

    @Test
    void testSendSystemMessage_KafkaException() {
        MessageSystemRequest request = new MessageSystemRequest();
        request.setContent("System rebooting in 5 mins");

        when(systemMessageRepository.save(any(SystemMessage.class))).thenReturn(new SystemMessage());

        CompletableFuture<SendResult<String, Object>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka down"));
        when(chatProducer.sendSystemMessage(any())).thenReturn(failedFuture);

        chatService.sendSystemMessage("admin", request);

        verify(systemMessageRepository).save(any(SystemMessage.class));
        verify(webSocketErrorHandler, never()).handleChatError(any(), any(), any());
    }

    @Test
    void testSendSystemMessage_DbException_ThrowsSystemOverloadException() {
        MessageSystemRequest request = new MessageSystemRequest();
        request.setContent("System rebooting in 5 mins");

        when(systemMessageRepository.save(any(SystemMessage.class))).thenThrow(new RuntimeException("MongoDB down"));

        assertThrows(SystemOverloadException.class, () -> chatService.sendSystemMessage("admin", request));
        verify(webSocketErrorHandler, never()).handleChatError(any(), any(), any());
        verify(chatProducer, never()).sendSystemMessage(any());
    }

    @Test
    void testBuildChatMessage_NullFields() {
        when(userRepository.findUserStatusByUsername("recipient")).thenReturn(Optional.of(UserStatus.ACTIVE));
        when(friendService.isFriend("sender", "recipient")).thenReturn(true);

        ChatMessageRequest request = new ChatMessageRequest();
        request.setRecipient("recipient");
        request.setContent("File Msg");
        request.setFileUrl("http://file.com");
        request.setMessageType(MessageType.CHAT);

        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setContent(null);
        chatMessage.setContentType(null);
        chatMessage.setTimestamp(null);
        chatMessage.setMessageType(MessageType.CHAT);
        when(messageMapper.toEntity(request)).thenReturn(chatMessage);

        CompletableFuture<SendResult<String, ChatMessageAvro>> future = CompletableFuture
                .completedFuture(mock(SendResult.class, RETURNS_DEEP_STUBS));
        when(chatProducer.sendChatMessage(any())).thenReturn(future);

        chatService.sendPrivateMessage("sender", request);

        assertEquals("", chatMessage.getContent());
        assertEquals(com.web.backend.common.ContentType.TEXT, chatMessage.getContentType());
        assertNotNull(chatMessage.getTimestamp());
    }

    @Test
    void testSendPrivateMessage_RateLimitExceeded_ThrowsTooManyRequestsException() {
        when(rateLimitingService.isAllowed(anyString(), anyInt(), anyLong())).thenReturn(false);

        ChatMessageRequest request = new ChatMessageRequest();
        request.setRecipient("recipient");
        request.setContent("Hello!");
        request.setMessageType(MessageType.CHAT);

        assertThrows(TooManyRequestsException.class, () -> chatService.sendPrivateMessage("sender", request));
    }

    @Test
    void testSendPrivateMessage_InvalidMessageType_ThrowsInvalidDataException() {
        ChatMessageRequest request = new ChatMessageRequest();
        request.setRecipient("recipient");
        request.setContent("Hello!");
        request.setMessageType(null);

        assertThrows(InvalidDataException.class, () -> chatService.sendPrivateMessage("sender", request));
    }
}
