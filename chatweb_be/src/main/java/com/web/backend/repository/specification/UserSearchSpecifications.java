package com.web.backend.repository.specification;

import com.web.backend.common.FriendshipStatus;
import com.web.backend.common.UserStatus;
import com.web.backend.model.postgres.FriendshipEntity;
import com.web.backend.model.postgres.RoleEntity;
import com.web.backend.model.postgres.UserEntity;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

public class UserSearchSpecifications {

    private static final String ROLE_STRING = "role";
    private static final String NAME_STRING = "name";
    private static final String USER_STATUS_STRING = "userStatus";
    private static final String USERNAME_STRING = "username";
    private static final String REQUESTER_STRING = "requester";
    private static final String ADDRESSEE_STRING = "addressee";
    private static final String STATUS_STRING = "status";
    private static final String EMAIL_STRING = "email";
    private static final String FIRST_NAME_STRING = "firstName";
    private static final String LAST_NAME_STRING = "lastName";
    private static final String PERCENT_STRING = "%";

    private UserSearchSpecifications() {
    }

    public static Specification<UserEntity> isRole(String roleName) {
        return (root, query, cb) -> {
            if (roleName == null || roleName.isBlank()) {
                return null;
            }
            Join<UserEntity, RoleEntity> roleJoin = root.join(ROLE_STRING, JoinType.INNER);
            return cb.equal(cb.upper(roleJoin.get(NAME_STRING)), roleName.toUpperCase());
        };
    }

    public static Specification<UserEntity> isNotStatus(UserStatus status) {
        return (root, query, cb) -> status == null ? null : cb.notEqual(root.get(USER_STATUS_STRING), status);
    }

    public static Specification<UserEntity> isNotCurrentUsername(String currentUsername) {
        return (root, query, cb) -> (currentUsername == null || currentUsername.isBlank())
                ? null
                : cb.notEqual(root.get(USERNAME_STRING), currentUsername);
    }

    public static Specification<UserEntity> notBlockedWith(String currentUsername) {
        return (root, query, cb) -> {
            if (currentUsername == null || currentUsername.isBlank()) {
                return null;
            }

            Subquery<Integer> subquery = query.subquery(Integer.class);
            Root<FriendshipEntity> f = subquery.from(FriendshipEntity.class);
            subquery.select(cb.literal(1));

            Predicate reqAndAdd = cb.and(
                    cb.equal(f.get(REQUESTER_STRING).get(USERNAME_STRING), currentUsername),
                    cb.equal(f.get(ADDRESSEE_STRING), root));
            Predicate addAndReq = cb.and(
                    cb.equal(f.get(ADDRESSEE_STRING).get(USERNAME_STRING), currentUsername),
                    cb.equal(f.get(REQUESTER_STRING), root));
            Predicate isBlocked = cb.equal(f.get(STATUS_STRING), FriendshipStatus.BLOCKED);

            subquery.where(cb.and(cb.or(reqAndAdd, addAndReq), isBlocked));
            return cb.not(cb.exists(subquery));
        };
    }

    public static Specification<UserEntity> containsKeyword(String keyword) {
        return (root, query, cb) -> {
            if (keyword == null || keyword.isBlank()) {
                return null;
            }
            String pattern = PERCENT_STRING + keyword.toLowerCase().trim() + PERCENT_STRING;
            return cb.or(
                    cb.like(cb.lower(root.get(USERNAME_STRING)), pattern),
                    cb.like(cb.lower(root.get(EMAIL_STRING)), pattern),
                    cb.like(cb.lower(root.get(FIRST_NAME_STRING)), pattern),
                    cb.like(cb.lower(root.get(LAST_NAME_STRING)), pattern));
        };
    }
}
