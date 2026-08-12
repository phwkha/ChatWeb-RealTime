package com.web.backend.controller.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class MessageSystemResponse {

    private String sender;
    private String content;
    private Instant timestamp;
}
