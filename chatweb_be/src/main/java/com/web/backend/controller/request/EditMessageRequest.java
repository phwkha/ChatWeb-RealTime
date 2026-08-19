package com.web.backend.controller.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EditMessageRequest {
    @NotBlank(message = "{valid.msg_id_empty}")
    private String messageId;

    @NotBlank(message = "{valid.recipient_empty}")
    private String recipient;

    @NotBlank(message = "{valid.content_empty}")
    @Size(max = 10000, message = "{valid.msg_max_10000}")
    private String newContent;

    private String iv;
    private String wrappedKeyRecipient;
    private String wrappedKeySender;
}
