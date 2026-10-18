package com.web.backend.controller.response;

import com.web.backend.common.SystemMessageType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageSystemResponse {

    private String id;
    private String sender;
    private SystemMessageType type;
    private String content;
    private Instant timestamp;
}
