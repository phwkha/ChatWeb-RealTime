package com.web.backend.model.mongodb;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document("read_receipts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@CompoundIndex(name = "conv_user_idx", def = "{'conversationId': 1, 'username': 1}", unique = true)
public class ReadReceipt {

    @Id
    private String id;

    @Indexed
    private String conversationId;

    @Indexed
    private String username;

    private LocalDateTime lastReadTimestamp;

    private String lastReadMessageId;
}
