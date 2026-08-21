package com.web.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.web.backend.config.localresolverconfig.Translator;
import com.web.backend.controller.request.AddressRequest;
import com.web.backend.controller.request.AdminCreateUserRequest;
import com.web.backend.controller.request.AdminUpdateUserRequest;
import com.web.backend.controller.response.*;
import com.web.backend.jwt.JwtAuthenticationFilter;
import com.web.backend.model.postgres.UserEntity;
import com.web.backend.service.AdminService;
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
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
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

@WebMvcTest(controllers = AdminController.class, excludeAutoConfiguration = {
                SecurityAutoConfiguration.class,
                SecurityFilterAutoConfiguration.class,
                OAuth2ClientAutoConfiguration.class,
                OAuth2ClientWebSecurityAutoConfiguration.class,
                OAuth2ResourceServerAutoConfiguration.class
}, excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)
})
class AdminControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @MockBean
        private AdminService adminService;

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
        void testGetAllUsers_Success() throws Exception {
                UserSummaryResponse summary = UserSummaryResponse.builder().username("user1").build();
                PageResponse<UserSummaryResponse> pageResponse = PageResponse.<UserSummaryResponse>builder()
                                .content(List.of(summary))
                                .build();

                when(adminService.getAllUsers(eq(0), eq(10), any())).thenReturn(pageResponse);

                mockMvc.perform(get("/api/admin/users")
                                .principal(mockAuth)
                                .param("page", "0")
                                .param("size", "10"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200))
                                .andExpect(jsonPath("$.data.content[0].username").value("user1"));
        }

        @Test
        void testGetOnlineUsers_Success() throws Exception {
                UserSummaryResponse summary = UserSummaryResponse.builder().username("user1").build();
                PageResponse<UserSummaryResponse> pageResponse = PageResponse.<UserSummaryResponse>builder()
                                .content(List.of(summary))
                                .build();

                when(adminService.getOnlineUsers(eq(0), eq(10))).thenReturn(pageResponse);

                mockMvc.perform(get("/api/admin/online")
                                .principal(mockAuth)
                                .param("page", "0")
                                .param("size", "10"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        void testGetUserByUsername_Success() throws Exception {
                UserDetailResponse detail = new UserDetailResponse();
                detail.setUsername("user1");

                when(adminService.getUserByUsername("user1")).thenReturn(detail);

                mockMvc.perform(get("/api/admin/user/user1")
                                .principal(mockAuth))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200))
                                .andExpect(jsonPath("$.data.username").value("user1"));
        }

        @Test
        void testAddUser_Success() throws Exception {
                AdminCreateUserRequest request = new AdminCreateUserRequest();
                request.setUsername("newuser");
                request.setPassword("password123");
                request.setFirstName("First");
                request.setEmail("test@gmail.com");

                UserResponse response = new UserResponse();
                response.setUsername("newuser");

                when(adminService.adminCreateUser(any(AdminCreateUserRequest.class))).thenReturn(response);

                mockMvc.perform(post("/api/admin/add")
                                .principal(mockAuth)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.code").value(201))
                                .andExpect(jsonPath("$.data.username").value("newuser"));
        }

        @Test
        void testUnlockUser_Success() throws Exception {
                UserResponse response = new UserResponse();
                response.setUsername("user1");

                when(adminService.unlockUser("user1")).thenReturn(response);

                mockMvc.perform(post("/api/admin/user1/unlock")
                                .principal(mockAuth))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        void testLockUser_Success() throws Exception {
                UserResponse response = new UserResponse();
                response.setUsername("user1");

                when(adminService.lockUser("user1")).thenReturn(response);

                mockMvc.perform(post("/api/admin/user1/lock")
                                .principal(mockAuth))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        void testDeleteAvatar_Success() throws Exception {
                mockMvc.perform(post("/api/admin/user1/delete-avatar")
                                .principal(mockAuth))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200));

                verify(adminService).deleteAvatar("user1");
        }

        @Test
        void testUpdateUser_Success() throws Exception {
                AdminUpdateUserRequest request = new AdminUpdateUserRequest();
                request.setEmail("updated@gmail.com");

                UserResponse response = new UserResponse();
                response.setUsername("user1");

                when(adminService.adminUpdateUser(eq("user1"), any(AdminUpdateUserRequest.class))).thenReturn(response);

                mockMvc.perform(put("/api/admin/user1")
                                .principal(mockAuth)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        void testDeleteUser_Success() throws Exception {
                mockMvc.perform(delete("/api/admin/user1")
                                .principal(mockAuth))
                                .andExpect(status().isNoContent());

                verify(adminService).adminDeleteUser("user1", "admin");
        }

        @Test
        void testGetAllAddressesForUser_Success() throws Exception {
                AddressResponse address = new AddressResponse();
                address.setId(1L);

                when(adminService.adminGetAllAddresses("user1")).thenReturn(List.of(address));

                mockMvc.perform(get("/api/admin/user/user1/addresses")
                                .principal(mockAuth))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        void testGetAddressByIdForUser_Success() throws Exception {
                AddressResponse address = new AddressResponse();
                address.setId(1L);

                when(adminService.adminGetAddressById("user1", 1L)).thenReturn(address);

                mockMvc.perform(get("/api/admin/user/user1/address/1")
                                .principal(mockAuth))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        void testUpdateAddressForUser_Success() throws Exception {
                AddressRequest request = new AddressRequest();
                request.setCity("Hanoi");
                request.setStreet("Hoan Kiem");
                request.setCountry("Vietnam");
                request.setDistrict("Hoan Kiem District");
                request.setWard("Hang Trong");

                UserDetailResponse response = new UserDetailResponse();
                response.setUsername("user1");

                when(adminService.adminUpdateAddress(eq("user1"), eq(1L), any(AddressRequest.class)))
                                .thenReturn(response);

                mockMvc.perform(put("/api/admin/user/user1/address/1")
                                .principal(mockAuth)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        void testDeleteAddressForUser_Success() throws Exception {
                mockMvc.perform(delete("/api/admin/user/user1/address/1")
                                .principal(mockAuth))
                                .andExpect(status().isNoContent());

                verify(adminService).adminDeleteAddress("user1", 1L);
        }
}
