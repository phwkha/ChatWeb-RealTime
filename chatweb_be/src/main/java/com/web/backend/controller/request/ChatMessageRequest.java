package com.web.backend.controller.request;

import com.web.backend.common.ContentType;
import com.web.backend.common.MessageType;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChatMessageRequest {
    @NotBlank(message = "{valid.recipient_empty}")
    @Size(max = 255)
    private String recipient;

    @Size(max = 10000, message = "{valid.msg_max_10000}")
    private String content;

    private ContentType contentType;

    @NotNull(message = "{valid.msg_type_empty}")
    private MessageType messageType;

    @Size(max = 50)
    private String color;

    @Size(max = 255)
    private String replyToId;

    @Size(max = 1000)
    private String fileUrl;

    @Size(max = 255)
    private String fileName;

    private Long fileSize;

    @Size(max = 255)
    private String iv;

    @Size(max = 1000)
    private String wrappedKeyRecipient;

    @Size(max = 1000)
    private String wrappedKeySender;

    @Size(max = 255)
    private String localId;
}