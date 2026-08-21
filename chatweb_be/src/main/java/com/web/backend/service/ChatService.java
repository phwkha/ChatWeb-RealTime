package com.web.backend.service;

import com.web.backend.controller.request.ChatMessageRequest;
import com.web.backend.controller.request.MessageSystemRequest;

public interface ChatService {
    void sendPrivateMessage(String sender, ChatMessageRequest request);

    void sendSystemMessage(String currentUsername, MessageSystemRequest request);
}
