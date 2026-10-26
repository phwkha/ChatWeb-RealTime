package com.web.backend.repository.specification;

import com.web.backend.common.AuthProvider;
import com.web.backend.common.GenderType;
import com.web.backend.common.UserStatus;
import com.web.backend.model.postgres.FriendshipEntity;
import com.web.backend.model.postgres.RoleEntity;
import com.web.backend.model.postgres.UserEntity;
import jakarta.persistence.criteria.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.jpa.domain.Specification;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserSearchSpecificationsTest {

    @Mock
    private Root<UserEntity> root;

    @Mock
    private CriteriaQuery<?> query;

    @Mock
    private CriteriaBuilder cb;

    @Mock
    private Join<UserEntity, RoleEntity> roleJoin;

    @Mock
    private Path<Object> path;

    @Mock
    private Path<Boolean> boolPath;

    @Mock
    private Expression<String> stringExpr;

    @Mock
    private Predicate predicate;

    @Mock
    private Subquery<Integer> subquery;

    @Mock
    private Root<FriendshipEntity> friendshipRoot;

    @Test
    void testIsUserRole() {
        when(root.<UserEntity, RoleEntity>join("role", JoinType.INNER)).thenReturn(roleJoin);
        when(roleJoin.<Boolean>get("userRole")).thenReturn(boolPath);
        when(cb.isTrue(any())).thenReturn(predicate);

        Specification<UserEntity> spec = UserSearchSpecifications.isUserRole();
        Predicate result = spec.toPredicate(root, query, cb);

        assertNotNull(result);
        assertEquals(predicate, result);
        verify(root).join("role", JoinType.INNER);
        verify(roleJoin).get("userRole");
        verify(cb).isTrue(boolPath);
    }

    @Test
    void testIsRole_WhenValidRoleName() {
        when(root.<UserEntity, RoleEntity>join("role", JoinType.INNER)).thenReturn(roleJoin);
        when(roleJoin.get("name")).thenReturn(path);
        when(cb.upper(any())).thenReturn(stringExpr);
        when(cb.equal(stringExpr, "ADMIN")).thenReturn(predicate);

        Specification<UserEntity> spec = UserSearchSpecifications.isRole("admin");
        Predicate result = spec.toPredicate(root, query, cb);

        assertNotNull(result);
        assertEquals(predicate, result);
    }

    @Test
    void testIsRole_WhenNullOrBlank() {
        assertNull(UserSearchSpecifications.isRole(null).toPredicate(root, query, cb));
        assertNull(UserSearchSpecifications.isRole("").toPredicate(root, query, cb));
        assertNull(UserSearchSpecifications.isRole("   ").toPredicate(root, query, cb));
    }

    @Test
    void testHasStatus() {
        when(root.get("userStatus")).thenReturn(path);
        when(cb.equal(path, UserStatus.ACTIVE)).thenReturn(predicate);

        Predicate result = UserSearchSpecifications.hasStatus(UserStatus.ACTIVE).toPredicate(root, query, cb);
        assertEquals(predicate, result);

        assertNull(UserSearchSpecifications.hasStatus(null).toPredicate(root, query, cb));
    }

    @Test
    void testHasGender() {
        when(root.get("gender")).thenReturn(path);
        when(cb.equal(path, GenderType.MAN)).thenReturn(predicate);

        Predicate result = UserSearchSpecifications.hasGender(GenderType.MAN).toPredicate(root, query, cb);
        assertEquals(predicate, result);

        assertNull(UserSearchSpecifications.hasGender(null).toPredicate(root, query, cb));
    }

    @Test
    void testHasAuthProvider() {
        when(root.get("authProvider")).thenReturn(path);
        when(cb.equal(path, AuthProvider.LOCAL)).thenReturn(predicate);

        Predicate result = UserSearchSpecifications.hasAuthProvider(AuthProvider.LOCAL).toPredicate(root, query, cb);
        assertEquals(predicate, result);

        assertNull(UserSearchSpecifications.hasAuthProvider(null).toPredicate(root, query, cb));
    }

    @Test
    void testIsNotStatus() {
        when(root.get("userStatus")).thenReturn(path);
        when(cb.notEqual(path, UserStatus.INACTIVE)).thenReturn(predicate);

        Predicate result = UserSearchSpecifications.isNotStatus(UserStatus.INACTIVE).toPredicate(root, query, cb);
        assertEquals(predicate, result);

        assertNull(UserSearchSpecifications.isNotStatus(null).toPredicate(root, query, cb));
    }

    @Test
    void testIsNotCurrentUsername() {
        when(root.get("username")).thenReturn(path);
        when(cb.notEqual(path, "alice")).thenReturn(predicate);

        Predicate result = UserSearchSpecifications.isNotCurrentUsername("alice").toPredicate(root, query, cb);
        assertEquals(predicate, result);

        assertNull(UserSearchSpecifications.isNotCurrentUsername(null).toPredicate(root, query, cb));
        assertNull(UserSearchSpecifications.isNotCurrentUsername("").toPredicate(root, query, cb));
        assertNull(UserSearchSpecifications.isNotCurrentUsername("   ").toPredicate(root, query, cb));
    }

    @Test
    void testContainsKeyword() {
        when(root.get(anyString())).thenReturn(path);
        when(cb.lower(any())).thenReturn(stringExpr);
        when(cb.like(any(), anyString())).thenReturn(predicate);
        when(cb.or(any(), any(), any(), any())).thenReturn(predicate);

        Predicate result = UserSearchSpecifications.containsKeyword("alice").toPredicate(root, query, cb);
        assertEquals(predicate, result);

        assertNull(UserSearchSpecifications.containsKeyword(null).toPredicate(root, query, cb));
        assertNull(UserSearchSpecifications.containsKeyword("").toPredicate(root, query, cb));
        assertNull(UserSearchSpecifications.containsKeyword("   ").toPredicate(root, query, cb));
    }

    @Test
    void testContainsAdminKeyword() {
        when(root.get(anyString())).thenReturn(path);
        when(cb.lower(any())).thenReturn(stringExpr);
        when(cb.like(any(), anyString())).thenReturn(predicate);
        when(cb.or(any(), any(), any(), any(), any())).thenReturn(predicate);

        Predicate result = UserSearchSpecifications.containsAdminKeyword("alice").toPredicate(root, query, cb);
        assertEquals(predicate, result);

        assertNull(UserSearchSpecifications.containsAdminKeyword(null).toPredicate(root, query, cb));
        assertNull(UserSearchSpecifications.containsAdminKeyword("").toPredicate(root, query, cb));
        assertNull(UserSearchSpecifications.containsAdminKeyword("   ").toPredicate(root, query, cb));
    }

    @Test
    void testNotBlockedWith() {
        when(query.subquery(Integer.class)).thenReturn(subquery);
        when(subquery.from(FriendshipEntity.class)).thenReturn(friendshipRoot);
        when(friendshipRoot.get(anyString())).thenReturn(path);
        when(path.get(anyString())).thenReturn(path);
        when(cb.literal(1)).thenReturn(mock(Expression.class));
        when(cb.equal(any(), any())).thenReturn(predicate);
        when(cb.and(any(Predicate.class), any(Predicate.class))).thenReturn(predicate);
        when(cb.or(any(Predicate.class), any(Predicate.class))).thenReturn(predicate);
        when(cb.exists(subquery)).thenReturn(predicate);
        when(cb.not(predicate)).thenReturn(predicate);

        Predicate result = UserSearchSpecifications.notBlockedWith("alice").toPredicate(root, query, cb);
        assertEquals(predicate, result);

        assertNull(UserSearchSpecifications.notBlockedWith(null).toPredicate(root, query, cb));
        assertNull(UserSearchSpecifications.notBlockedWith("").toPredicate(root, query, cb));
        assertNull(UserSearchSpecifications.notBlockedWith("   ").toPredicate(root, query, cb));
    }
}
