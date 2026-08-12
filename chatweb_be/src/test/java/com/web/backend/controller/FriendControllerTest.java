package com.web.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.web.backend.config.localresolverconfig.Translator;
import com.web.backend.controller.response.PageResponse;
import com.web.backend.controller.response.UserSummaryResponse;
import com.web.backend.jwt.JwtAuthenticationFilter;
import com.web.backend.model.UserEntity;
import com.web.backend.service.FriendService;
import com.web.backend.service.JwtService;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.messaging.simp.SimpMessagingTemplate;

@WebMvcTest(controllers = FriendController.class, excludeAutoConfiguration = {
                SecurityAutoConfiguration.class,
                SecurityFilterAutoConfiguration.class,
                OAuth2ClientAutoConfiguration.class,
                OAuth2ClientWebSecurityAutoConfiguration.class,
                OAuth2ResourceServerAutoConfiguration.class
}, excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)
})
class FriendControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @MockBean
        private FriendService friendService;

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
                new Translator(messageSource);

                mockUser = new UserEntity();
                mockUser.setUsername("testuser");

                mockAuth = new UsernamePasswordAuthenticationToken(mockUser, null, Collections.emptyList());
        }

        @Test
        void testGetFriendRequests_Success() throws Exception {
                UserSummaryResponse summary = UserSummaryResponse.builder()
                                .username("friend1")
                                .build();
                PageResponse<UserSummaryResponse> pageResponse = PageResponse.<UserSummaryResponse>builder()
                                .content(List.of(summary))
                                .build();

                when(friendService.getPendingRequests(eq("testuser"), eq(0), eq(10), eq("desc")))
                                .thenReturn(pageResponse);

                mockMvc.perform(get("/api/friends/requests")
                                .principal(mockAuth)
                                .param("page", "0")
                                .param("size", "10")
                                .param("sortDir", "desc"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200))
                                .andExpect(jsonPath("$.data.content[0].username").value("friend1"));
        }

        @Test
        void testGetSentRequests_Success() throws Exception {
                UserSummaryResponse summary = UserSummaryResponse.builder()
                                .username("friend1")
                                .build();
                PageResponse<UserSummaryResponse> pageResponse = PageResponse.<UserSummaryResponse>builder()
                                .content(List.of(summary))
                                .build();

                when(friendService.getSentRequests(eq("testuser"), eq(0), eq(10), eq("desc")))
                                .thenReturn(pageResponse);

                mockMvc.perform(get("/api/friends/sent")
                                .principal(mockAuth)
                                .param("page", "0")
                                .param("size", "10")
                                .param("sortDir", "desc"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200))
                                .andExpect(jsonPath("$.data.content[0].username").value("friend1"));
        }

        @Test
        void testGetFriendsList_Success() throws Exception {
                UserSummaryResponse summary = UserSummaryResponse.builder()
                                .username("friend1")
                                .build();
                PageResponse<UserSummaryResponse> pageResponse = PageResponse.<UserSummaryResponse>builder()
                                .content(List.of(summary))
                                .build();

                when(friendService.getFriendsList(eq("testuser"), eq(0), eq(10), eq("desc")))
                                .thenReturn(pageResponse);

                mockMvc.perform(get("/api/friends")
                                .principal(mockAuth)
                                .param("page", "0")
                                .param("size", "10")
                                .param("sortDir", "desc"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200))
                                .andExpect(jsonPath("$.data.content[0].username").value("friend1"));
        }

        @Test
        void testDeleteFriendship_Success() throws Exception {
                mockMvc.perform(delete("/api/friends/otheruser")
                                .principal(mockAuth))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200));

                verify(friendService).deleteFriendship("testuser", "otheruser");
        }

        @Test
        void testBlockUser_Success() throws Exception {
                mockMvc.perform(post("/api/friends/block/otheruser")
                                .principal(mockAuth))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200));

                verify(friendService).blockUser("testuser", "otheruser");
        }
}
