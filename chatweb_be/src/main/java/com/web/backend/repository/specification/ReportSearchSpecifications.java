package com.web.backend.repository.specification;

import com.web.backend.common.ReportReason;
import com.web.backend.common.ReportStatus;
import com.web.backend.model.postgres.ReportEntity;
import com.web.backend.model.postgres.UserEntity;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

public class ReportSearchSpecifications {

    private static final String STATUS_STRING = "status";
    private static final String REASON_STRING = "reason";
    private static final String REPORTER_STRING = "reporter";
    private static final String REPORTED_USER_STRING = "reportedUser";
    private static final String USERNAME_STRING = "username";
    private static final String PERCENT_STRING = "%";

    private ReportSearchSpecifications() {
    }

    public static Specification<ReportEntity> hasStatus(ReportStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get(STATUS_STRING), status);
    }

    public static Specification<ReportEntity> hasReason(ReportReason reason) {
        return (root, query, cb) -> reason == null ? null : cb.equal(root.get(REASON_STRING), reason);
    }

    public static Specification<ReportEntity> containsKeyword(String keyword) {
        return (root, query, cb) -> {
            if (keyword == null || keyword.isBlank()) {
                return null;
            }
            String pattern = PERCENT_STRING + keyword.toLowerCase().trim() + PERCENT_STRING;
            Join<ReportEntity, UserEntity> reporterJoin = root.join(REPORTER_STRING, JoinType.LEFT);
            Join<ReportEntity, UserEntity> reportedUserJoin = root.join(REPORTED_USER_STRING, JoinType.LEFT);
            return cb.or(
                    cb.like(cb.lower(reporterJoin.get(USERNAME_STRING)), pattern),
                    cb.like(cb.lower(reportedUserJoin.get(USERNAME_STRING)), pattern));
        };
    }
}
