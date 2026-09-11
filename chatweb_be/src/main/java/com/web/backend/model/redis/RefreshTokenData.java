package com.web.backend.model.redis;

import java.io.Serializable;
import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshTokenData implements Serializable {
    private static final long serialVersionUID = 1L;

    private String username;
    private Integer tokenVersion;
    private Instant createdAt;
}
