package com.web.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.web.backend.common.ReportReason;
import com.web.backend.common.ReportStatus;
import com.web.backend.config.localresolverconfig.Translator;
import com.web.backend.controller.request.AdminReportSearchRequest;
import com.web.backend.controller.request.ResolveReportRequest;
import com.web.backend.controller.response.PageResponse;
import com.web.backend.controller.response.ReportDetailResponse;
import com.web.backend.controller.response.ReportResponse;
import com.web.backend.controller.response.ReportStatisticsResponse;
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
@WebMvcTest(controllers = AdminReportController.class, excludeAutoConfiguration = {
        SecurityAutoConfiguration.class,
        SecurityFilterAutoConfiguration.class,
        OAuth2ClientAutoConfiguration.class,
        OAuth2ClientWebSecurityAutoConfiguration.class,
        OAuth2ResourceServerAutoConfiguration.class
}, excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)
})
class AdminReportControllerTest {

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
    private UserEntity mockAdmin;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("i18n/messages");
        messageSource.setDefaultEncoding("UTF-8");
        Translator.setStaticMessageSource(messageSource);

        mockAdmin = new UserEntity();
        mockAdmin.setId(99L);
        mockAdmin.setUsername("admin");

        mockAuth = new UsernamePasswordAuthenticationToken(mockAdmin, null, Collections.emptyList());
    }

    @Test
    void testGetReports_Success() throws Exception {
        ReportResponse report = ReportResponse.builder()
                .id(1L)
                .reason(ReportReason.SPAM)
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

        when(reportService.getReportsForAdmin(any(AdminReportSearchRequest.class), eq(0), eq(10), any()))
                .thenReturn(pageResponse);

        mockMvc.perform(get("/api/admin/reports")
                        .principal(mockAuth)
                        .param("page", "0")
                        .param("size", "10")
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content[0].id").value(1L));

        verify(reportService).getReportsForAdmin(any(AdminReportSearchRequest.class), eq(0), eq(10), any());
    }

    @Test
    void testGetStatistics_Success() throws Exception {
        ReportStatisticsResponse statistics = ReportStatisticsResponse.builder()
                .totalReports(10L)
                .pendingReports(4L)
                .resolvedReports(5L)
                .dismissedReports(1L)
                .build();

        when(reportService.getReportStatistics()).thenReturn(statistics);

        mockMvc.perform(get("/api/admin/reports/statistics")
                        .principal(mockAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalReports").value(10))
                .andExpect(jsonPath("$.data.pendingReports").value(4));

        verify(reportService).getReportStatistics();
    }

    @Test
    void testGetReportById_Success() throws Exception {
        ReportDetailResponse response = ReportDetailResponse.builder()
                .id(5L)
                .reason(ReportReason.HARASSMENT)
                .status(ReportStatus.PENDING)
                .reportedUserTotalReports(2L)
                .build();

        when(reportService.getReportById(5L)).thenReturn(response);

        mockMvc.perform(get("/api/admin/reports/{id}", 5L)
                        .principal(mockAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(5L))
                .andExpect(jsonPath("$.data.reportedUserTotalReports").value(2));

        verify(reportService).getReportById(5L);
    }

    @Test
    void testResolveReport_Success() throws Exception {
        ResolveReportRequest request = ResolveReportRequest.builder()
                .status(ReportStatus.RESOLVED)
                .resolutionNote("Confirmed violation")
                .lockReportedUser(true)
                .build();

        ReportDetailResponse response = ReportDetailResponse.builder()
                .id(5L)
                .status(ReportStatus.RESOLVED)
                .resolutionNote("Confirmed violation")
                .build();

        when(reportService.resolveReport(eq(mockAdmin), eq(5L), any(ResolveReportRequest.class)))
                .thenReturn(response);

        mockMvc.perform(put("/api/admin/reports/{id}/resolve", 5L)
                        .principal(mockAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("RESOLVED"));

        verify(reportService).resolveReport(eq(mockAdmin), eq(5L), any(ResolveReportRequest.class));
    }

    @Test
    void testDeleteReport_Success() throws Exception {
        mockMvc.perform(delete("/api/admin/reports/{id}", 5L)
                        .principal(mockAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(reportService).deleteReport(5L);
    }
}
