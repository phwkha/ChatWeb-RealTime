package com.web.backend.oauth2;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.web.backend.model.UserEntity;
import com.web.backend.service.JwtService;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

        private final JwtService jwtService;

        @Value("${app.oauth2.redirect-uri}")
        private String redirectUri;

        private static final String API_AUTH_STRING = "/api/auth";
        private static final String PATH_STRING = "/";

        private static final String STRICT_STRING = "Strict";

        private static final String REFRESHTOKEN_STRING = "refreshToken";
        private static final String ACCESSTOKEN_STRING = "accessToken";

        @Override
        public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                        Authentication authentication) throws IOException, ServletException {
                CustomOAuth2User oauthUser = (CustomOAuth2User) authentication.getPrincipal();
                UserEntity user = oauthUser.getUserEntity();

                List<String> authorities = user.getAuthorities().stream()
                                .map(auth -> auth.getAuthority()).toList();

                String accessToken = jwtService.generateAccessToken(user.getUsername(), authorities,
                                user.getTokenVersion());
                String refreshToken = jwtService.generateRefreshToken(user.getUsername(), authorities,
                                user.getTokenVersion());

                ResponseCookie accessCookie = ResponseCookie.from(ACCESSTOKEN_STRING, accessToken)
                                .httpOnly(true)
                                .secure(true)
                                .path(PATH_STRING)
                                .maxAge(15 * 60l)
                                .sameSite(STRICT_STRING)
                                .build();

                ResponseCookie refreshCookie = ResponseCookie.from(REFRESHTOKEN_STRING, refreshToken)
                                .httpOnly(true)
                                .secure(true)
                                .path(API_AUTH_STRING)
                                .maxAge(7 * 24 * 60 * 60l)
                                .sameSite(STRICT_STRING)
                                .build();

                response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
                response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
                getRedirectStrategy().sendRedirect(request, response, redirectUri);
        }
}
