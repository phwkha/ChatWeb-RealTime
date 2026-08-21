package com.web.backend.controller.websocket;

import com.web.backend.controller.request.ChatMessageRequest;
import com.web.backend.controller.request.MessageSystemRequest;
import com.web.backend.model.UserEntity;
import com.web.backend.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
@Slf4j(topic = "CHAT-CONTROLLER")
public class ChatController {

    private final ChatService chatService;

    @MessageMapping("/chat/sendMessageSystem")
    @PreAuthorize("hasAuthority('ADMIN_SEND-MESSAGE')")
    public void sendMessage(@Payload @Valid MessageSystemRequest request,
            Authentication authentication) {

        UserEntity userPrincipal = (UserEntity) authentication.getPrincipal();
        String currentUsername = userPrincipal.getUsername();

        log.debug("STOMP public system message received from admin '{}'", currentUsername);
        chatService.sendSystemMessage(currentUsername, request);
    }

    @MessageMapping("/chat/sendPrivateMessage")
    public void sendPrivateMessage(@Payload @Valid ChatMessageRequest request, Authentication authentication) {

        UserEntity userPrincipal = (UserEntity) authentication.getPrincipal();
        String senderUsername = userPrincipal.getUsername();

        log.debug("STOMP private message received: sender='{}', recipient='{}'", senderUsername,
                request.getRecipient());
        chatService.sendPrivateMessage(senderUsername, request);
    }
}