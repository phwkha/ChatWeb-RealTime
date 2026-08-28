package com.web.backend.controller.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReadReceiptResponse {
    private String conversationId;
    private String reader;
    private String sender;
    private Instant readTimestamp;
}
