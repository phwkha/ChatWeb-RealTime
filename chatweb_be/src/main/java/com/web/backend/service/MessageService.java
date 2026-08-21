package com.web.backend.service;

import com.web.backend.controller.request.ReactionRequest;
import com.web.backend.controller.request.EditMessageRequest;
import com.web.backend.controller.request.MarkReadRequest;
import com.web.backend.controller.request.RevokeMessageRequest;
import com.web.backend.controller.response.ChatMessageResponse;
import com.web.backend.controller.response.CursorResponse;
import com.web.backend.controller.response.MessageSystemResponse;
import com.web.backend.controller.response.UnreadCountsResponse;

public interface MessageService {
    CursorResponse<ChatMessageResponse> findPrivateMessageWithCursor(String user1, String user2, String cursorStr,
            int size);

    CursorResponse<MessageSystemResponse> findSystemMessageWithCursor(String cursorStr, int size);

    UnreadCountsResponse getUnreadMessageCounts(String recipientUsername);

    void markMessagesAsRead(String recipientUsername, MarkReadRequest request);

    ChatMessageResponse reactToMessage(String senderUsername, ReactionRequest request);

    ChatMessageResponse getMessageById(String messageId, String currentUsername);

    ChatMessageResponse editMessage(String senderUsername, EditMessageRequest request);

    void revokeMessage(String senderUsername, RevokeMessageRequest request);
}
