package com.web.backend.model.mongodb;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("system_message")
@Data
public class SystemMessage {
    @Id
    private String id;

    private String sender;

    private String content;

    private Instant timestamp;

    @Indexed(expireAfter = "0s")
    private Instant expiresAt;
}
