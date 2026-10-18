package com.web.backend.service;

import com.web.backend.controller.request.ChatMessageRequest;
import com.web.backend.controller.request.MessageSystemRequest;
import com.web.backend.controller.request.WorldChatRequest;
import com.web.backend.model.postgres.UserEntity;

public interface ChatService {
    void sendPrivateMessage(String sender, ChatMessageRequest request);

    void sendSystemMessage(String currentUsername, MessageSystemRequest request);

    void sendWorldChatMessage(UserEntity user, WorldChatRequest request);
}
