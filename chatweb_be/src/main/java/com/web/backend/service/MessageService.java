package com.web.backend.service;

import com.web.backend.controller.request.ChatMessageRequest;
import com.web.backend.controller.request.MessageSystemRequest;
import com.web.backend.controller.request.ReactionRequest;
import com.web.backend.controller.request.EditMessageRequest;
import com.web.backend.controller.request.MarkReadRequest;
import com.web.backend.controller.request.RevokeMessageRequest;
import com.web.backend.controller.response.ChatMessageResponse;
import com.web.backend.controller.response.CursorResponse;
import com.web.backend.controller.response.MessageSystemResponse;
import com.web.backend.controller.response.UnreadCountsResponse;

public interface MessageService {
    void sendPrivateMessage(String sender, ChatMessageRequest request);

    void sendSystemMessage(String currentUsername, MessageSystemRequest request);

    CursorResponse<ChatMessageResponse> findPrivateMessageWithCursor(String user1, String user2, String cursorStr,
            int size);

    CursorResponse<MessageSystemResponse> findSystemMessageWithCursor(String cursorStr, int size);

    UnreadCountsResponse getUnreadMessageCounts(String recipientUsername);

    void markMessagesAsRead(String recipientUsername, MarkReadRequest request);

    void reactToMessage(String senderUsername, ReactionRequest request);

    ChatMessageResponse getMessageById(String messageId, String currentUsername);

    void editMessage(String senderUsername, EditMessageRequest request);

    void revokeMessage(String senderUsername, RevokeMessageRequest request);
}
