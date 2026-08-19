package com.web.backend.controller.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MarkReadRequest {
    @NotBlank(message = "{valid.sender_empty}")
    @Size(max = 255)
    private String sender;
}