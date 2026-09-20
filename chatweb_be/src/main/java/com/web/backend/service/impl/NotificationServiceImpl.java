package com.web.backend.service.impl;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.web.backend.config.localresolverconfig.Translator;
import com.web.backend.controller.response.CursorResponse;
import com.web.backend.controller.response.NotificationResponse;
import com.web.backend.exception.custom.ResourceNotFoundException;
import com.web.backend.model.postgres.UserEntity;
import com.web.backend.repository.NotificationRepository;
import com.web.backend.repository.UserRepository;
import com.web.backend.service.NotificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j(topic = "NOTIFICATION-SERVICE")
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;

    private final UserRepository userRepository;

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String NOTIF_UNREAD_PREFIX = "notif:unread:";

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private static final String ERROR_USER_NOT_FOUND_STRING = "error.user.not_found";
    private static final String ERROR_NOTIFICATION_NOT_FOUND_STRING = "error.notification.not_found";

    @Override
    @Transactional
    public void markNotificationAsRead(String username, Long notiId) {
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException(Translator.tolocale(ERROR_USER_NOT_FOUND_STRING)));

        int updateRow = notificationRepository.markAsReadByIdAndRecipientId(notiId, user.getId());
        if (updateRow == 0) {
            log.warn("Notification {} not found or already read for user {}", notiId, username);
            throw new ResourceNotFoundException(Translator.tolocale(ERROR_NOTIFICATION_NOT_FOUND_STRING));
        }
        try {
            redisTemplate.delete(NOTIF_UNREAD_PREFIX + username);
        } catch (Exception e) {
            log.warn("Failed to evict unread notification cache for user {}", username, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public CursorResponse<NotificationResponse> getNotifications(String username, String cursorStr, int size) {
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException(Translator.tolocale(ERROR_USER_NOT_FOUND_STRING)));

        int pageSize = (size <= 0 || size > MAX_PAGE_SIZE) ? DEFAULT_PAGE_SIZE : size;

        Instant cursorTime = null;
        if (cursorStr != null && !cursorStr.isBlank()) {
            try {
                cursorTime = Instant.parse(cursorStr);
            } catch (Exception e) {
                log.warn("Invalid cursor format: {}, defaulting to first page", cursorStr);
            }
        }

        Pageable pageable = PageRequest.of(0, pageSize + 1);
        List<NotificationResponse> notifications = new ArrayList<>(
                notificationRepository.findNotificationsByCursor(user.getId(), cursorTime, pageable));

        boolean hasMore = false;
        if (notifications.size() > pageSize) {
            hasMore = true;
            notifications.remove(notifications.size() - 1);
        }

        String nextCursor = null;
        if (!notifications.isEmpty() && hasMore) {
            Instant lastCreatedAt = notifications.get(notifications.size() - 1).getCreatedAt();
            if (lastCreatedAt != null) {
                nextCursor = lastCreatedAt.toString();
            }
        }

        return new CursorResponse<>(notifications, nextCursor, hasMore);
    }

    @Override
    @Transactional(readOnly = true)
    public Long getUnreadNotificationCounts(String username) {
        String cacheKey = NOTIF_UNREAD_PREFIX + username;

        try {
            Object cachedCount = redisTemplate.opsForValue().get(cacheKey);
            if (cachedCount != null) {
                return Long.valueOf(cachedCount.toString());
            }
        } catch (Exception e) {
            log.warn("Failed to get unread notification count from Redis for user {}", username, e);
        }
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException(Translator.tolocale(ERROR_USER_NOT_FOUND_STRING)));

        long count = notificationRepository.countByRecipientIdAndIsReadFalse(user.getId());

        try {
            redisTemplate.opsForValue().set(cacheKey, count, Duration.ofMinutes(10));
        } catch (Exception e) {
            log.warn("Failed to cache unread notification count for user {}", username, e);
        }
        return count;
    }

    @Override
    @Transactional
    public int markAllNotificationsAsRead(String username) {
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException(Translator.tolocale(ERROR_USER_NOT_FOUND_STRING)));

        int updatedCount = notificationRepository.markAllAsReadByRecipientId(user.getId());

        try {
            redisTemplate.delete(NOTIF_UNREAD_PREFIX + username);
        } catch (Exception e) {
            log.warn("Failed to evict unread notification cache for user {}", username, e);
        }
        return updatedCount;
    }

}
