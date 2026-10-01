package com.web.backend.service;

import io.jsonwebtoken.Claims;

import java.util.List;
import java.util.function.Function;

import com.web.backend.controller.response.TokenResponse;
import com.web.backend.model.redis.RefreshTokenData;

public interface JwtService {

    String generateAccessToken(String username, List<String> authorities, Integer tokenVersion);

    String generateRefreshToken(String username, Integer tokenVersion);

    public void revokeRefreshToken(String token);

    public RefreshTokenData validateRefreshToken(String token);

    String extractUsername(String token);

    <T> T extractClaim(String token, Function<Claims, T> claimsResolver);

    public long getRemainingTime(String token);

    TokenResponse getGraceTokenResponse(String token);

    void recordGracePeriod(String oldToken, TokenResponse response, long durationSeconds);
}
