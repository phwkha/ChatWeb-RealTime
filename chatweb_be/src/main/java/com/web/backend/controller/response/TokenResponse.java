package com.web.backend.controller.response;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class TokenResponse {

    private String accessToken;
    private String refreshToken;
}
