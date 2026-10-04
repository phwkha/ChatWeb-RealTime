package com.web.backend.kafka.payload;

import java.util.UUID;

import com.web.backend.common.UpdateMessageType;

import lombok.Builder;

@Builder
public record UpdateMessagePayload(
        String eventId,
        String relatedUsername,
        UpdateMessageType type,
        Object updateEvent) {
    public UpdateMessagePayload {
        if (eventId == null || eventId.isBlank()) {
            eventId = UUID.randomUUID().toString();
        }
    }
}
