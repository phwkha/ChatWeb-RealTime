package com.web.backend.repository;

import com.web.backend.common.ReportStatus;
import com.web.backend.model.postgres.ReportEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface ReportRepository extends JpaRepository<ReportEntity, Long>, JpaSpecificationExecutor<ReportEntity> {

    @EntityGraph(attributePaths = { "reporter", "reportedUser", "resolvedBy" })
    Optional<ReportEntity> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = { "reporter", "reportedUser", "resolvedBy" })
    Page<ReportEntity> findByReporterId(Long reporterId, Pageable pageable);

    boolean existsByReporterIdAndReportedUserIdAndStatus(Long reporterId, Long reportedUserId, ReportStatus status);

    long countByReportedUserId(Long reportedUserId);

    long countByStatus(ReportStatus status);
}
