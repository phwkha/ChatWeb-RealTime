package com.web.backend.controller.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EditMessageRequest {
    @NotBlank(message = "{valid.msg_id_empty}")
    @Size(max = 255)
    private String messageId;

    @NotBlank(message = "{valid.recipient_empty}")
    @Size(max = 255)
    private String recipient;

    @NotBlank(message = "{valid.content_empty}")
    @Size(max = 10000, message = "{valid.msg_max_10000}")
    private String newContent;

    @Size(max = 255)
    private String iv;

    @Size(max = 1000)
    private String wrappedKeyRecipient;

    @Size(max = 1000)
    private String wrappedKeySender;
}
