package com.web.backend.controller.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReadReceiptData {
    private String conversationId;
    private String reader;
    private String sender;
    private LocalDateTime readTimestamp;
}
