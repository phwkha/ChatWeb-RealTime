package com.web.backend.kafka.payload;

import com.web.backend.common.UpdateMessageType;

import lombok.Builder;

@Builder
public record UpdateMessagePayload(
        String relatedUsername,
        UpdateMessageType type,
        Object updateEvent) {
}
