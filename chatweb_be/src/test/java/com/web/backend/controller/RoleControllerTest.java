package com.web.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.web.backend.config.localresolverconfig.Translator;
import com.web.backend.controller.request.RoleRequest;
import com.web.backend.controller.response.PermissionResponse;
import com.web.backend.controller.response.RoleResponse;
import com.web.backend.jwt.JwtAuthenticationFilter;
import com.web.backend.model.UserEntity;
import com.web.backend.service.JwtService;
import com.web.backend.service.RoleService;
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

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.messaging.simp.SimpMessagingTemplate;

@WebMvcTest(controllers = RoleController.class, excludeAutoConfiguration = {
                SecurityAutoConfiguration.class,
                SecurityFilterAutoConfiguration.class,
                OAuth2ClientAutoConfiguration.class,
                OAuth2ClientWebSecurityAutoConfiguration.class,
                OAuth2ResourceServerAutoConfiguration.class
}, excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)
})
class RoleControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @MockBean
        private RoleService roleService;

        @MockBean
        private JwtService jwtService;

        @MockBean
        private UserServiceDetail userServiceDetail;

        @MockBean
        private RedisTemplate<String, Object> redisTemplate;

        @MockBean
        private SimpMessagingTemplate simpMessagingTemplate;

        private UsernamePasswordAuthenticationToken mockAuth;
        private UserEntity mockAdmin;

        @BeforeEach
        void setUp() {
                ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
                messageSource.setBasename("i18n/messages");
                messageSource.setDefaultEncoding("UTF-8");
                Translator.setStaticMessageSource(messageSource);

                mockAdmin = new UserEntity();
                mockAdmin.setUsername("admin");

                mockAuth = new UsernamePasswordAuthenticationToken(mockAdmin, null, Collections.emptyList());
        }

        @Test
        void testGetAllRoles_Success() throws Exception {
                RoleResponse role = RoleResponse.builder()
                                .id(1L)
                                .name("ADMIN")
                                .build();

                when(roleService.getAllRoles()).thenReturn(List.of(role));

                mockMvc.perform(get("/api/roles")
                                .principal(mockAuth))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200))
                                .andExpect(jsonPath("$.data[0].name").value("ADMIN"));
        }

        @Test
        void testGetAllPermissions_Success() throws Exception {
                PermissionResponse permission = PermissionResponse.builder()
                                .id(1L)
                                .name("READ")
                                .build();

                when(roleService.getAllPermissions()).thenReturn(List.of(permission));

                mockMvc.perform(get("/api/roles/permissions")
                                .principal(mockAuth))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200))
                                .andExpect(jsonPath("$.data[0].name").value("READ"));
        }

        @Test
        void testCreateRole_Success() throws Exception {
                RoleRequest request = new RoleRequest();
                request.setName("USER");
                request.setDescription("User role");
                request.setPermissionIds(List.of(1L, 2L));

                RoleResponse roleResponse = RoleResponse.builder()
                                .id(2L)
                                .name("USER")
                                .build();

                when(roleService.createRole(any(RoleRequest.class))).thenReturn(roleResponse);

                mockMvc.perform(post("/api/roles")
                                .principal(mockAuth)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.code").value(201))
                                .andExpect(jsonPath("$.data.name").value("USER"));
        }

        @Test
        void testUpdateRole_Success() throws Exception {
                RoleRequest request = new RoleRequest();
                request.setName("USER_UPDATED");
                request.setDescription("Updated user role");
                request.setPermissionIds(List.of(1L, 2L, 3L));

                RoleResponse roleResponse = RoleResponse.builder()
                                .id(2L)
                                .name("USER_UPDATED")
                                .build();

                when(roleService.updateRole(eq(2L), any(RoleRequest.class))).thenReturn(roleResponse);

                mockMvc.perform(put("/api/roles/2")
                                .principal(mockAuth)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200))
                                .andExpect(jsonPath("$.data.name").value("USER_UPDATED"));
        }

        @Test
        void testDeleteRole_Success() throws Exception {
                mockMvc.perform(delete("/api/roles/2")
                                .principal(mockAuth))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(204));

                verify(roleService).deleteRole(2L);
        }
}
