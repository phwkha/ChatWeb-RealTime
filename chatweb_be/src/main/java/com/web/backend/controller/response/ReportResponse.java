package com.web.backend.controller.response;

import java.time.Instant;

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
public class ReportResponse {
    private Long id;
    private UserSummaryResponse reporter;
    private UserSummaryResponse reportedUser;
    private ReportReason reason;
    private String details;
    private ReportStatus status;
    private String resolutionNote;
    private UserSummaryResponse resolvedBy;
    private Instant resolvedAt;
    private Instant createdAt;
    private Instant updatedAt;
}
