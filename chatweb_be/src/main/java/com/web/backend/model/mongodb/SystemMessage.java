package com.web.backend.model.mongodb;

import com.web.backend.common.SystemMessageType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("system_message")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemMessage {
    @Id
    private String id;

    private String sender;

    @Builder.Default
    private SystemMessageType type = SystemMessageType.ANNOUNCEMENT;

    private String content;

    private Instant timestamp;

    @Indexed(expireAfter = "0s")
    private Instant expiresAt;
}
