package com.web.backend.service;

import com.web.backend.common.ReportReason;
import com.web.backend.common.ReportStatus;
import com.web.backend.common.UserStatus;
import com.web.backend.config.localresolverconfig.Translator;
import com.web.backend.controller.request.CreateReportRequest;
import com.web.backend.controller.request.ResolveReportRequest;
import com.web.backend.controller.response.PageResponse;
import com.web.backend.controller.response.ReportDetailResponse;
import com.web.backend.controller.response.ReportResponse;
import com.web.backend.controller.response.ReportStatisticsResponse;
import com.web.backend.exception.custom.AccessForbiddenException;
import com.web.backend.exception.custom.InvalidDataException;
import com.web.backend.exception.custom.ResourceConflictException;
import com.web.backend.mapper.ReportMapper;
import com.web.backend.model.postgres.ReportEntity;
import com.web.backend.model.postgres.UserEntity;
import com.web.backend.repository.ReportRepository;
import com.web.backend.repository.UserRepository;
import com.web.backend.service.impl.ReportServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AdminService adminService;

    @Mock
    private ReportMapper reportMapper;

    @InjectMocks
    private ReportServiceImpl reportService;

    private UserEntity reporter;
    private UserEntity reportedUser;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messageSource = mock(ResourceBundleMessageSource.class);
        lenient().when(messageSource.getMessage(anyString(), any(), any())).thenReturn("Mocked Error Message");
        Translator.setStaticMessageSource(messageSource);

        reporter = new UserEntity();
        reporter.setId(1L);
        reporter.setUsername("reporter");
        reporter.setUserStatus(UserStatus.ACTIVE);

        reportedUser = new UserEntity();
        reportedUser.setId(2L);
        reportedUser.setUsername("reported");
        reportedUser.setUserStatus(UserStatus.ACTIVE);
    }

    @Test
    void createReport_success() {
        CreateReportRequest request = CreateReportRequest.builder()
                .reportedUsername("reported")
                .reason(ReportReason.SPAM)
                .details("Spamming advertisement")
                .build();

        when(userRepository.findByUsername("reported")).thenReturn(Optional.of(reportedUser));
        when(reportRepository.existsByReporterIdAndReportedUserIdAndStatus(1L, 2L, ReportStatus.PENDING))
                .thenReturn(false);

        ReportEntity savedEntity = ReportEntity.builder()
                .reporter(reporter)
                .reportedUser(reportedUser)
                .reason(ReportReason.SPAM)
                .details("Spamming advertisement")
                .status(ReportStatus.PENDING)
                .build();
        savedEntity.setId(10L);

        when(reportRepository.save(any(ReportEntity.class))).thenReturn(savedEntity);
        when(reportMapper.toReportResponse(savedEntity)).thenReturn(
                ReportResponse.builder().id(10L).reason(ReportReason.SPAM).status(ReportStatus.PENDING).build()
        );

        ReportResponse result = reportService.createReport(reporter, request);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getStatus()).isEqualTo(ReportStatus.PENDING);
        verify(reportRepository).save(any(ReportEntity.class));
    }

    @Test
    void createReport_selfReport_throwsException() {
        CreateReportRequest request = CreateReportRequest.builder()
                .reportedUsername("reporter")
                .reason(ReportReason.OTHER)
                .build();

        when(userRepository.findByUsername("reporter")).thenReturn(Optional.of(reporter));

        assertThatThrownBy(() -> reportService.createReport(reporter, request))
                .isInstanceOf(InvalidDataException.class);

        verify(reportRepository, never()).save(any());
    }

    @Test
    void createReport_pendingExists_throwsConflict() {
        CreateReportRequest request = CreateReportRequest.builder()
                .reportedUsername("reported")
                .reason(ReportReason.HARASSMENT)
                .build();

        when(userRepository.findByUsername("reported")).thenReturn(Optional.of(reportedUser));
        when(reportRepository.existsByReporterIdAndReportedUserIdAndStatus(1L, 2L, ReportStatus.PENDING))
                .thenReturn(true);

        assertThatThrownBy(() -> reportService.createReport(reporter, request))
                .isInstanceOf(ResourceConflictException.class);

        verify(reportRepository, never()).save(any());
    }

    @Test
    void getMyReports_success() {
        ReportEntity report = ReportEntity.builder()
                .reporter(reporter)
                .reportedUser(reportedUser)
                .reason(ReportReason.SPAM)
                .status(ReportStatus.PENDING)
                .build();
        report.setId(10L);

        Page<ReportEntity> page = new PageImpl<>(List.of(report));
        when(reportRepository.findByReporterId(eq(1L), any(Pageable.class))).thenReturn(page);
        when(reportMapper.toReportResponse(report)).thenReturn(
                ReportResponse.builder().id(10L).status(ReportStatus.PENDING).build()
        );

        PageResponse<ReportResponse> result = reportService.getMyReports(reporter, 0, 10, "desc");

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void cancelReport_success() {
        ReportEntity report = ReportEntity.builder()
                .reporter(reporter)
                .reportedUser(reportedUser)
                .status(ReportStatus.PENDING)
                .build();
        report.setId(10L);

        when(reportRepository.findById(10L)).thenReturn(Optional.of(report));

        reportService.cancelReport(reporter, 10L);

        verify(reportRepository).deleteById(10L);
    }

    @Test
    void cancelReport_notPending_throwsException() {
        ReportEntity report = ReportEntity.builder()
                .reporter(reporter)
                .reportedUser(reportedUser)
                .status(ReportStatus.RESOLVED)
                .build();
        report.setId(10L);

        when(reportRepository.findById(10L)).thenReturn(Optional.of(report));

        assertThatThrownBy(() -> reportService.cancelReport(reporter, 10L))
                .isInstanceOf(AccessForbiddenException.class);

        verify(reportRepository, never()).deleteById(any());
    }

    @Test
    void resolveReport_withLockUser_success() {
        UserEntity admin = new UserEntity();
        admin.setId(99L);
        admin.setUsername("admin");

        ReportEntity report = ReportEntity.builder()
                .reporter(reporter)
                .reportedUser(reportedUser)
                .status(ReportStatus.PENDING)
                .build();
        report.setId(10L);

        when(reportRepository.findWithDetailsById(10L)).thenReturn(Optional.of(report));
        when(reportRepository.save(any(ReportEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reportRepository.countByReportedUserId(2L)).thenReturn(3L);

        ReportDetailResponse detailResponse = ReportDetailResponse.builder()
                .id(10L)
                .status(ReportStatus.RESOLVED)
                .resolutionNote("Violation confirmed")
                .reportedUserTotalReports(3L)
                .build();
        when(reportMapper.toReportDetailResponse(any(ReportEntity.class))).thenReturn(detailResponse);

        ResolveReportRequest request = ResolveReportRequest.builder()
                .status(ReportStatus.RESOLVED)
                .resolutionNote("Violation confirmed")
                .lockReportedUser(true)
                .build();

        ReportDetailResponse result = reportService.resolveReport(admin, 10L, request);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(ReportStatus.RESOLVED);
        verify(adminService).lockUser("reported");
        verify(reportRepository).save(report);
    }

    @Test
    void getReportStatistics_success() {
        when(reportRepository.count()).thenReturn(15L);
        when(reportRepository.countByStatus(ReportStatus.PENDING)).thenReturn(5L);
        when(reportRepository.countByStatus(ReportStatus.RESOLVED)).thenReturn(8L);
        when(reportRepository.countByStatus(ReportStatus.DISMISSED)).thenReturn(2L);

        ReportStatisticsResponse stats = reportService.getReportStatistics();

        assertThat(stats.getTotalReports()).isEqualTo(15L);
        assertThat(stats.getPendingReports()).isEqualTo(5L);
        assertThat(stats.getResolvedReports()).isEqualTo(8L);
        assertThat(stats.getDismissedReports()).isEqualTo(2L);
    }
}
