package com.web.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.web.backend.config.localresolverconfig.Translator;
import com.web.backend.controller.request.MarkReadRequest;
import com.web.backend.controller.response.ChatMessageResponse;
import com.web.backend.controller.response.CursorResponse;
import com.web.backend.controller.response.UnreadCountsResponse;
import com.web.backend.jwt.JwtAuthenticationFilter;
import com.web.backend.model.UserEntity;
import com.web.backend.service.JwtService;
import com.web.backend.service.MessageService;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MessageController.class, excludeAutoConfiguration = {
                SecurityAutoConfiguration.class,
                SecurityFilterAutoConfiguration.class,
                OAuth2ClientAutoConfiguration.class,
                OAuth2ClientWebSecurityAutoConfiguration.class,
                OAuth2ResourceServerAutoConfiguration.class
}, excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)
})
class MessageControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @MockBean
        private MessageService messageService;

        @MockBean
        private SimpMessagingTemplate simpMessagingTemplate;

        @MockBean
        private JwtService jwtService;

        @MockBean
        private UserServiceDetail userServiceDetail;

        @MockBean
        private RedisTemplate<String, Object> redisTemplate;

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
        void testGetPrivateMessage_Success() throws Exception {
                ChatMessageResponse chatResponse = ChatMessageResponse.builder()
                                .id("msg123")
                                .content("Hello!")
                                .build();

                CursorResponse<ChatMessageResponse> cursorResponse = new CursorResponse<>(List.of(chatResponse),
                                "nextCursor123", true);

                when(messageService.findPrivateMessageWithCursor(eq("testuser"), eq("otheruser"), eq("cursor123"),
                                eq(20)))
                                .thenReturn(cursorResponse);

                mockMvc.perform(get("/api/messages/private")
                                .principal(mockAuth)
                                .param("user1", "testuser")
                                .param("user2", "otheruser")
                                .param("cursor", "cursor123")
                                .param("size", "20"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200))
                                .andExpect(jsonPath("$.data.content[0].id").value("msg123"))
                                .andExpect(jsonPath("$.data.nextCursor").value("nextCursor123"));
        }

        @Test
        void testGetUnreadCounts_Success() throws Exception {
                Map<String, Long> counts = new HashMap<>();
                counts.put("otheruser", 5L);

                UnreadCountsResponse unreadResponse = UnreadCountsResponse.builder()
                                .unreadCounts(counts)
                                .build();

                when(messageService.getUnreadMessageCounts("testuser")).thenReturn(unreadResponse);

                mockMvc.perform(get("/api/messages/unread-counts")
                                .principal(mockAuth))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200))
                                .andExpect(jsonPath("$.data.unreadCounts.otheruser").value(5));
        }

        @Test
        void testMarkAsRead_Success() throws Exception {
                MarkReadRequest request = new MarkReadRequest();
                request.setSender("otheruser");

                mockMvc.perform(post("/api/messages/mark-as-read")
                                .principal(mockAuth)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200));

                verify(messageService).markMessagesAsRead("testuser", "otheruser");
        }

        @Test
        void testGetMessageById_Success() throws Exception {
                ChatMessageResponse chatResponse = ChatMessageResponse.builder()
                                .id("msg123")
                                .content("Hello!")
                                .build();

                when(messageService.getMessageById("msg123", "testuser")).thenReturn(chatResponse);

                mockMvc.perform(get("/api/messages/msg123")
                                .principal(mockAuth))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200))
                                .andExpect(jsonPath("$.data.id").value("msg123"));
        }
}
