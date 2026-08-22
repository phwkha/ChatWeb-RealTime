package com.web.backend.controller;

import com.web.backend.config.localresolverconfig.Translator;
import com.web.backend.controller.response.PageResponse;
import com.web.backend.controller.response.UserDetailResponse;
import com.web.backend.controller.response.UserSummaryResponse;
import com.web.backend.jwt.JwtAuthenticationFilter;
import com.web.backend.service.JwtService;
import com.web.backend.service.SearchUserService;
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
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.messaging.simp.SimpMessagingTemplate;

@WebMvcTest(controllers = SearchUserController.class, excludeAutoConfiguration = {
                SecurityAutoConfiguration.class,
                SecurityFilterAutoConfiguration.class,
                OAuth2ClientAutoConfiguration.class,
                OAuth2ClientWebSecurityAutoConfiguration.class,
                OAuth2ResourceServerAutoConfiguration.class
}, excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)
})
class SearchUserControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @MockBean
        private SearchUserService searchUserService;

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

        @BeforeEach
        void setUp() {
                ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
                messageSource.setBasename("i18n/messages");
                messageSource.setDefaultEncoding("UTF-8");
                Translator.setStaticMessageSource(messageSource);
        }

        @Test
        void testSearchUsers_Success() throws Exception {
                UserSummaryResponse summary = UserSummaryResponse.builder()
                                .username("foundUser")
                                .build();
                PageResponse<UserSummaryResponse> pageResponse = PageResponse.<UserSummaryResponse>builder()
                                .content(List.of(summary))
                                .build();

                when(searchUserService.searchUsers(isNull(), eq("keyword"), eq(0), eq(10), eq("desc")))
                                .thenReturn(pageResponse);

                mockMvc.perform(get("/api/search/users")
                                .param("keyword", "keyword")
                                .param("page", "0")
                                .param("size", "10")
                                .param("sortDir", "desc"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200))
                                .andExpect(jsonPath("$.data.content[0].username").value("foundUser"));
        }

        @Test
        void testSearchUsers_WithAuth_Success() throws Exception {
                com.web.backend.model.postgres.UserEntity mockUser = new com.web.backend.model.postgres.UserEntity();
                mockUser.setUsername("testuser");
                org.springframework.security.authentication.UsernamePasswordAuthenticationToken mockAuth =
                        new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(mockUser, null, java.util.Collections.emptyList());

                UserSummaryResponse summary = UserSummaryResponse.builder()
                                .username("foundUser")
                                .build();
                PageResponse<UserSummaryResponse> pageResponse = PageResponse.<UserSummaryResponse>builder()
                                .content(List.of(summary))
                                .build();

                when(searchUserService.searchUsers(eq("testuser"), eq("keyword"), eq(0), eq(10), eq("desc")))
                                .thenReturn(pageResponse);

                mockMvc.perform(get("/api/search/users")
                                .principal(mockAuth)
                                .param("keyword", "keyword")
                                .param("page", "0")
                                .param("size", "10")
                                .param("sortDir", "desc"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200))
                                .andExpect(jsonPath("$.data.content[0].username").value("foundUser"));
        }

        @Test
        void testAdvanceSearch_Success() throws Exception {
                UserDetailResponse detail = new UserDetailResponse();
                detail.setUsername("foundUser");
                PageResponse<UserDetailResponse> pageResponse = PageResponse.<UserDetailResponse>builder()
                                .content(List.of(detail))
                                .build();

                when(searchUserService.advanceSearchWithSpecifications(any(Pageable.class), any(), any()))
                                .thenReturn(pageResponse);

                mockMvc.perform(get("/api/search/users/filter")
                                .param("user", "name:test")
                                .param("address", "city:hanoi"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200))
                                .andExpect(jsonPath("$.data.content[0].username").value("foundUser"));
        }
}
