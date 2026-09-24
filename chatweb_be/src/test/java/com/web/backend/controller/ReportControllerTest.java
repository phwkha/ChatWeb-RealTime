package com.web.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.web.backend.common.ReportReason;
import com.web.backend.common.ReportStatus;
import com.web.backend.config.localresolverconfig.Translator;
import com.web.backend.controller.request.CreateReportRequest;
import com.web.backend.controller.response.PageResponse;
import com.web.backend.controller.response.ReportResponse;
import com.web.backend.jwt.JwtAuthenticationFilter;
import com.web.backend.model.postgres.UserEntity;
import com.web.backend.service.JwtService;
import com.web.backend.service.ReportService;
import com.web.backend.service.UserServiceDetail;
import com.web.backend.service.WebSocketRoutingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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

@ActiveProfiles("test")
@WebMvcTest(controllers = ReportController.class, excludeAutoConfiguration = {
        SecurityAutoConfiguration.class,
        SecurityFilterAutoConfiguration.class,
        OAuth2ClientAutoConfiguration.class,
        OAuth2ClientWebSecurityAutoConfiguration.class,
        OAuth2ResourceServerAutoConfiguration.class
}, excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)
})
class ReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ReportService reportService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserServiceDetail userServiceDetail;

    @MockitoBean
    private RedisTemplate<String, Object> redisTemplate;

    @MockitoBean
    private SimpMessagingTemplate simpMessagingTemplate;

    @MockitoBean
    private WebSocketRoutingService webSocketRoutingService;

    private UsernamePasswordAuthenticationToken mockAuth;
    private UserEntity mockUser;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("i18n/messages");
        messageSource.setDefaultEncoding("UTF-8");
        Translator.setStaticMessageSource(messageSource);

        mockUser = new UserEntity();
        mockUser.setId(1L);
        mockUser.setUsername("testuser");

        mockAuth = new UsernamePasswordAuthenticationToken(mockUser, null, Collections.emptyList());
    }

    @Test
    void testCreateReport_Success() throws Exception {
        CreateReportRequest request = CreateReportRequest.builder()
                .reportedUsername("baduser")
                .reason(ReportReason.SPAM)
                .details("Posting spam links")
                .build();

        ReportResponse response = ReportResponse.builder()
                .id(10L)
                .reason(ReportReason.SPAM)
                .status(ReportStatus.PENDING)
                .details("Posting spam links")
                .build();

        when(reportService.createReport(eq(mockUser), any(CreateReportRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/reports")
                        .principal(mockAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.data.id").value(10L))
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        verify(reportService).createReport(eq(mockUser), any(CreateReportRequest.class));
    }

    @Test
    void testCreateReport_ValidationError_MissingReason() throws Exception {
        CreateReportRequest invalidRequest = CreateReportRequest.builder()
                .reportedUsername("baduser")
                .details("No reason given")
                .build();

        mockMvc.perform(post("/api/reports")
                        .principal(mockAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testGetMyReports_Success() throws Exception {
        ReportResponse report = ReportResponse.builder()
                .id(10L)
                .reason(ReportReason.HARASSMENT)
                .status(ReportStatus.PENDING)
                .build();

        PageResponse<ReportResponse> pageResponse = PageResponse.<ReportResponse>builder()
                .content(List.of(report))
                .pageNo(0)
                .pageSize(10)
                .totalElements(1L)
                .totalPages(1)
                .last(true)
                .build();

        when(reportService.getMyReports(mockUser, 0, 10, "desc")).thenReturn(pageResponse);

        mockMvc.perform(get("/api/reports/me")
                        .principal(mockAuth)
                        .param("page", "0")
                        .param("size", "10")
                        .param("sortDir", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content[0].id").value(10L))
                .andExpect(jsonPath("$.data.totalElements").value(1));

        verify(reportService).getMyReports(mockUser, 0, 10, "desc");
    }

    @Test
    void testCancelReport_Success() throws Exception {
        mockMvc.perform(delete("/api/reports/{id}", 10L)
                        .principal(mockAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(reportService).cancelReport(mockUser, 10L);
    }
}
