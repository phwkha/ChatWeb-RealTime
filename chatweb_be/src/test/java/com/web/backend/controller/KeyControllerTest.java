package com.web.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.web.backend.config.localresolverconfig.Translator;
import com.web.backend.controller.request.SaveKeyRequest;
import com.web.backend.controller.request.SavePublicKeyRequest;
import com.web.backend.jwt.JwtAuthenticationFilter;
import com.web.backend.model.postgres.UserEntity;
import com.web.backend.service.JwtService;
import com.web.backend.service.KeyService;
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
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = KeyController.class, excludeAutoConfiguration = {
        SecurityAutoConfiguration.class,
        SecurityFilterAutoConfiguration.class,
        OAuth2ClientAutoConfiguration.class,
        OAuth2ClientWebSecurityAutoConfiguration.class,
        OAuth2ResourceServerAutoConfiguration.class
}, excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)
})
class KeyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private KeyService keyService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserServiceDetail userServiceDetail;

    @MockBean
    private RedisTemplate<String, Object> redisTemplate;

    @MockBean
    private SimpMessagingTemplate simpMessagingTemplate;

    @MockBean
    private com.web.backend.service.WebSocketRoutingService webSocketRoutingService;

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

        mockAuth = new UsernamePasswordAuthenticationToken(mockUser, null, Collections.emptyList());
    }

    @Test
    void testGetRsaKey_Success() throws Exception {
        when(keyService.getRsaKey("testuser")).thenReturn("mockPrivateKey");

        mockMvc.perform(get("/api/keys/rsa")
                .principal(mockAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.privateKey").value("mockPrivateKey"));
    }

    @Test
    void testSaveRsaKey_Success() throws Exception {
        SaveKeyRequest request = new SaveKeyRequest();
        request.setKey("newPrivateKey");

        mockMvc.perform(post("/api/keys/rsa")
                .principal(mockAuth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(keyService).saveRsaKey("testuser", "newPrivateKey");
    }

    @Test
    void testGetPublicKey_Success() throws Exception {
        when(keyService.getPublicKey("otheruser")).thenReturn("mockPublicKey");

        mockMvc.perform(get("/api/keys/public-key/otheruser")
                .principal(mockAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("mockPublicKey"));
    }

    @Test
    void testSavePublicKey_Success() throws Exception {
        SavePublicKeyRequest request = new SavePublicKeyRequest();
        request.setPublicKey("newPublicKey");

        mockMvc.perform(post("/api/keys/public-key")
                .principal(mockAuth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(keyService).savePublicKey("testuser", "newPublicKey");
    }
}
