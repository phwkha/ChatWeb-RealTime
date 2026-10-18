package com.web.backend.controller.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorldChatRequest {

    @NotBlank(message = "{valid.content_empty}")
    @Size(max = 500, message = "{valid.content_too_long}")
    private String content;
}
