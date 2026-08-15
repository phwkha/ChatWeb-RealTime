package com.web.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.web.backend.config.localresolverconfig.Translator;
import com.web.backend.controller.request.*;
import com.web.backend.controller.response.*;
import com.web.backend.model.UserEntity;
import com.web.backend.service.AuthenticationService;
import com.web.backend.jwt.JwtAuthenticationFilter;
import com.web.backend.service.JwtService;
import com.web.backend.service.RateLimitingService;
import com.web.backend.service.UserServiceDetail;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import jakarta.servlet.http.Cookie;

import java.util.Collections;

import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import com.web.backend.ratelimit.RateLimitAspect;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.springframework.messaging.simp.SimpMessagingTemplate;

@WebMvcTest(controllers = AuthController.class, excludeAutoConfiguration = {
                SecurityAutoConfiguration.class,
                SecurityFilterAutoConfiguration.class,
                OAuth2ClientAutoConfiguration.class,
                OAuth2ClientWebSecurityAutoConfiguration.class,
                OAuth2ResourceServerAutoConfiguration.class
}, excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)
})
@EnableAspectJAutoProxy
@Import({RateLimitAspect.class, AopAutoConfiguration.class})
class AuthControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @MockBean
        private AuthenticationService authenticationService;

        @MockBean
        private RateLimitingService rateLimitingService;

        @MockBean
        private JwtService jwtService;

        @MockBean
        private UserServiceDetail userServiceDetail;

        @MockBean
        private RedisTemplate<String, Object> redisTemplate;

        @MockBean
        private SimpMessagingTemplate simpMessagingTemplate;

        private UsernamePasswordAuthenticationToken mockAuth;
        private UserEntity mockUser;

        @BeforeEach
        void setUp() {
                ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
                messageSource.setBasename("i18n/messages");
                messageSource.setDefaultEncoding("UTF-8");
                Translator.setStaticMessageSource(messageSource);

                mockUser = new UserEntity();
                mockUser.setUsername("testuser");
                mockUser.setEmail("test@gmail.com");

                mockAuth = new UsernamePasswordAuthenticationToken(mockUser, null, Collections.emptyList());

                when(rateLimitingService.isAllowed(any(), anyInt(), anyLong())).thenReturn(true);
        }

        @Test
        void testLogin_Success() throws Exception {
                LoginRequest request = new LoginRequest();
                request.setUsername("testuser");
                request.setPassword("password123");

                UserResponse userResponse = new UserResponse();
                userResponse.setUsername("testuser");

                LoginResponse loginResponse = LoginResponse.builder()
                                .accessToken("mockAccessToken")
                                .refreshToken("mockRefreshToken")
                                .userResponse(userResponse)
                                .build();

                when(rateLimitingService.isAllowed(any(), eq(5), eq(60L))).thenReturn(true);
                when(authenticationService.login(any(LoginRequest.class))).thenReturn(loginResponse);

                mockMvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200))
                                .andExpect(cookie().exists("accessToken"))
                                .andExpect(cookie().exists("refreshToken"));
        }

        @Test
        void testLogin_RateLimitExceeded() throws Exception {
                LoginRequest request = new LoginRequest();
                request.setUsername("testuser");
                request.setPassword("password123");

                when(rateLimitingService.isAllowed(any(), eq(5), eq(60L))).thenReturn(false);

                mockMvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isTooManyRequests())
                                .andExpect(jsonPath("$.code").value(429));
        }

        @Test
        void testRegisterUser_Success() throws Exception {
                CreateUserRequest request = new CreateUserRequest();
                request.setUsername("newuser");
                request.setEmail("newuser@gmail.com");
                request.setPassword("password123");

                UserResponse userResponse = new UserResponse();
                userResponse.setUsername("newuser");

                when(authenticationService.createUser(any(CreateUserRequest.class))).thenReturn(userResponse);

                mockMvc.perform(post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.code").value(201))
                                .andExpect(jsonPath("$.data.username").value("newuser"));
        }

        @Test
        void testVerifyOtp_Success() throws Exception {
                VerifyOtpRequest request = new VerifyOtpRequest();
                request.setEmail("test@gmail.com");
                request.setOtp("123456");

                mockMvc.perform(post("/api/auth/verify-account")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200));

                verify(authenticationService).verifyUser(any(VerifyOtpRequest.class));
        }

        @Test
        void testResendOtp_Success() throws Exception {
                mockMvc.perform(post("/api/auth/resend-otp")
                                .param("email", "test@gmail.com"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200));

                verify(authenticationService).resendOtp("test@gmail.com");
        }

        @Test
        void testRefreshToken_Success() throws Exception {
                TokenResponse tokenResponse = TokenResponse.builder()
                                .accessToken("newAccessToken")
                                .refreshToken("newRefreshToken")
                                .build();

                when(authenticationService.refreshToken("validRefreshToken")).thenReturn(tokenResponse);

                Cookie cookie = new Cookie("refreshToken", "validRefreshToken");

                mockMvc.perform(post("/api/auth/refresh-token")
                                .cookie(cookie))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200))
                                .andExpect(jsonPath("$.data").value("newAccessToken"))
                                .andExpect(cookie().exists("accessToken"))
                                .andExpect(cookie().exists("refreshToken"));
        }

        @Test
        void testForgotPassword_Success() throws Exception {
                ForgotPasswordRequest request = new ForgotPasswordRequest();
                request.setEmail("test@gmail.com");

                mockMvc.perform(post("/api/auth/forgot-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200));

                verify(authenticationService).initiateForgotPassword("test@gmail.com");
        }

        @Test
        void testResetPassword_Success() throws Exception {
                ResetPasswordRequest request = new ResetPasswordRequest();
                request.setEmail("test@gmail.com");
                request.setOtp("123456");
                request.setNewPassword("newPass123!");

                mockMvc.perform(post("/api/auth/reset-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200));

                verify(authenticationService).verifyPasswordReset("test@gmail.com", "123456", "newPass123!");
        }

        @Test
        void testResendForgotPassword_Success() throws Exception {
                mockMvc.perform(post("/api/auth/resend-forgot-password")
                                .param("email", "test@gmail.com"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200));

                verify(authenticationService).resendForgotPasswordOtp("test@gmail.com");
        }

        @Test
        void testLogout_Success() throws Exception {
                Cookie accessTokenCookie = new Cookie("accessToken", "mockAccessToken");
                Cookie refreshTokenCookie = new Cookie("refreshToken", "mockRefreshToken");

                mockMvc.perform(post("/api/auth/logout")
                                .principal(mockAuth)
                                .cookie(accessTokenCookie, refreshTokenCookie))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200))
                                .andExpect(cookie().value("accessToken", ""))
                                .andExpect(cookie().value("refreshToken", ""));
        }

        @Test
        void testLogoutAllDevices_Success() throws Exception {
                Cookie accessTokenCookie = new Cookie("accessToken", "mockAccessToken");
                Cookie refreshTokenCookie = new Cookie("refreshToken", "mockRefreshToken");

                mockMvc.perform(post("/api/auth/logout-all-devices")
                                .principal(mockAuth)
                                .cookie(accessTokenCookie, refreshTokenCookie))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200))
                                .andExpect(cookie().value("accessToken", ""))
                                .andExpect(cookie().value("refreshToken", ""));

                verify(authenticationService).logoutAllDevices("testuser");
        }
}
