package com.web.backend.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.web.backend.config.localresolverconfig.Translator;
import com.web.backend.controller.response.ApiResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final String ERROR_STRING = "error";
    private static final String ERROR_AUTH_SESSION_EXPIRED_STRING = "error.auth.session_expired";

    private static final String APPLICATION_JSON_STRING = "application/json";

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(APPLICATION_JSON_STRING);
        ApiResponse<?> apiResponse = ApiResponse.builder()
                .code(HttpStatus.UNAUTHORIZED.value())
                .status(ERROR_STRING)
                .message(Translator.tolocale(ERROR_AUTH_SESSION_EXPIRED_STRING))
                .build();

        ObjectMapper objectMapper = new ObjectMapper();
        response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
        response.flushBuffer();
    }
}
