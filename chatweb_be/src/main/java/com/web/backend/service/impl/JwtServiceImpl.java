package com.web.backend.service.impl;

import com.web.backend.common.TokenType;
import com.web.backend.config.localresolverconfig.Translator;
import com.web.backend.exception.custom.InvalidDataException;
import com.web.backend.service.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.*;
import java.util.function.Function;
import java.time.Instant;

@Service
@Slf4j(topic = "JWT-SERVICE")
public class JwtServiceImpl implements JwtService {

    @Value("${jwt.expiry-minutes}")
    private Long expiryMinutes;

    @Value("${jwt.expiry-day}")
    private Long expiryDay;

    @Value("${jwt.secret-key-access}")
    private String secretKeyAccess;

    @Value("${jwt.secret-key-refresh}")
    private String secretKeyRefresh;

    private static final String ROLE_STRING = "role";

    private static final String ERROR_JWT_INVALID_TYPE_STRING = "error.jwt.invalid_type";

    @Override
    public String generateAccessToken(String username, List<String> authorities, Integer tokenVersion) {
        log.debug("Generating access token for user '{}' [authorities={}]", username, authorities);

        Map<String, Object> claims = new HashMap<>();
        claims.put(ROLE_STRING, authorities);
        claims.put("v", tokenVersion);

        return generateToken(claims, username);
    }

    @Override
    public String generateRefreshToken(String username, List<String> authorities, Integer tokenVersion) {
        log.debug("Generating refresh token for user '{}'", username);

        Map<String, Object> claims = new HashMap<>();
        claims.put(ROLE_STRING, authorities);
        claims.put("v", tokenVersion);

        return generateRefreshToken(claims, username);
    }

    @Override
    public String extractUsername(String token, TokenType type) {
        return extractClaims(type, token, Claims::getSubject);
    }

    @Override
    public <T> T extractClaim(String token, TokenType type, Function<Claims, T> claimsResolver) {
        final Claims claims = extraAllClaim(token, type);
        return claimsResolver.apply(claims);
    }

    @Override
    public long getRemainingTime(String token, TokenType tokenType) {
        Date expiration = extractClaim(token, tokenType, Claims::getExpiration);
        long now = Instant.now().toEpochMilli();
        long remaining = expiration.getTime() - now;
        return Math.max(remaining, 0);
    }

    private <T> T extractClaims(TokenType type, String token, Function<Claims, T> claimsExtractor) {
        final Claims claims = extraAllClaim(token, type);
        return claimsExtractor.apply(claims);
    }

    private Claims extraAllClaim(String token, TokenType type) {
        return Jwts.parser()
                .verifyWith(getKey(type))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private String generateToken(Map<String, Object> claims, String username) {
        Instant now = Instant.now();
        return Jwts.builder()
                .claims(claims)
                .subject(username)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(1000L * 60 * expiryMinutes)))
                .signWith(getKey(TokenType.ACCESS_TOKEN), Jwts.SIG.HS256)
                .compact();
    }

    private String generateRefreshToken(Map<String, Object> claims, String username) {
        Instant now = Instant.now();
        return Jwts.builder()
                .claims(claims)
                .subject(username)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(1000L * 60 * 60 * 24 * expiryDay)))
                .signWith(getKey(TokenType.REFRESH_TOKEN), Jwts.SIG.HS256)
                .compact();
    }

    private SecretKey getKey(TokenType type) {
        switch (type) {
            case ACCESS_TOKEN -> {
                return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretKeyAccess));
            }
            case REFRESH_TOKEN -> {
                return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretKeyRefresh));
            }
            default -> throw new InvalidDataException(Translator.tolocale(ERROR_JWT_INVALID_TYPE_STRING));
        }
    }
}