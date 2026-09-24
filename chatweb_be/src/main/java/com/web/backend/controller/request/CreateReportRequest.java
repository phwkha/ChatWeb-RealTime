package com.web.backend.controller.request;

import com.web.backend.common.ReportReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateReportRequest {

    private Long reportedUserId;

    private String reportedUsername;

    @NotNull(message = "{valid.report_reason_required}")
    private ReportReason reason;

    @Size(max = 1000, message = "{valid.report_details_too_long}")
    private String details;
}
