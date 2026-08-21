package com.web.backend.controller;

import com.web.backend.controller.request.*;
import com.web.backend.controller.response.ApiResponse;
import com.web.backend.controller.response.LoginResponse;
import com.web.backend.controller.response.TokenResponse;
import com.web.backend.controller.response.UserResponse;
import com.web.backend.model.postgres.UserEntity;
import com.web.backend.ratelimit.LimitType;
import com.web.backend.ratelimit.RateLimit;
import com.web.backend.service.AuthenticationService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.*;

import com.web.backend.common.TokenType;
import com.web.backend.config.localresolverconfig.Translator;
import jakarta.servlet.http.Cookie;

@Tag(name = "Auth Controller")
@RestController
@RequestMapping("/api/auth/")
@RequiredArgsConstructor
@Slf4j(topic = "AUTH-CONTROLLER")
public class AuthController {

        private final AuthenticationService authenticationService;

        private static final String PATH_STRING = "/";
        private static final String EMPTY_STRING = "";

        private static final String API_AUTH_REFRESH_TOKEN_STRING = "/api/auth/refresh-token";

        private static final String AUTHORIZATION_STRING = "Authorization";
        private static final String BEARER_STRING = "Bearer ";

        private static final String NULL_STRING = "null";
        private static final String UNDEFINED_STRING = "undefined";

        private static final String STRICT_STRING = "Strict";
        private static final String AUTH_PATH = "/api/auth";

        private static final String ACCESSTOKEN = "accessToken";
        private static final String REFRESHTOKEN = "refreshToken";

        private static final String SUCCESS_AUTH_ACTIVATED_STRING = "success.auth.activated";
        private static final String SUCCESS_AUTH_CODE_RESENT_STRING = "success.auth.code_resent";
        private static final String SUCCESS_AUTH_CODE_SENT_STRING = "success.auth.code_sent";
        private static final String SUCCESS_AUTH_LOGIN_STRING = "success.auth.login";
        private static final String SUCCESS_AUTH_LOGOUT_STRING = "success.auth.logout";
        private static final String SUCCESS_AUTH_OTP_RESENT_STRING = "success.auth.otp_resent";
        private static final String SUCCESS_AUTH_PWD_RESET_STRING = "success.auth.pwd_reset";
        private static final String SUCCESS_AUTH_REGISTERED_STRING = "success.auth.registered";
        private static final String SUCCESS_AUTH_TOKEN_REFRESHED_STRING = "success.auth.token_refreshed";

        @Operation(summary = "Login", description = "API endpoint for login")
        @RateLimit(key = "login", limit = 5, period = 60, type = LimitType.IP)
        @PostMapping("/login")
        public ResponseEntity<ApiResponse<UserResponse>> login(@RequestBody @Valid LoginRequest loginRequest) {

                log.debug("Login request received for user '{}'", loginRequest.getUsername());

                LoginResponse loginResponse = authenticationService.login(loginRequest);

                ResponseCookie accessCookie = buildCookie(ACCESSTOKEN, loginResponse.getAccessToken(), PATH_STRING,
                                15 * 60L);
                ResponseCookie refreshCookie = buildCookie(REFRESHTOKEN, loginResponse.getRefreshToken(),
                                API_AUTH_REFRESH_TOKEN_STRING, 7 * 24 * 60 * 60L);

                return ResponseEntity.ok()
                                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                                .body(ApiResponse.success(HttpStatus.OK.value(),
                                                Translator.tolocale(SUCCESS_AUTH_LOGIN_STRING),
                                                loginResponse.getUserResponse()));
        }

        @Operation(summary = "Register user", description = "API endpoint for register user")
        @RateLimit(key = "register", limit = 3, period = 60, type = LimitType.IP)
        @PostMapping("/register")
        public ResponseEntity<ApiResponse<UserResponse>> registerUser(
                        @RequestBody @Valid CreateUserRequest createUserRequest) {
                log.debug("Registration request received for new user '{}'", createUserRequest.getUsername());

                UserResponse newUser = authenticationService.createUser(createUserRequest);

                return ResponseEntity.status(HttpStatus.CREATED)
                                .body(ApiResponse.success(HttpStatus.CREATED.value(),
                                                Translator.tolocale(SUCCESS_AUTH_REGISTERED_STRING), newUser));
        }

        @Operation(summary = "Verify otp", description = "API endpoint for verify otp")
        @RateLimit(key = "verify_account", limit = 5, period = 60, type = LimitType.IP)
        @PostMapping("/verify-account")
        public ResponseEntity<ApiResponse<Void>> verifyOtp(@RequestBody @Valid VerifyOtpRequest request) {
                log.debug("Verifying account for email '{}'", request.getEmail());
                authenticationService.verifyUser(request);
                return ResponseEntity
                                .ok(ApiResponse.success(HttpStatus.OK.value(),
                                                Translator.tolocale(SUCCESS_AUTH_ACTIVATED_STRING), null));
        }

        @Operation(summary = "Resend otp", description = "API endpoint for resend otp")
        @RateLimit(key = "resend_otp", limit = 3, period = 60, type = LimitType.IP)
        @PostMapping("/resend-otp")
        public ResponseEntity<ApiResponse<Void>> resendOtp(@RequestParam String email) {
                log.debug("Resending verification OTP for email '{}'", email);
                authenticationService.resendOtp(email);
                return ResponseEntity
                                .ok(ApiResponse.success(HttpStatus.OK.value(),
                                                Translator.tolocale(SUCCESS_AUTH_OTP_RESENT_STRING), null));
        }

        @Operation(summary = "Refresh token", description = "API endpoint for refresh token")
        @PostMapping("/refresh-token")
        public ResponseEntity<ApiResponse<String>> refreshToken(
                        @CookieValue(name = REFRESHTOKEN, required = false) String refreshToken) {

                log.debug("Refreshing authentication token");
                TokenResponse newTokenResponse = authenticationService.refreshToken(refreshToken);

                ResponseCookie newAccessCookie = buildCookie(ACCESSTOKEN, newTokenResponse.getAccessToken(),
                                PATH_STRING,
                                15 * 60L);
                ResponseCookie newrefreshCookie = buildCookie(REFRESHTOKEN, newTokenResponse.getRefreshToken(),
                                AUTH_PATH, 7 * 24 * 60 * 60L);

                return ResponseEntity.ok()
                                .header(HttpHeaders.SET_COOKIE, newAccessCookie.toString())
                                .header(HttpHeaders.SET_COOKIE, newrefreshCookie.toString())
                                .body(ApiResponse.success(HttpStatus.OK.value(),
                                                Translator.tolocale(SUCCESS_AUTH_TOKEN_REFRESHED_STRING),
                                                newTokenResponse.getAccessToken()));
        }

        @Operation(summary = "Forgot password", description = "API endpoint for forgot password")
        @RateLimit(key = "forgot_pwd", limit = 3, period = 60, type = LimitType.IP)
        @PostMapping("/forgot-password")
        public ResponseEntity<ApiResponse<Void>> forgotPassword(@RequestBody @Valid ForgotPasswordRequest request) {
                log.debug("Password reset request received for email '{}'", request.getEmail());
                authenticationService.initiateForgotPassword(request.getEmail());
                return ResponseEntity
                                .ok(ApiResponse.success(HttpStatus.OK.value(),
                                                Translator.tolocale(SUCCESS_AUTH_CODE_SENT_STRING), null));
        }

        @Operation(summary = "Reset password", description = "API endpoint for reset password")
        @RateLimit(key = "reset_pwd", limit = 5, period = 60, type = LimitType.IP)
        @PostMapping("/reset-password")
        public ResponseEntity<ApiResponse<Void>> resetPassword(@RequestBody @Valid ResetPasswordRequest request) {
                log.debug("Password reset verification request received for email '{}'", request.getEmail());
                authenticationService.verifyPasswordReset(request.getEmail(), request.getOtp(),
                                request.getNewPassword());
                return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(),
                                Translator.tolocale(SUCCESS_AUTH_PWD_RESET_STRING), null));
        }

        @Operation(summary = "Resend forgot password", description = "API endpoint for resend forgot password")
        @RateLimit(key = "resend_forgot_pwd", limit = 3, period = 60, type = LimitType.IP)
        @PostMapping("/resend-forgot-password")
        public ResponseEntity<ApiResponse<Void>> resendForgotPassword(@RequestParam String email) {
                authenticationService.resendForgotPasswordOtp(email);
                return ResponseEntity
                                .ok(ApiResponse.success(HttpStatus.OK.value(),
                                                Translator.tolocale(SUCCESS_AUTH_CODE_RESENT_STRING), null));
        }

        @Operation(summary = "Logout", description = "API endpoint for logout")
        @PostMapping("/logout")
        public ResponseEntity<ApiResponse<String>> logout(Authentication authentication, HttpServletRequest request) {
                UserEntity userEntityPrincipal = (UserEntity) authentication.getPrincipal();
                log.debug("User '{}' logging out", userEntityPrincipal.getUsername());
                clearTokens(request);

                ResponseCookie deleteAccess = buildCookie(ACCESSTOKEN, EMPTY_STRING, PATH_STRING, 0);
                ResponseCookie deleteRefresh = buildCookie(REFRESHTOKEN, EMPTY_STRING, AUTH_PATH, 0);

                return ResponseEntity.ok()
                                .header(HttpHeaders.SET_COOKIE, deleteAccess.toString())
                                .header(HttpHeaders.SET_COOKIE, deleteRefresh.toString())
                                .body(ApiResponse.success(200, Translator.tolocale(SUCCESS_AUTH_LOGOUT_STRING), null));
        }

        @Operation(summary = "Logout all devices", description = "API endpoint to invalidate all refresh tokens globally")
        @PostMapping("/logout-all-devices")
        public ResponseEntity<ApiResponse<String>> logoutAllDevices(Authentication authentication,
                        HttpServletRequest request) {
                UserEntity userEntityPrincipal = (UserEntity) authentication.getPrincipal();
                log.debug("User '{}' logging out from all devices", userEntityPrincipal.getUsername());

                authenticationService.logoutAllDevices(userEntityPrincipal.getUsername());

                clearTokens(request);

                ResponseCookie deleteAccess = buildCookie(ACCESSTOKEN, EMPTY_STRING, PATH_STRING, 0);
                ResponseCookie deleteRefresh = buildCookie(REFRESHTOKEN, EMPTY_STRING, AUTH_PATH, 0);

                return ResponseEntity.ok()
                                .header(HttpHeaders.SET_COOKIE, deleteAccess.toString())
                                .header(HttpHeaders.SET_COOKIE, deleteRefresh.toString())
                                .body(ApiResponse.success(200, Translator.tolocale(SUCCESS_AUTH_LOGOUT_STRING), null));
        }

        private void clearTokens(HttpServletRequest request) {
                String accesstoken = getCookieValue(request, ACCESSTOKEN);
                String refreshtoken = getCookieValue(request, REFRESHTOKEN);

                if (accesstoken == null || accesstoken.isEmpty()) {
                        accesstoken = getBearerToken(request);
                }

                logoutIfValid(accesstoken, TokenType.ACCESS_TOKEN);
                logoutIfValid(refreshtoken, TokenType.REFRESH_TOKEN);
        }

        private String getCookieValue(HttpServletRequest request, String cookieName) {
                if (request.getCookies() == null) {
                        return null;
                }
                for (Cookie cookie : request.getCookies()) {
                        if (cookieName.equals(cookie.getName())) {
                                return cookie.getValue();
                        }
                }
                return null;
        }

        private String getBearerToken(HttpServletRequest request) {
                String authHeader = request.getHeader(AUTHORIZATION_STRING);
                if (authHeader != null && authHeader.startsWith(BEARER_STRING)) {
                        return authHeader.substring(7);
                }
                return null;
        }

        private void logoutIfValid(String token, TokenType tokenType) {
                if (token != null && !token.trim().isEmpty() && !token.equals(NULL_STRING)
                                && !token.equals(UNDEFINED_STRING)) {
                        authenticationService.logout(token, tokenType);
                }
        }

        private ResponseCookie buildCookie(String name, String value, String path, long maxAge) {
                return ResponseCookie.from(Objects.requireNonNull(name), value)
                                .httpOnly(true)
                                .secure(true)
                                .path(path)
                                .maxAge(maxAge)
                                .sameSite(STRICT_STRING)
                                .build();
        }
}
