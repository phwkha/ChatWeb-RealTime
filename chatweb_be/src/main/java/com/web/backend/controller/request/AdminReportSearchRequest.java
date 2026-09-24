package com.web.backend.controller.request;

import com.web.backend.common.ReportReason;
import com.web.backend.common.ReportStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminReportSearchRequest {
    private String keyword;
    private ReportStatus status;
    private ReportReason reason;
}
