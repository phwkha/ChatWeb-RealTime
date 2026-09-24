package com.web.backend.service;

import com.web.backend.controller.request.AdminReportSearchRequest;
import com.web.backend.controller.request.CreateReportRequest;
import com.web.backend.controller.request.ResolveReportRequest;
import com.web.backend.controller.response.PageResponse;
import com.web.backend.controller.response.ReportDetailResponse;
import com.web.backend.controller.response.ReportResponse;
import com.web.backend.controller.response.ReportStatisticsResponse;
import com.web.backend.model.postgres.UserEntity;

public interface ReportService {

    ReportResponse createReport(UserEntity currentUser, CreateReportRequest request);

    PageResponse<ReportResponse> getMyReports(UserEntity currentUser, int pageNo, int pageSize, String sortDir);

    void cancelReport(UserEntity currentUser, Long reportId);

    PageResponse<ReportResponse> getReportsForAdmin(AdminReportSearchRequest request, int pageNo, int pageSize, String... sorts);

    ReportDetailResponse getReportById(Long reportId);

    ReportDetailResponse resolveReport(UserEntity adminUser, Long reportId, ResolveReportRequest request);

    void deleteReport(Long reportId);

    ReportStatisticsResponse getReportStatistics();
}
