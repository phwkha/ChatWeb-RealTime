package com.web.backend.kafka.payload;

import com.web.backend.common.ContentType;
import com.web.backend.common.MessageType;
import com.web.backend.common.MessageStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessagePayload implements Serializable {

    private String id;
    private String localId;
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

    private LocalDateTime timestamp;
    private MessageStatus status;

    private boolean isEdited;
    private boolean isDeleted;
    private boolean isReacted;
    private Map<String, String> reactions;

    private String iv;
    private String wrappedKeyRecipient;
    private String wrappedKeySender;
}
