package com.web.backend.controller.websocket;

import com.web.backend.controller.request.ChatMessageRequest;
import com.web.backend.controller.request.MessageSystemRequest;
import com.web.backend.controller.request.WorldChatRequest;
import com.web.backend.exception.WebSocketErrorHandler;
import com.web.backend.model.postgres.UserEntity;
import com.web.backend.service.ChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.lang.reflect.Method;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock
    private ChatService chatService;

    @Mock
    private WebSocketErrorHandler webSocketErrorHandler;

    @InjectMocks
    private ChatController chatController;

    private UsernamePasswordAuthenticationToken mockAuth;
    private UserEntity mockUser;

    @BeforeEach
    void setUp() {
        mockUser = new UserEntity();
        mockUser.setUsername("testuser");
        mockAuth = new UsernamePasswordAuthenticationToken(mockUser, null, Collections.emptyList());
    }

    @Test
    void testSendMessageSystem_Success() {
        MessageSystemRequest request = new MessageSystemRequest();
        request.setContent("system message");

        chatController.sendMessage(request, mockAuth);

        verify(chatService).sendSystemMessage("testuser", request);
    }

    @Test
    void testSendPrivateMessage_Success() {
        ChatMessageRequest request = new ChatMessageRequest();
        request.setRecipient("otheruser");
        request.setContent("hello");

        chatController.sendPrivateMessage(request, mockAuth);

        verify(chatService).sendPrivateMessage("testuser", request);
    }

    @Test
    void testSendWorldChat_Success() {
        WorldChatRequest request = new WorldChatRequest("Hello World Chat!");

        chatController.sendWorldChat(request, mockAuth);

        verify(chatService).sendWorldChatMessage(mockUser, request);
    }

    @Test
    void testSendWorldChat_SecurityAndMappingAnnotations() throws NoSuchMethodException {
        Method method = ChatController.class.getMethod("sendWorldChat", WorldChatRequest.class, org.springframework.security.core.Authentication.class);

        MessageMapping messageMapping = method.getAnnotation(MessageMapping.class);
        assertThat(messageMapping).isNotNull();
        assertThat(messageMapping.value()).containsExactly("/chat/sendWorldChat");

        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo("hasAnyAuthority('USER_SEND_WORLD_CHAT', 'ADMIN_SEND-MESSAGE')");
    }

    @Test
    void testSendMessageSystem_SecurityAndMappingAnnotations() throws NoSuchMethodException {
        Method method = ChatController.class.getMethod("sendMessage", MessageSystemRequest.class, org.springframework.security.core.Authentication.class);

        MessageMapping messageMapping = method.getAnnotation(MessageMapping.class);
        assertThat(messageMapping).isNotNull();
        assertThat(messageMapping.value()).containsExactly("/chat/sendMessageSystem");

        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo("hasAuthority('ADMIN_SEND-MESSAGE')");
    }

    @Test
    void testSendPrivateMessage_MappingAnnotation() throws NoSuchMethodException {
        Method method = ChatController.class.getMethod("sendPrivateMessage", ChatMessageRequest.class, org.springframework.security.core.Authentication.class);

        MessageMapping messageMapping = method.getAnnotation(MessageMapping.class);
        assertThat(messageMapping).isNotNull();
        assertThat(messageMapping.value()).containsExactly("/chat/sendPrivateMessage");
    }
}
