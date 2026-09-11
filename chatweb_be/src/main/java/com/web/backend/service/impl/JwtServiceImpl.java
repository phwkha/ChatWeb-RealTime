package com.web.backend.service.impl;

import com.web.backend.config.localresolverconfig.Translator;
import com.web.backend.exception.custom.AccessForbiddenException;
import com.web.backend.model.redis.RefreshTokenData;
import com.web.backend.service.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.concurrent.atomic.AtomicReference;
import java.time.Instant;

@Service
@Slf4j(topic = "JWT-SERVICE")
@RequiredArgsConstructor
public class JwtServiceImpl implements JwtService {

    private final AtomicReference<SecretKey> key = new AtomicReference<>();

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${jwt.expiry-minutes}")
    private Long expiryMinutes;

    @Value("${jwt.expiry-day}")
    private Long expiryDay;

    @Value("${jwt.secret-key-access}")
    private String secretKeyAccess;

    private static final String ROLE_STRING = "role";
    private static final String TOKEN_VERSION_CLAIM_STRING = "v";
    private static final String RT_PREFIX = "rt:";
    private static final String ERROR_AUTH_REFRESH_EXPIRED_STRING = "error.auth.refresh_expired";

    @Override
    public String generateAccessToken(String username, List<String> authorities, Integer tokenVersion) {
        log.debug("Generating access token for user '{}' [authorities={}]", username, authorities);

        Map<String, Object> claims = new HashMap<>();
        claims.put(ROLE_STRING, authorities);
        claims.put(TOKEN_VERSION_CLAIM_STRING, tokenVersion != null ? tokenVersion : 0);

        return generateToken(claims, username);
    }

    @Override
    public String generateRefreshToken(String username, Integer tokenVersion) {
        String token = UUID.randomUUID().toString();

        RefreshTokenData data = RefreshTokenData.builder()
                .username(username)
                .tokenVersion(tokenVersion != null ? tokenVersion : 0)
                .createdAt(Instant.now())
                .build();

        redisTemplate.opsForValue().set(RT_PREFIX + token, data, expiryDay, TimeUnit.DAYS);

        return token;
    }

    @Override
    public RefreshTokenData validateRefreshToken(String token) {
        if (token == null || token.isBlank()) {
            throw new AccessForbiddenException(Translator.tolocale(ERROR_AUTH_REFRESH_EXPIRED_STRING));
        }

        RefreshTokenData data = (RefreshTokenData) redisTemplate.opsForValue().get(RT_PREFIX + token);

        if (data == null) {
            throw new AccessForbiddenException(Translator.tolocale(ERROR_AUTH_REFRESH_EXPIRED_STRING));
        }

        return data;
    }

    @Override
    public void revokeRefreshToken(String token) {
        if (token != null && !token.isBlank()) {
            redisTemplate.delete(RT_PREFIX + token);
        }
    }

    @Override
    public String extractUsername(String token) {
        return extractClaims(token, Claims::getSubject);
    }

    @Override
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extraAllClaim(token);
        return claimsResolver.apply(claims);
    }

    @PostConstruct
    public void init() {
        if (secretKeyAccess != null && !secretKeyAccess.isBlank()) {
            this.key.set(Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretKeyAccess)));
        }
    }

    @Override
    public long getRemainingTime(String token) {
        try {
            Date expiration = extractClaim(token, Claims::getExpiration);
            long now = Instant.now().toEpochMilli();
            long remaining = expiration.getTime() - now;
            return Math.max(remaining, 0);
        } catch (ExpiredJwtException e) {
            return 0;
        }
    }

    private <T> T extractClaims(String token, Function<Claims, T> claimsExtractor) {
        final Claims claims = extraAllClaim(token);
        return claimsExtractor.apply(claims);
    }

    private SecretKey getKey() {
        SecretKey currentKey = this.key.get();
        if (currentKey == null) {
            currentKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretKeyAccess));
            this.key.compareAndSet(null, currentKey);
            return this.key.get();
        }
        return currentKey;
    }

    private Claims extraAllClaim(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
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
                .signWith(getKey(), Jwts.SIG.HS256)
                .compact();
    }

}