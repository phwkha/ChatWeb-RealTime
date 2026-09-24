package com.web.backend.oauth2;

import java.io.IOException;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import com.web.backend.config.localresolverconfig.Translator;

@Slf4j
@Component
public class OAuth2AuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    @Value("${app.oauth2.redirect-uri}")
    private String redirectUri;

    private static final String ERROR_STRING = "error";
    private static final String ERROR_DESCRIPTION_STRING = "error_description";

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException, ServletException {

        String errorCode = "error.auth.failed";
        if (exception instanceof OAuth2AuthenticationException oAuth2Ex && oAuth2Ex.getError() != null) {
            errorCode = oAuth2Ex.getError().getErrorCode();
        } else if (exception != null && exception.getMessage() != null && !exception.getMessage().isBlank()) {
            errorCode = exception.getMessage();
        }

        String message;
        try {
            message = Translator.tolocale(errorCode);
        } catch (Exception e) {
            log.warn("Failed to translate error code '{}': {}", errorCode, e.getMessage());
            message = errorCode;
        }

        String targetUrl = UriComponentsBuilder.fromUriString(Objects.requireNonNull(redirectUri))
                .queryParam(ERROR_STRING, message)
                .queryParam(ERROR_DESCRIPTION_STRING, message)
                .build().toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}
