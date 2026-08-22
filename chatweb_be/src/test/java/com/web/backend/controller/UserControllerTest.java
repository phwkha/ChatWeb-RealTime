package com.web.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.web.backend.config.localresolverconfig.Translator;
import com.web.backend.controller.request.*;
import com.web.backend.controller.response.*;
import com.web.backend.service.UserService;
import com.web.backend.service.UserServiceDetail;
import com.web.backend.jwt.JwtAuthenticationFilter;
import com.web.backend.model.postgres.UserEntity;
import com.web.backend.service.JwtService;

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
import org.springframework.mock.web.MockMultipartFile;
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

@WebMvcTest(controllers = UserController.class, excludeAutoConfiguration = {
        SecurityAutoConfiguration.class,
        SecurityFilterAutoConfiguration.class,
        OAuth2ClientAutoConfiguration.class,
        OAuth2ClientWebSecurityAutoConfiguration.class,
        OAuth2ResourceServerAutoConfiguration.class
}, excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)
})
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    @MockBean
    private SimpMessagingTemplate simpMessagingTemplate;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserServiceDetail userServiceDetail;

    @MockBean
    private RedisTemplate<String, Object> redisTemplate;

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
        mockUser.setEmail("test@gmail.com");

        mockAuth = new UsernamePasswordAuthenticationToken(mockUser, null, Collections.emptyList());
    }

    @Test
    void testGetMe_Success() throws Exception {
        UserResponse mockResponse = new UserResponse();
        mockResponse.setUsername("testuser");

        when(userService.getMe("testuser")).thenReturn(mockResponse);

        mockMvc.perform(get("/api/users/me")
                .principal(mockAuth)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value("testuser"));
    }

    @Test
    void testGetProfileUser_Success() throws Exception {
        UserDetailResponse mockResponse = new UserDetailResponse();
        mockResponse.setUsername("testuser");
        mockResponse.setEmail("test@gmail.com");

        when(userService.getProfileUser("testuser")).thenReturn(mockResponse);

        mockMvc.perform(get("/api/users/profile")
                .principal(mockAuth)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value("testuser"))
                .andExpect(jsonPath("$.data.email").value("test@gmail.com"));
    }

    @Test
    void testUpdateUser_Success() throws Exception {
        UpdateUserRequest request = new UpdateUserRequest();
        request.setFirstName("John");
        request.setLastName("Doe");

        UserDetailResponse response = new UserDetailResponse();
        response.setUsername("testuser");
        response.setFirstName("John");
        response.setLastName("Doe");

        when(userService.updateUser(eq("testuser"), any(UpdateUserRequest.class))).thenReturn(response);

        mockMvc.perform(put("/api/users/profile")
                .principal(mockAuth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.firstName").value("John"));
    }

    @Test
    void testUpdateAvatar_Success() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", "image data".getBytes());
        when(userService.updateAvatar(eq("testuser"), any())).thenReturn("https://s3.amazonaws.com/avatar.png");

        mockMvc.perform(multipart("/api/users/avatar")
                .file(file)
                .principal(mockAuth)
                .with(request -> {
                    request.setMethod("PATCH");
                    return request;
                }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("https://s3.amazonaws.com/avatar.png"));
    }

    @Test
    void testChangePassword_Success() throws Exception {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("oldPass123!");
        request.setNewPassword("newPass123!");

        mockMvc.perform(post("/api/users/change-password")
                .principal(mockAuth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(userService).changePassword("testuser", "oldPass123!", "newPass123!");
    }

    @Test
    void testDeleteUser_Success() throws Exception {
        mockMvc.perform(delete("/api/users/me")
                .principal(mockAuth))
                .andExpect(status().isOk());

        verify(userService).deleteUser("testuser");
    }

    @Test
    void testAddAddress_Success() throws Exception {
        AddressRequest request = new AddressRequest();
        request.setStreet("123 Street");
        request.setWard("Ward 1");
        request.setDistrict("District 1");
        request.setCity("Hanoi");
        request.setCountry("Vietnam");

        UserDetailResponse response = new UserDetailResponse();
        response.setUsername("testuser");

        when(userService.addAddress(eq("testuser"), any(AddressRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/users/address")
                .principal(mockAuth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(201));
    }

    @Test
    void testUpdateAddress_Success() throws Exception {
        AddressRequest request = new AddressRequest();
        request.setStreet("123 Street");
        request.setWard("Ward 1");
        request.setDistrict("District 1");
        request.setCity("Hanoi");
        request.setCountry("Vietnam");

        UserDetailResponse response = new UserDetailResponse();

        when(userService.updateAddress(eq("testuser"), eq(1L), any(AddressRequest.class))).thenReturn(response);

        mockMvc.perform(put("/api/users/address/1")
                .principal(mockAuth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void testDeleteAddress_Success() throws Exception {
        UserDetailResponse response = new UserDetailResponse();

        when(userService.deleteAddress("testuser", 1L)).thenReturn(response);

        mockMvc.perform(delete("/api/users/address/1")
                .principal(mockAuth))
                .andExpect(status().isOk());
    }

    @Test
    void testGetAllAddresses_Success() throws Exception {
        AddressResponse addr = new AddressResponse();
        addr.setCity("Hanoi");
        when(userService.getAllAddresses("testuser")).thenReturn(List.of(addr));

        mockMvc.perform(get("/api/users/addresses")
                .principal(mockAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].city").value("Hanoi"));
    }

    @Test
    void testGetAddressDetail_Success() throws Exception {
        AddressResponse addr = new AddressResponse();
        addr.setCity("Hanoi");
        when(userService.getAddressById("testuser", 1L)).thenReturn(addr);

        mockMvc.perform(get("/api/users/address/1")
                .principal(mockAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.city").value("Hanoi"));
    }

    @Test
    void testInitiateEmailChange_Success() throws Exception {
        InitiateEmailChangeRequest request = new InitiateEmailChangeRequest();
        request.setNewEmail("new@gmail.com");
        request.setCurrentPassword("pass123!");

        mockMvc.perform(post("/api/users/initiate-email-change")
                .principal(mockAuth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(userService).initiateEmailChange("testuser", "new@gmail.com", "pass123!");
    }

    @Test
    void testVerifyEmailChange_Success() throws Exception {
        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setEmail("test@gmail.com");
        request.setOtp("123456");

        mockMvc.perform(post("/api/users/verify-email-change")
                .principal(mockAuth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(userService).verifyEmailChange("testuser", "123456");
    }

    @Test
    void testResendEmailVerification_Success() throws Exception {
        mockMvc.perform(post("/api/users/resend-email-verification")
                .principal(mockAuth))
                .andExpect(status().isOk());

        verify(userService).resendEmailChangeOtp("testuser");
    }

    @Test
    void testInitiatePhoneChange_Success() throws Exception {
        InitiatePhoneChangeRequest request = new InitiatePhoneChangeRequest();
        request.setNewPhone("0123456789");
        request.setCurrentPassword("pass123!");

        mockMvc.perform(post("/api/users/initiate-phone-change")
                .principal(mockAuth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(userService).initiatePhoneChange("testuser", "0123456789", "pass123!");
    }

    @Test
    void testVerifyPhoneChange_Success() throws Exception {
        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setEmail("test@gmail.com");
        request.setOtp("123456");

        mockMvc.perform(post("/api/users/verify-phone-change")
                .principal(mockAuth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(userService).verifyPhoneChange("testuser", "123456");
    }

    @Test
    void testResendPhoneVerification_Success() throws Exception {
        mockMvc.perform(post("/api/users/resend-phone-change-verification")
                .principal(mockAuth))
                .andExpect(status().isOk());

        verify(userService).resendPhoneChangeOtp("testuser");
    }
}
