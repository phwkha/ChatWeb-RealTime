package com.web.backend.model.redis;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RedisWsMessage {
    private String recipient;
    private String destination;
    private Object payload;
}
