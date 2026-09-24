package com.web.backend.controller;

import com.web.backend.config.localresolverconfig.Translator;
import com.web.backend.controller.request.CreateReportRequest;
import com.web.backend.controller.response.ApiResponse;
import com.web.backend.controller.response.PageResponse;
import com.web.backend.controller.response.ReportResponse;
import com.web.backend.model.postgres.UserEntity;
import com.web.backend.ratelimit.LimitType;
import com.web.backend.ratelimit.RateLimit;
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

@Tag(name = "Report Controller")
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Slf4j(topic = "REPORT-CONTROLLER")
public class ReportController {

    private final ReportService reportService;

    private static final String SUCCESS_REPORT_CREATED_STRING = "success.report.created";
    private static final String SUCCESS_REPORT_CANCELLED_STRING = "success.report.cancelled";
    private static final String SUCCESS_SYS_OPERATION_STRING = "success.sys.operation";

    @Operation(summary = "Submit a user report", description = "Allows an authenticated user to report another user")
    @RateLimit(key = "report_create", limit = 10, period = 60, type = LimitType.USER)
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ReportResponse>> createReport(
            Authentication auth,
            @Valid @RequestBody CreateReportRequest request) {
        UserEntity currentUser = (UserEntity) auth.getPrincipal();
        ReportResponse response = reportService.createReport(currentUser, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                HttpStatus.CREATED.value(),
                Translator.tolocale(SUCCESS_REPORT_CREATED_STRING),
                response));
    }

    @Operation(summary = "Get current user's submitted reports")
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PageResponse<ReportResponse>>> getMyReports(
            Authentication auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "desc") String sortDir) {
        UserEntity currentUser = (UserEntity) auth.getPrincipal();
        PageResponse<ReportResponse> reports = reportService.getMyReports(currentUser, page, size, sortDir);

        return ResponseEntity.ok(ApiResponse.success(
                HttpStatus.OK.value(),
                Translator.tolocale(SUCCESS_SYS_OPERATION_STRING),
                reports));
    }

    @Operation(summary = "Cancel a pending report")
    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> cancelReport(
            Authentication auth,
            @PathVariable Long id) {
        UserEntity currentUser = (UserEntity) auth.getPrincipal();
        reportService.cancelReport(currentUser, id);

        return ResponseEntity.ok(ApiResponse.success(
                HttpStatus.OK.value(),
                Translator.tolocale(SUCCESS_REPORT_CANCELLED_STRING),
                null));
    }
}
