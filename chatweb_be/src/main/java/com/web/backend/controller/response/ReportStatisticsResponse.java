package com.web.backend.controller.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportStatisticsResponse {
    private long totalReports;
    private long pendingReports;
    private long resolvedReports;
    private long dismissedReports;
}
