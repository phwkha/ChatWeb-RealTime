package com.web.backend.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.web.backend.config.localresolverconfig.Translator;
import com.web.backend.controller.response.ApiResponse;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private static final String ERROR_STRING = "error";
    private static final String ERROR_AUTH_FORBIDDEN_STRING = "error.auth.forbidden";

    private static final String APPLICATION_JSON_STRING = "application/json";

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException, ServletException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(APPLICATION_JSON_STRING);

        ApiResponse<?> apiResponse = ApiResponse.builder()
                .code(HttpStatus.FORBIDDEN.value())
                .status(ERROR_STRING)
                .message(Translator.tolocale(ERROR_AUTH_FORBIDDEN_STRING))
                .build();

        ObjectMapper objectMapper = new ObjectMapper();
        response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
        response.flushBuffer();
    }
}