package com.web.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.web.backend.common.NotificationsType;
import com.web.backend.config.localresolverconfig.Translator;
import com.web.backend.controller.response.CursorResponse;
import com.web.backend.controller.response.NotificationResponse;
import com.web.backend.exception.custom.ResourceNotFoundException;
import com.web.backend.model.postgres.UserEntity;
import com.web.backend.repository.NotificationRepository;
import com.web.backend.service.impl.NotificationServiceImpl;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    private UserEntity testUser;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messageSource = mock(ResourceBundleMessageSource.class);
        lenient().when(messageSource.getMessage(anyString(), any(), any())).thenReturn("Mocked Error Message");
        Translator.setStaticMessageSource(messageSource);

        testUser = new UserEntity();
        testUser.setId(100L);
        testUser.setUsername("testuser");

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    // ==========================================
    // getNotifications Tests
    // ==========================================

    @Test
    void getNotifications_HappyPath_NoCursor() {
        List<NotificationResponse> mockList = new ArrayList<>();
        Instant now = Instant.now();
        mockList.add(NotificationResponse.builder()
                .id(1L)
                .type(NotificationsType.FRIEND_REQUEST)
                .content("Friend request")
                .isRead(false)
                .createdAt(now)
                .build());

        when(notificationRepository.findNotificationsByCursor(eq(100L), eq(null), any(Pageable.class)))
                .thenReturn(mockList);

        CursorResponse<NotificationResponse> result = notificationService.getNotifications(testUser, null, 20);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo(1L);
        assertThat(result.isHasMore()).isFalse();
        assertThat(result.getNextCursor()).isNull();
    }

    @Test
    void getNotifications_WithValidCursor_HasMore() {
        Instant cursorTime = Instant.parse("2026-09-20T10:00:00Z");
        Instant item1Time = Instant.parse("2026-09-20T09:00:00Z");
        Instant item2Time = Instant.parse("2026-09-20T08:00:00Z");
        Instant item3Time = Instant.parse("2026-09-20T07:00:00Z");

        // Requested size is 2, repository returns 3 items (size + 1)
        List<NotificationResponse> mockList = new ArrayList<>(List.of(
                NotificationResponse.builder().id(1L).createdAt(item1Time).build(),
                NotificationResponse.builder().id(2L).createdAt(item2Time).build(),
                NotificationResponse.builder().id(3L).createdAt(item3Time).build()));

        when(notificationRepository.findNotificationsByCursor(eq(100L), eq(cursorTime), any(Pageable.class)))
                .thenReturn(mockList);

        CursorResponse<NotificationResponse> result = notificationService.getNotifications(testUser,
                cursorTime.toString(), 2);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.isHasMore()).isTrue();
        assertThat(result.getNextCursor()).isEqualTo(item2Time.toString());
    }

    @Test
    void getNotifications_InvalidCursor_FallsBackToFirstPage() {
        List<NotificationResponse> mockList = new ArrayList<>();
        when(notificationRepository.findNotificationsByCursor(eq(100L), eq(null), any(Pageable.class)))
                .thenReturn(mockList);

        CursorResponse<NotificationResponse> result = notificationService.getNotifications(testUser,
                "invalid-timestamp", 20);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEmpty();
        verify(notificationRepository).findNotificationsByCursor(eq(100L), eq(null), any(Pageable.class));
    }

    @Test
    void getNotifications_SizeGreaterThanMax_UsesDefaultSize() {
        when(notificationRepository.findNotificationsByCursor(eq(100L), eq(null), any(Pageable.class)))
                .thenAnswer(inv -> {
                    Pageable pageable = inv.getArgument(2);
                    // DEFAULT_PAGE_SIZE = 20, so pageable should request pageSize + 1 = 21
                    assertThat(pageable.getPageSize()).isEqualTo(21);
                    return new ArrayList<NotificationResponse>();
                });

        CursorResponse<NotificationResponse> result = notificationService.getNotifications(testUser, null, 999);

        assertThat(result).isNotNull();
    }

    // ==========================================
    // getUnreadNotificationCounts Tests
    // ==========================================

    @Test
    void getUnreadNotificationCounts_CacheHit_ReturnsCachedValue() {
        when(valueOperations.get("notif:unread:testuser")).thenReturn(5L);

        Long count = notificationService.getUnreadNotificationCounts(testUser);

        assertThat(count).isEqualTo(5L);
        verify(notificationRepository, never()).countByRecipientIdAndIsReadFalse(any());
    }

    @Test
    void getUnreadNotificationCounts_CacheMiss_QueriesDbAndSetsCache() {
        when(valueOperations.get("notif:unread:testuser")).thenReturn(null);
        when(notificationRepository.countByRecipientIdAndIsReadFalse(100L)).thenReturn(8L);

        Long count = notificationService.getUnreadNotificationCounts(testUser);

        assertThat(count).isEqualTo(8L);
        verify(valueOperations).set("notif:unread:testuser", 8L, Duration.ofMinutes(10));
    }

    // ==========================================
    // markNotificationAsRead Tests
    // ==========================================

    @Test
    void markNotificationAsRead_Success_UpdatesAndEvictsCache() {
        when(notificationRepository.markAsReadByIdAndRecipientId(1L, 100L)).thenReturn(1);

        notificationService.markNotificationAsRead(testUser, 1L);

        verify(notificationRepository).markAsReadByIdAndRecipientId(1L, 100L);
        verify(redisTemplate).delete("notif:unread:testuser");
    }

    @Test
    void markNotificationAsRead_NotFoundOrNotOwner_ThrowsResourceNotFoundException() {
        when(notificationRepository.markAsReadByIdAndRecipientId(999L, 100L)).thenReturn(0);

        assertThatThrownBy(() -> notificationService.markNotificationAsRead(testUser, 999L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(redisTemplate, never()).delete(anyString());
    }

    // ==========================================
    // markAllNotificationsAsRead Tests
    // ==========================================

    @Test
    void markAllNotificationsAsRead_Success_UpdatesAndEvictsCache_ReturnsCount() {
        when(notificationRepository.markAllAsReadByRecipientId(100L)).thenReturn(7);

        int updatedCount = notificationService.markAllNotificationsAsRead(testUser);

        assertThat(updatedCount).isEqualTo(7);
        verify(notificationRepository).markAllAsReadByRecipientId(100L);
        verify(redisTemplate).delete("notif:unread:testuser");
    }
}
