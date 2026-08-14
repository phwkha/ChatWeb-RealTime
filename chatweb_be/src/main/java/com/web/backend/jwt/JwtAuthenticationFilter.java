package com.web.backend.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.web.backend.common.TokenType;
import com.web.backend.config.localresolverconfig.Translator;
import com.web.backend.controller.response.ApiResponse;
import com.web.backend.model.UserEntity;
import com.web.backend.service.JwtService;
import com.web.backend.service.UserServiceDetail;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.servlet.HandlerExceptionResolver;
import java.io.IOException;

@Component
@Slf4j(topic = "JWT-AUTHENTICATION-FLITER")
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    private final UserServiceDetail userServiceDetail;

    private final RedisTemplate<String, Object> redisTemplate;

    private final HandlerExceptionResolver exceptionResolver;

    private static final String AUTHORIZATION_STRING = "Authorization";
    private static final String BEARER_STRING = "Bearer ";
    private static final String UTF_8_STRING = "UTF-8";

    private static final String APPLICATION_JSON_STRING = "application/json";

    private static final String ACCESSTOKEN_STRING = "accessToken";

    private static final String ERROR_WS_BLACKLISTED_STRING = "error.ws.blacklisted";

    private static final String BLACK_LIST_PREFIX_STRING = "blacklist:";

    private static final String LOGOUT_URL_STRING = "/api/auth/logout";
    private static final String LOGOUT_ALL_DEVICES_URL_STRING = "/api/auth/logout-all-devices";
    private static final String AUTH_PREFIX_STRING = "/api/auth/";

    public JwtAuthenticationFilter(
            JwtService jwtService,
            UserServiceDetail userServiceDetail,
            RedisTemplate<String, Object> redisTemplate,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
        this.jwtService = jwtService;
        this.userServiceDetail = userServiceDetail;
        this.redisTemplate = redisTemplate;
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getServletPath();
        if (path.equals(LOGOUT_URL_STRING) || path.equals(LOGOUT_ALL_DEVICES_URL_STRING)) {
            return false;
        }
        return path.startsWith(AUTH_PREFIX_STRING);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        try {
            String jwt = getTokenFromRequest(request);

            if (jwt != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                if (isTokenBlacklisted(jwt, response)) {
                    return;
                }
                if (!authenticateUser(jwt, request, response, filterChain)) {
                    return;
                }
            }
        } catch (Exception e) {
            log.warn("JWT authentication filter error: {}", e.getMessage());
            exceptionResolver.resolveException(request, response, null, e);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isTokenBlacklisted(String jwt, HttpServletResponse response) throws IOException {
        String key = BLACK_LIST_PREFIX_STRING + jwt;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(APPLICATION_JSON_STRING);
            response.setCharacterEncoding(UTF_8_STRING);

            ApiResponse<?> apiResponse = ApiResponse.error(
                    HttpStatus.UNAUTHORIZED.value(),
                    Translator.tolocale(ERROR_WS_BLACKLISTED_STRING));

            ObjectMapper objectMapper = new ObjectMapper();
            response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
            response.flushBuffer();
            return true;
        }
        return false;
    }

    private boolean authenticateUser(String jwt, HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String username = jwtService.extractUsername(jwt, TokenType.ACCESS_TOKEN);
        if (username == null) {
            return true;
        }

        UserDetails userDetails = userServiceDetail.loadUserByUsername(username);

        if (!isTokenVersionValid(jwt, userDetails, username)) {
            filterChain.doFilter(request, response);
            return false;
        }

        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        securityContext.setAuthentication(authToken);
        SecurityContextHolder.setContext(securityContext);

        return true;
    }

    private boolean isTokenVersionValid(String jwt, UserDetails userDetails, String username) {
        if (!(userDetails instanceof UserEntity userEntity)) {
            return true;
        }

        Integer tokenVersionInJwt = jwtService.extractClaim(jwt, TokenType.ACCESS_TOKEN,
                claims -> claims.get("v", Integer.class));

        Integer currentVersion = userEntity.getTokenVersion();
        if (currentVersion == null) {
            currentVersion = 0;
        }

        if (tokenVersionInJwt == null || !tokenVersionInJwt.equals(currentVersion)) {
            log.warn("Token version mismatch for user '{}' [jwtVersion={}, currentVersion={}]", username,
                    tokenVersionInJwt, currentVersion);
            return false;
        }
        return true;
    }

    /**
     * Hàm hỗ trợ lấy Token từ Cookie (ưu tiên) hoặc Header Authorization
     */
    private String getTokenFromRequest(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (ACCESSTOKEN_STRING.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }

        String authHeader = request.getHeader(AUTHORIZATION_STRING);
        if (authHeader != null && authHeader.startsWith(BEARER_STRING)) {
            return authHeader.substring(7);
        }

        return null;
    }
}