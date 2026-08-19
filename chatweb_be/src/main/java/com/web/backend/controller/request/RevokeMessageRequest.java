package com.web.backend.controller.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RevokeMessageRequest {
    @NotBlank(message = "{valid.msg_id_empty}")
    @Size(max = 255)
    private String messageId;

    @NotBlank(message = "{valid.recipient_empty}")
    @Size(max = 255)
    private String recipient;
}
