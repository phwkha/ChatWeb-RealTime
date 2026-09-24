package com.web.backend.controller.request;

import com.web.backend.common.ReportStatus;
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
public class ResolveReportRequest {

    @NotNull(message = "{valid.report_status_required}")
    private ReportStatus status;

    @Size(max = 1000, message = "{valid.report_resolution_note_too_long}")
    private String resolutionNote;

    private Boolean lockReportedUser;
}
