package com.web.backend.service.impl;

import com.web.backend.common.ReportStatus;
import com.web.backend.common.UserStatus;
import com.web.backend.config.localresolverconfig.Translator;
import com.web.backend.controller.request.AdminReportSearchRequest;
import com.web.backend.controller.request.CreateReportRequest;
import com.web.backend.controller.request.ResolveReportRequest;
import com.web.backend.controller.response.PageResponse;
import com.web.backend.controller.response.ReportDetailResponse;
import com.web.backend.controller.response.ReportResponse;
import com.web.backend.controller.response.ReportStatisticsResponse;
import com.web.backend.exception.custom.AccessForbiddenException;
import com.web.backend.exception.custom.InvalidDataException;
import com.web.backend.exception.custom.ResourceConflictException;
import com.web.backend.exception.custom.ResourceNotFoundException;
import com.web.backend.mapper.ReportMapper;
import com.web.backend.model.postgres.ReportEntity;
import com.web.backend.model.postgres.UserEntity;
import com.web.backend.repository.ReportRepository;
import com.web.backend.repository.UserRepository;
import com.web.backend.repository.specification.ReportSearchSpecifications;
import com.web.backend.service.AdminService;
import com.web.backend.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j(topic = "REPORT-SERVICE")
public class ReportServiceImpl implements ReportService {

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final AdminService adminService;
    private final ReportMapper reportMapper;

    private static final String ID_STRING = "id";
    private static final String CREATE_AT_STRING = "createAt";
    private static final String STATUS_STRING = "status";
    private static final String REASON_STRING = "reason";
    private static final String DELIMITE_STRING = ":";
    private static final String ASC_STRING = "asc";

    private static final String ERROR_REPORT_SELF_REPORT_STRING = "error.report.self_report";
    private static final String ERROR_USER_NOT_FOUND_STRING = "error.user.not_found";
    private static final String ERROR_REPORT_PENDING_EXISTS_STRING = "error.report.pending_exists";
    private static final String ERROR_REPORT_NOT_FOUND_STRING = "error.report.not_found";
    private static final String ERROR_REPORT_CANNOT_CANCEL_STRING = "error.report.cannot_cancel";
    private static final String VALID_REPORT_TARGET_REQUIRED_STRING = "valid.report_target_required";

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            ID_STRING, CREATE_AT_STRING, STATUS_STRING, REASON_STRING);

    @Override
    @Transactional
    public ReportResponse createReport(UserEntity currentUser, CreateReportRequest request) {
        UserEntity reportedUser = resolveReportedUser(request);

        if (currentUser.getId().equals(reportedUser.getId())) {
            throw new InvalidDataException(Translator.tolocale(ERROR_REPORT_SELF_REPORT_STRING));
        }

        if (reportedUser.getUserStatus() == UserStatus.INACTIVE) {
            throw new ResourceNotFoundException(Translator.tolocale(ERROR_USER_NOT_FOUND_STRING));
        }

        boolean hasPending = reportRepository.existsByReporterIdAndReportedUserIdAndStatus(
                currentUser.getId(), reportedUser.getId(), ReportStatus.PENDING);
        if (hasPending) {
            throw new ResourceConflictException(Translator.tolocale(ERROR_REPORT_PENDING_EXISTS_STRING));
        }

        ReportEntity report = ReportEntity.builder()
                .reporter(currentUser)
                .reportedUser(reportedUser)
                .reason(request.getReason())
                .details(request.getDetails())
                .status(ReportStatus.PENDING)
                .build();

        ReportEntity saved = reportRepository.save(report);
        log.info("User '{}' reported user '{}' for reason: {}", currentUser.getUsername(), reportedUser.getUsername(), request.getReason());

        return reportMapper.toReportResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReportResponse> getMyReports(UserEntity currentUser, int pageNo, int pageSize, String sortDir) {
        Sort.Direction direction = ASC_STRING.equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(pageNo, pageSize, Sort.by(direction, CREATE_AT_STRING));

        Page<ReportEntity> page = reportRepository.findByReporterId(currentUser.getId(), pageable);
        Page<ReportResponse> responsePage = page.map(reportMapper::toReportResponse);

        return buildPageResponse(responsePage);
    }

    @Override
    @Transactional
    public void cancelReport(UserEntity currentUser, Long reportId) {
        ReportEntity report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException(Translator.tolocale(ERROR_REPORT_NOT_FOUND_STRING)));

        if (!report.getReporter().getId().equals(currentUser.getId()) || report.getStatus() != ReportStatus.PENDING) {
            throw new AccessForbiddenException(Translator.tolocale(ERROR_REPORT_CANNOT_CANCEL_STRING));
        }

        reportRepository.deleteById(report.getId());
        log.info("User '{}' cancelled report id: {}", currentUser.getUsername(), reportId);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReportResponse> getReportsForAdmin(AdminReportSearchRequest request, int pageNo, int pageSize, String... sorts) {
        Pageable pageable = buildPageable(pageNo, pageSize, sorts);

        Specification<ReportEntity> spec = Specification.<ReportEntity>unrestricted()
                .and(ReportSearchSpecifications.hasStatus(request != null ? request.getStatus() : null))
                .and(ReportSearchSpecifications.hasReason(request != null ? request.getReason() : null))
                .and(ReportSearchSpecifications.containsKeyword(request != null ? request.getKeyword() : null));

        Page<ReportEntity> page = reportRepository.findAll(spec, pageable);
        Page<ReportResponse> responsePage = page.map(reportMapper::toReportResponse);

        return buildPageResponse(responsePage);
    }

    @Override
    @Transactional(readOnly = true)
    public ReportDetailResponse getReportById(Long reportId) {
        ReportEntity report = reportRepository.findWithDetailsById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException(Translator.tolocale(ERROR_REPORT_NOT_FOUND_STRING)));

        long totalReports = reportRepository.countByReportedUserId(report.getReportedUser().getId());

        ReportDetailResponse response = reportMapper.toReportDetailResponse(report);
        response.setReportedUserTotalReports(totalReports);

        return response;
    }

    @Override
    @Transactional
    public ReportDetailResponse resolveReport(UserEntity adminUser, Long reportId, ResolveReportRequest request) {
        ReportEntity report = reportRepository.findWithDetailsById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException(Translator.tolocale(ERROR_REPORT_NOT_FOUND_STRING)));

        report.setStatus(request.getStatus());
        report.setResolutionNote(request.getResolutionNote());
        report.setResolvedBy(adminUser);
        report.setResolvedAt(Instant.now());

        ReportEntity saved = reportRepository.save(report);

        if (Boolean.TRUE.equals(request.getLockReportedUser())) {
            adminService.lockUser(report.getReportedUser().getUsername());
            log.info("Admin '{}' locked reported user '{}' via report id: {}",
                    adminUser.getUsername(), report.getReportedUser().getUsername(), reportId);
        }

        long totalReports = reportRepository.countByReportedUserId(saved.getReportedUser().getId());
        ReportDetailResponse response = reportMapper.toReportDetailResponse(saved);
        response.setReportedUserTotalReports(totalReports);

        log.info("Admin '{}' resolved report id: {} with status: {}", adminUser.getUsername(), reportId, request.getStatus());
        return response;
    }

    @Override
    @Transactional
    public void deleteReport(Long reportId) {
        if (!reportRepository.existsById(reportId)) {
            throw new ResourceNotFoundException(Translator.tolocale(ERROR_REPORT_NOT_FOUND_STRING));
        }
        reportRepository.deleteById(reportId);
        log.info("Report id: {} was deleted", reportId);
    }

    @Override
    @Transactional(readOnly = true)
    public ReportStatisticsResponse getReportStatistics() {
        return ReportStatisticsResponse.builder()
                .totalReports(reportRepository.count())
                .pendingReports(reportRepository.countByStatus(ReportStatus.PENDING))
                .resolvedReports(reportRepository.countByStatus(ReportStatus.RESOLVED))
                .dismissedReports(reportRepository.countByStatus(ReportStatus.DISMISSED))
                .build();
    }

    private UserEntity resolveReportedUser(CreateReportRequest request) {
        if (request.getReportedUsername() != null && !request.getReportedUsername().isBlank()) {
            return userRepository.findByUsername(request.getReportedUsername())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            Translator.tolocale(ERROR_USER_NOT_FOUND_STRING)));
        } else if (request.getReportedUserId() != null) {
            return userRepository.findById(request.getReportedUserId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            Translator.tolocale(ERROR_USER_NOT_FOUND_STRING)));
        } else {
            throw new InvalidDataException(Translator.tolocale(VALID_REPORT_TARGET_REQUIRED_STRING));
        }
    }

    private <T> PageResponse<T> buildPageResponse(Page<T> page) {
        return PageResponse.<T>builder()
                .content(page.getContent())
                .pageNo(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    private Pageable buildPageable(int pageNo, int pageSize, String... sorts) {
        List<Sort.Order> orders = new ArrayList<>();
        if (sorts != null) {
            Arrays.stream(sorts)
                    .map(this::parseSortOrder)
                    .flatMap(Optional::stream)
                    .forEach(orders::add);
        }

        if (orders.isEmpty()) {
            orders.add(new Sort.Order(Sort.Direction.DESC, ID_STRING));
        }
        return PageRequest.of(pageNo, pageSize, Sort.by(orders));
    }

    private Optional<Sort.Order> parseSortOrder(String sortBy) {
        if (sortBy == null) {
            return Optional.empty();
        }
        String[] parts = sortBy.split(DELIMITE_STRING, 2);
        if (parts.length == 2 && !parts[0].isEmpty() && ALLOWED_SORT_FIELDS.contains(parts[0])) {
            Sort.Direction direction = parts[1].equalsIgnoreCase(ASC_STRING) ? Sort.Direction.ASC : Sort.Direction.DESC;
            return Optional.of(new Sort.Order(direction, parts[0]));
        }
        return Optional.empty();
    }
}
