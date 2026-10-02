package com.web.backend.jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.web.backend.config.localresolverconfig.Translator;
import com.web.backend.model.postgres.UserEntity;
import com.web.backend.service.JwtService;
import com.web.backend.service.UserServiceDetail;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private UserServiceDetail userServiceDetail;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private HandlerExceptionResolver exceptionResolver;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        ResourceBundleMessageSource messageSource = mock(ResourceBundleMessageSource.class);
        lenient().when(messageSource.getMessage(anyString(), any(), any())).thenReturn("Mocked Blacklist Error");
        Translator.setStaticMessageSource(messageSource);

        filter = new JwtAuthenticationFilter(jwtService, userServiceDetail, redisTemplate, exceptionResolver);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void testShouldNotFilter_AuthEndpoints() {
        MockHttpServletRequest loginRequest = new MockHttpServletRequest("POST", "/api/auth/login");
        loginRequest.setServletPath("/api/auth/login");
        assertTrue(filter.shouldNotFilter(loginRequest));

        MockHttpServletRequest logoutRequest = new MockHttpServletRequest("POST", "/api/auth/logout");
        logoutRequest.setServletPath("/api/auth/logout");
        assertFalse(filter.shouldNotFilter(logoutRequest));

        MockHttpServletRequest userRequest = new MockHttpServletRequest("GET", "/api/users/profile");
        userRequest.setServletPath("/api/users/profile");
        assertFalse(filter.shouldNotFilter(userRequest));
    }

    @Test
    void testDoFilter_NoAuthHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = mock(MockFilterChain.class);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void testDoFilter_TokenBlacklisted() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/me");
        request.addHeader("Authorization", "Bearer blacklisted-jwt-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = mock(MockFilterChain.class);

        when(redisTemplate.hasKey("blacklist:blacklisted-jwt-token")).thenReturn(true);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, never()).doFilter(request, response);
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("Mocked Blacklist Error"));
    }

    @Test
    void testDoFilter_ValidToken_AuthenticatesSuccessfully() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/me");
        request.addHeader("Authorization", "Bearer valid-jwt-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = mock(MockFilterChain.class);

        when(redisTemplate.hasKey("blacklist:valid-jwt-token")).thenReturn(false);
        when(jwtService.extractUsername("valid-jwt-token")).thenReturn("alice");

        UserEntity user = new UserEntity();
        user.setUsername("alice");
        user.setTokenVersion(1);
        when(userServiceDetail.loadUserByUsername("alice")).thenReturn(user);
        when(jwtService.extractClaim(eq("valid-jwt-token"), any())).thenReturn(1);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("alice", SecurityContextHolder.getContext().getAuthentication().getName());
    }

    @Test
    void testDoFilter_TokenVersionMismatch_RejectsAuthentication() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/me");
        request.addHeader("Authorization", "Bearer valid-jwt-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = mock(MockFilterChain.class);

        when(redisTemplate.hasKey("blacklist:valid-jwt-token")).thenReturn(false);
        when(jwtService.extractUsername("valid-jwt-token")).thenReturn("alice");

        UserEntity user = new UserEntity();
        user.setUsername("alice");
        user.setTokenVersion(2);
        when(userServiceDetail.loadUserByUsername("alice")).thenReturn(user);
        when(jwtService.extractClaim(eq("valid-jwt-token"), any())).thenReturn(1);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void testDoFilter_Exception_CallsResolver() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/me");
        request.addHeader("Authorization", "Bearer error-jwt-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = mock(MockFilterChain.class);

        when(redisTemplate.hasKey("blacklist:error-jwt-token"))
                .thenThrow(new RuntimeException("Redis connection error"));

        filter.doFilterInternal(request, response, filterChain);

        verify(exceptionResolver).resolveException(eq(request), eq(response), eq(null), any(RuntimeException.class));
        verify(filterChain, never()).doFilter(request, response);
    }
}
