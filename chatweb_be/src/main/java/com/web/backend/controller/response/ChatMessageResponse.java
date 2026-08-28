package com.web.backend.controller.response;

import com.web.backend.common.ContentType;
import com.web.backend.common.MessageStatus;
import com.web.backend.common.MessageType;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageResponse {

    private String id;
    private String conversationId;

    private String sender;
    private String recipient;

    private String content;
    private ContentType contentType;
    private MessageType messageType;
    private String color;

    private String replyToId;

    private String fileUrl;
    private String fileName;
    private Long fileSize;

    private Instant timestamp;

    private MessageStatus status;

    private boolean isEdited;
    private boolean isDeleted;
    private boolean isReacted;

    private Map<String, String> reactions;

    private String iv;
    private String wrappedKeyRecipient;
    private String wrappedKeySender;

    private String localId;
}