package com.web.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.Optional;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.web.backend.common.FriendshipStatus;
import com.web.backend.common.UserStatus;
import com.web.backend.config.localresolverconfig.Translator;
import com.web.backend.controller.response.UserSummaryResponse;
import com.web.backend.controller.response.PageResponse;
import com.web.backend.exception.custom.AccessForbiddenException;
import com.web.backend.exception.custom.InvalidDataException;
import com.web.backend.exception.custom.ResourceConflictException;
import com.web.backend.exception.custom.ResourceNotFoundException;
import com.web.backend.kafka.payload.FriendPayload;
import com.web.backend.mapper.UserMapper;
import com.web.backend.model.postgres.FriendshipEntity;
import com.web.backend.model.postgres.UserEntity;
import com.web.backend.repository.FriendshipRepository;
import com.web.backend.repository.UserRepository;
import com.web.backend.service.impl.FriendServiceImpl;

@ExtendWith(MockitoExtension.class)
class FriendServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private FriendshipRepository friendshipRepository;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private UserMapper userMapper;
    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private FriendServiceImpl friendService;

    private UserEntity userA;
    private UserEntity userB;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messageSource = mock(ResourceBundleMessageSource.class);
        lenient().when(messageSource.getMessage(anyString(), any(), any())).thenReturn("Mocked Error Message");
        Translator.setStaticMessageSource(messageSource);

        userA = new UserEntity();
        userA.setUsername("userA");
        userA.setUserStatus(UserStatus.ACTIVE);

        userB = new UserEntity();
        userB.setUsername("userB");
        userB.setUserStatus(UserStatus.ACTIVE);
    }

    // =====================================
    // SEND FRIEND REQUEST
    // =====================================
    @Test
    void testSendFriendRequest_SelfAdd() {
        assertThrows(InvalidDataException.class, () -> friendService.sendFriendRequest("userA", "userA"));
    }

    @Test
    void testSendFriendRequest_TargetLocked() {
        userB.setUserStatus(UserStatus.LOCKED);
        when(userRepository.findByUsername("userA")).thenReturn(Optional.of(userA));
        when(userRepository.findByUsername("userB")).thenReturn(Optional.of(userB));

        assertThrows(AccessForbiddenException.class, () -> friendService.sendFriendRequest("userA", "userB"));
    }

    @Test
    void testSendFriendRequest_Success() {
        when(userRepository.findByUsername("userA")).thenReturn(Optional.of(userA));
        when(userRepository.findByUsername("userB")).thenReturn(Optional.of(userB));
        when(friendshipRepository.findByUsers(userA, userB)).thenReturn(Optional.empty());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        friendService.sendFriendRequest("userA", "userB");

        verify(friendshipRepository).save(any(FriendshipEntity.class));
        verify(valueOperations).set(eq("relation:userA:userB"), eq("PENDING:userA"), eq(Duration.ofDays(1)));
        verify(eventPublisher).publishEvent(any(FriendPayload.class));
    }

    @Test
    void testSendFriendRequest_DataIntegrityViolation() {
        when(userRepository.findByUsername("userA")).thenReturn(Optional.of(userA));
        when(userRepository.findByUsername("userB")).thenReturn(Optional.of(userB));
        when(friendshipRepository.findByUsers(userA, userB)).thenReturn(Optional.empty());
        when(friendshipRepository.save(any(FriendshipEntity.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("Duplicate key"));

        assertThrows(ResourceConflictException.class, () -> friendService.sendFriendRequest("userA", "userB"));
    }

    // =====================================
    // ACCEPT FRIEND REQUEST
    // =====================================
    @Test
    void testAcceptFriendRequest_Success() {
        when(userRepository.findByUsername("userA")).thenReturn(Optional.of(userA));
        when(userRepository.findByUsername("userB")).thenReturn(Optional.of(userB));

        FriendshipEntity pendingReq = new FriendshipEntity();
        pendingReq.setStatus(FriendshipStatus.PENDING);
        when(friendshipRepository.findByUsers(userA, userB)).thenReturn(Optional.of(pendingReq));

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        friendService.acceptFriendRequest("userA", "userB");

        assertEquals(FriendshipStatus.ACCEPTED, pendingReq.getStatus());
        verify(friendshipRepository).save(pendingReq);
        verify(valueOperations).set(eq("relation:userA:userB"), eq("ACCEPTED"), eq(Duration.ofDays(7)));
        verify(eventPublisher).publishEvent(any(FriendPayload.class));
    }

    @Test
    void testAcceptFriendRequest_AlreadyFriends() {
        when(userRepository.findByUsername("userA")).thenReturn(Optional.of(userA));
        when(userRepository.findByUsername("userB")).thenReturn(Optional.of(userB));

        FriendshipEntity acceptedReq = new FriendshipEntity();
        acceptedReq.setStatus(FriendshipStatus.ACCEPTED);
        when(friendshipRepository.findByUsers(userA, userB)).thenReturn(Optional.of(acceptedReq));

        assertThrows(ResourceConflictException.class, () -> friendService.acceptFriendRequest("userA", "userB"));
    }

    // =====================================
    // DELETE / BLOCK / IS_FRIEND
    // =====================================
    @Test
    void testDeleteFriendship_Success_Unfriend() {
        when(userRepository.findByUsername("userA")).thenReturn(Optional.of(userA));
        when(userRepository.findByUsername("userB")).thenReturn(Optional.of(userB));

        FriendshipEntity f = new FriendshipEntity();
        f.setStatus(FriendshipStatus.ACCEPTED);
        f.setRequester(userA);
        f.setAddressee(userB);

        when(friendshipRepository.findByUsers(userA, userB)).thenReturn(Optional.of(f));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        friendService.deleteFriendship("userA", "userB");

        verify(friendshipRepository).delete(f);
        verify(valueOperations).set(eq("relation:userA:userB"), eq("NONE"), eq(Duration.ofHours(1)));
        verify(eventPublisher).publishEvent(any(FriendPayload.class));
    }

    @Test
    void testBlockUser() {
        when(userRepository.findByUsername("userA")).thenReturn(Optional.of(userA));
        when(userRepository.findByUsername("userB")).thenReturn(Optional.of(userB));
        when(friendshipRepository.findByUsers(userA, userB)).thenReturn(Optional.empty());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        friendService.blockUser("userA", "userB");

        verify(friendshipRepository).save(argThat(f -> f.getStatus() == FriendshipStatus.BLOCKED));
        verify(valueOperations).set(eq("relation:userA:userB"), eq("BLOCKED:userA"), eq(Duration.ofDays(7)));
    }

    @Test
    void testIsFriend_RedisHit() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("relation:userA:userB")).thenReturn("ACCEPTED");

        assertTrue(friendService.isFriend("userA", "userB"));
        verify(friendshipRepository, never()).findByUsernames(anyString(), anyString());
    }

    @Test
    void testIsFriend_DbFallback() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("relation:userA:userB")).thenReturn(null);

        FriendshipEntity f = new FriendshipEntity();
        f.setStatus(FriendshipStatus.ACCEPTED);
        when(friendshipRepository.findByUsernames("userA", "userB")).thenReturn(Optional.of(f));

        assertTrue(friendService.isFriend("userA", "userB"));
        verify(valueOperations).set(eq("relation:userA:userB"), eq("ACCEPTED"), eq(Duration.ofDays(7)));
    }

    // =====================================
    // PAGINATION QUERIES
    // =====================================
    @Test
    void testGetFriendsList() {
        when(userRepository.existsByUsername("userA")).thenReturn(true);

        Page<UserEntity> page = new PageImpl<>(List.of(userB));

        when(friendshipRepository.findFriendsByUsername(eq("userA"), any(Pageable.class))).thenReturn(page);

        UserSummaryResponse response = UserSummaryResponse.builder().username("userB").build();
        when(userMapper.toUserSummaryResponse(userB)).thenReturn(response);

        PageResponse<UserSummaryResponse> result = friendService.getFriendsList("userA", 0, 10, "asc");

        assertEquals(1, result.getContent().size());
        assertEquals("userB", result.getContent().get(0).getUsername());
    }

    @Test
    void testSendFriendRequest_AddresseeInactive() {
        userB.setUserStatus(UserStatus.INACTIVE);
        when(userRepository.findByUsername("userA")).thenReturn(Optional.of(userA));
        when(userRepository.findByUsername("userB")).thenReturn(Optional.of(userB));
        assertThrows(AccessForbiddenException.class, () -> friendService.sendFriendRequest("userA", "userB"));
    }

    @Test
    void testSendFriendRequest_ExistingRelation_Blocked() {
        when(userRepository.findByUsername("userA")).thenReturn(Optional.of(userA));
        when(userRepository.findByUsername("userB")).thenReturn(Optional.of(userB));
        FriendshipEntity f = new FriendshipEntity();
        f.setStatus(FriendshipStatus.BLOCKED);
        when(friendshipRepository.findByUsers(userA, userB)).thenReturn(Optional.of(f));

        assertThrows(AccessForbiddenException.class, () -> friendService.sendFriendRequest("userA", "userB"));
    }

    @Test
    void testSendFriendRequest_ExistingRelation_PendingOrAccepted() {
        when(userRepository.findByUsername("userA")).thenReturn(Optional.of(userA));
        when(userRepository.findByUsername("userB")).thenReturn(Optional.of(userB));
        FriendshipEntity f = new FriendshipEntity();
        f.setStatus(FriendshipStatus.PENDING);
        when(friendshipRepository.findByUsers(userA, userB)).thenReturn(Optional.of(f));

        assertThrows(ResourceConflictException.class, () -> friendService.sendFriendRequest("userA", "userB"));
    }

    @Test
    void testAcceptFriendRequest_RequesterInactive() {
        userA.setUserStatus(UserStatus.INACTIVE);
        when(userRepository.findByUsername("userB")).thenReturn(Optional.of(userB));
        when(userRepository.findByUsername("userA")).thenReturn(Optional.of(userA));
        assertThrows(AccessForbiddenException.class, () -> friendService.acceptFriendRequest("userB", "userA"));
    }

    @Test
    void testAcceptFriendRequest_RequesterLocked() {
        userA.setUserStatus(UserStatus.LOCKED);
        when(userRepository.findByUsername("userB")).thenReturn(Optional.of(userB));
        when(userRepository.findByUsername("userA")).thenReturn(Optional.of(userA));
        assertThrows(AccessForbiddenException.class, () -> friendService.acceptFriendRequest("userB", "userA"));
    }

    @Test
    void testAcceptFriendRequest_NotFound() {
        when(userRepository.findByUsername("userB")).thenReturn(Optional.of(userB));
        when(userRepository.findByUsername("userA")).thenReturn(Optional.of(userA));
        when(friendshipRepository.findByUsers(userB, userA)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> friendService.acceptFriendRequest("userB", "userA"));
    }

    @Test
    void testGetSentRequests() {
        when(userRepository.findByUsername("userA")).thenReturn(Optional.of(userA));
        FriendshipEntity f = new FriendshipEntity();
        f.setAddressee(userB);
        Page<FriendshipEntity> page = new PageImpl<>(List.of(f));
        when(friendshipRepository.findByRequesterAndStatus(eq(userA), eq(FriendshipStatus.PENDING),
                any(Pageable.class))).thenReturn(page);
        when(userMapper.toUserSummaryResponse(userB)).thenReturn(mock(UserSummaryResponse.class));

        PageResponse<UserSummaryResponse> res = friendService.getSentRequests("userA", 0, 10, "asc");
        assertEquals(1, res.getTotalElements());
    }

    @Test
    void testGetPendingRequests() {
        when(userRepository.findByUsername("userA")).thenReturn(Optional.of(userA));
        FriendshipEntity f = new FriendshipEntity();
        f.setRequester(userB);
        Page<FriendshipEntity> page = new PageImpl<>(List.of(f));
        when(friendshipRepository.findByAddresseeAndStatus(eq(userA), eq(FriendshipStatus.PENDING),
                any(Pageable.class))).thenReturn(page);
        when(userMapper.toUserSummaryResponse(userB)).thenReturn(mock(UserSummaryResponse.class));

        PageResponse<UserSummaryResponse> res = friendService.getPendingRequests("userA", 0, 10, "desc");
        assertEquals(1, res.getTotalElements());
    }

    @Test
    void testDeleteFriendship_NotFound() {
        when(userRepository.findByUsername("userA")).thenReturn(Optional.of(userA));
        when(userRepository.findByUsername("userB")).thenReturn(Optional.of(userB));
        when(friendshipRepository.findByUsers(userA, userB)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> friendService.deleteFriendship("userA", "userB"));
    }

    @Test
    void testDeleteFriendship_NotAccepted_IsRequester() {
        when(userRepository.findByUsername("userA")).thenReturn(Optional.of(userA));
        when(userRepository.findByUsername("userB")).thenReturn(Optional.of(userB));

        FriendshipEntity f = new FriendshipEntity();
        f.setStatus(FriendshipStatus.PENDING);
        f.setRequester(userA);
        f.setAddressee(userB);
        when(friendshipRepository.findByUsers(userA, userB)).thenReturn(Optional.of(f));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        friendService.deleteFriendship("userA", "userB");
        verify(valueOperations).set(eq("relation:userA:userB"), eq("NONE"), eq(Duration.ofHours(1)));
        verify(eventPublisher).publishEvent(any(FriendPayload.class));
    }

    @Test
    void testDeleteFriendship_NotAccepted_NotRequester() {
        when(userRepository.findByUsername("userB")).thenReturn(Optional.of(userB));
        when(userRepository.findByUsername("userA")).thenReturn(Optional.of(userA));

        FriendshipEntity f = new FriendshipEntity();
        f.setStatus(FriendshipStatus.PENDING);
        f.setRequester(userA); // userA is requester
        f.setAddressee(userB); // userB is current user
        when(friendshipRepository.findByUsers(userB, userA)).thenReturn(Optional.of(f));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        friendService.deleteFriendship("userB", "userA");
        verify(valueOperations).set(eq("relation:userA:userB"), eq("NONE"), eq(Duration.ofHours(1)));
        verify(eventPublisher).publishEvent(any(FriendPayload.class));
    }

    @Test
    void testIsFriend_RedisHit_False() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("relation:userA:userB")).thenReturn("NONE");

        assertFalse(friendService.isFriend("userA", "userB"));
        verify(friendshipRepository, never()).findByUsernames(anyString(), anyString());
    }

    @Test
    void testIsFriend_DbFallback_Pending() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("relation:userA:userB")).thenReturn(null);

        FriendshipEntity f = new FriendshipEntity();
        f.setStatus(FriendshipStatus.PENDING);
        f.setRequester(userA);
        f.setAddressee(userB);
        when(friendshipRepository.findByUsernames("userA", "userB")).thenReturn(Optional.of(f));

        assertFalse(friendService.isFriend("userA", "userB"));
        verify(valueOperations).set(eq("relation:userA:userB"), eq("PENDING:userA"), eq(Duration.ofDays(1)));
    }

    @Test
    void testIsFriend_DbFallback_Blocked() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("relation:userA:userB")).thenReturn(null);

        FriendshipEntity f = new FriendshipEntity();
        f.setStatus(FriendshipStatus.BLOCKED);
        f.setRequester(userA);
        f.setAddressee(userB);
        when(friendshipRepository.findByUsernames("userA", "userB")).thenReturn(Optional.of(f));

        assertFalse(friendService.isFriend("userA", "userB"));
        verify(valueOperations).set(eq("relation:userA:userB"), eq("BLOCKED:userA"), eq(Duration.ofDays(7)));
    }

    @Test
    void testIsFriend_DbFallback_False_AntiPenetration() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("relation:userA:userB")).thenReturn(null);
        when(friendshipRepository.findByUsernames("userA", "userB")).thenReturn(Optional.empty());

        assertFalse(friendService.isFriend("userA", "userB"));
        verify(valueOperations).set(eq("relation:userA:userB"), eq("NONE"), eq(Duration.ofHours(1)));
    }

    // =====================================
    // UNBLOCK & BLOCKED LIST TESTS
    // =====================================
    @Test
    void testUnblockUser_Success() {
        when(userRepository.findByUsername("userA")).thenReturn(Optional.of(userA));
        when(userRepository.findByUsername("userB")).thenReturn(Optional.of(userB));

        FriendshipEntity f = new FriendshipEntity();
        f.setStatus(FriendshipStatus.BLOCKED);
        f.setRequester(userA);
        f.setAddressee(userB);

        when(friendshipRepository.findByUsers(userA, userB)).thenReturn(Optional.of(f));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        friendService.unblockUser("userA", "userB");

        verify(friendshipRepository).delete(f);
        verify(valueOperations).set(eq("relation:userA:userB"), eq("NONE"), eq(Duration.ofHours(1)));
    }

    @Test
    void testUnblockUser_RelationNotFound_ThrowsException() {
        when(userRepository.findByUsername("userA")).thenReturn(Optional.of(userA));
        when(userRepository.findByUsername("userB")).thenReturn(Optional.of(userB));
        when(friendshipRepository.findByUsers(userA, userB)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> friendService.unblockUser("userA", "userB"));
    }

    @Test
    void testUnblockUser_NotBlocked_ThrowsException() {
        when(userRepository.findByUsername("userA")).thenReturn(Optional.of(userA));
        when(userRepository.findByUsername("userB")).thenReturn(Optional.of(userB));

        FriendshipEntity f = new FriendshipEntity();
        f.setStatus(FriendshipStatus.ACCEPTED);
        f.setRequester(userA);
        f.setAddressee(userB);

        when(friendshipRepository.findByUsers(userA, userB)).thenReturn(Optional.of(f));

        assertThrows(InvalidDataException.class, () -> friendService.unblockUser("userA", "userB"));
    }

    @Test
    void testUnblockUser_NotBlocker_ThrowsException() {
        when(userRepository.findByUsername("userA")).thenReturn(Optional.of(userA));
        when(userRepository.findByUsername("userB")).thenReturn(Optional.of(userB));

        FriendshipEntity f = new FriendshipEntity();
        f.setStatus(FriendshipStatus.BLOCKED);
        f.setRequester(userB); // userB is the blocker, userA is the target
        f.setAddressee(userA);

        when(friendshipRepository.findByUsers(userA, userB)).thenReturn(Optional.of(f));

        assertThrows(InvalidDataException.class, () -> friendService.unblockUser("userA", "userB"));
    }

    @Test
    void testGetBlockedList_Success() {
        when(userRepository.findByUsername("userA")).thenReturn(Optional.of(userA));

        FriendshipEntity f = new FriendshipEntity();
        f.setRequester(userA);
        f.setAddressee(userB);
        f.setStatus(FriendshipStatus.BLOCKED);

        Page<FriendshipEntity> page = new PageImpl<>(List.of(f));
        when(friendshipRepository.findByRequesterAndStatus(eq(userA), eq(FriendshipStatus.BLOCKED), any(Pageable.class)))
                .thenReturn(page);
        when(userMapper.toUserSummaryResponse(userB)).thenReturn(mock(UserSummaryResponse.class));

        PageResponse<UserSummaryResponse> res = friendService.getBlockedList("userA", 0, 10, "desc");

        assertNotNull(res);
        assertEquals(1, res.getTotalElements());
    }
}
