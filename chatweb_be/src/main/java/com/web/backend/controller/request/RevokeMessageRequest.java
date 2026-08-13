package com.web.backend.controller.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RevokeMessageRequest {
    @NotBlank(message = "{valid.msg_id_empty}")
    private String messageId;

    @NotBlank(message = "{valid.recipient_empty}")
    private String recipient;
}
