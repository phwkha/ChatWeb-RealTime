package com.web.backend.controller.websocket;

import com.web.backend.controller.request.ChatMessageRequest;
import com.web.backend.controller.request.MessageSystemRequest;
import com.web.backend.exception.WebSocketErrorHandler;
import com.web.backend.model.UserEntity;
import com.web.backend.service.MessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.Collections;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock
    private MessageService messageService;

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

        verify(messageService).sendSystemMessage("testuser", request);
    }

    @Test
    void testSendPrivateMessage_Success() {
        ChatMessageRequest request = new ChatMessageRequest();
        request.setRecipient("otheruser");
        request.setContent("hello");

        chatController.sendPrivateMessage(request, mockAuth);

        verify(messageService).sendPrivateMessage("testuser", request);
    }

}
