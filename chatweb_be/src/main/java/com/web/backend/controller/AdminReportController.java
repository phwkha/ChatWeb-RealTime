package com.web.backend.controller;

import com.web.backend.config.localresolverconfig.Translator;
import com.web.backend.controller.request.AdminReportSearchRequest;
import com.web.backend.controller.request.ResolveReportRequest;
import com.web.backend.controller.response.ApiResponse;
import com.web.backend.controller.response.PageResponse;
import com.web.backend.controller.response.ReportDetailResponse;
import com.web.backend.controller.response.ReportResponse;
import com.web.backend.controller.response.ReportStatisticsResponse;
import com.web.backend.model.postgres.UserEntity;
import com.web.backend.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin Report Controller")
@RestController
@RequestMapping("/api/admin/reports")
@RequiredArgsConstructor
@Slf4j(topic = "ADMIN-REPORT-CONTROLLER")
public class AdminReportController {

    private final ReportService reportService;

    private static final String SUCCESS_REPORT_RESOLVED = "success.report.resolved";
    private static final String SUCCESS_REPORT_DELETED = "success.report.deleted";
    private static final String SUCCESS_SYS_OPERATION = "success.sys.operation";

    @Operation(summary = "Search and list reports for admin")
    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN_VIEW_REPORTS')")
    public ResponseEntity<ApiResponse<PageResponse<ReportResponse>>> getReports(
            @ModelAttribute AdminReportSearchRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String... sorts) {
        PageResponse<ReportResponse> reports = reportService.getReportsForAdmin(request, page, size, sorts);

        return ResponseEntity.ok(ApiResponse.success(
                HttpStatus.OK.value(),
                Translator.tolocale(SUCCESS_SYS_OPERATION),
                reports));
    }

    @Operation(summary = "Get report statistics for admin dashboard")
    @GetMapping("/statistics")
    @PreAuthorize("hasAuthority('ADMIN_VIEW_REPORTS')")
    public ResponseEntity<ApiResponse<ReportStatisticsResponse>> getStatistics() {
        ReportStatisticsResponse statistics = reportService.getReportStatistics();

        return ResponseEntity.ok(ApiResponse.success(
                HttpStatus.OK.value(),
                Translator.tolocale(SUCCESS_SYS_OPERATION),
                statistics));
    }

    @Operation(summary = "Get report details by id")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN_VIEW_REPORTS')")
    public ResponseEntity<ApiResponse<ReportDetailResponse>> getReportById(@PathVariable Long id) {
        ReportDetailResponse report = reportService.getReportById(id);

        return ResponseEntity.ok(ApiResponse.success(
                HttpStatus.OK.value(),
                Translator.tolocale(SUCCESS_SYS_OPERATION),
                report));
    }

    @Operation(summary = "Resolve or dismiss a report")
    @PutMapping("/{id}/resolve")
    @PreAuthorize("hasAuthority('ADMIN_RESOLVE_REPORTS')")
    public ResponseEntity<ApiResponse<ReportDetailResponse>> resolveReport(
            Authentication auth,
            @PathVariable Long id,
            @Valid @RequestBody ResolveReportRequest request) {
        UserEntity adminUser = (UserEntity) auth.getPrincipal();
        ReportDetailResponse response = reportService.resolveReport(adminUser, id, request);

        return ResponseEntity.ok(ApiResponse.success(
                HttpStatus.OK.value(),
                Translator.tolocale(SUCCESS_REPORT_RESOLVED),
                response));
    }

    @Operation(summary = "Delete a report permanently")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN_DELETE_REPORTS')")
    public ResponseEntity<ApiResponse<Void>> deleteReport(@PathVariable Long id) {
        reportService.deleteReport(id);

        return ResponseEntity.ok(ApiResponse.success(
                HttpStatus.OK.value(),
                Translator.tolocale(SUCCESS_REPORT_DELETED),
                null));
    }
}
