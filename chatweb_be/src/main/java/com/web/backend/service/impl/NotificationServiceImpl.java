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

import com.web.backend.common.NotificationsType;
import com.web.backend.config.localresolverconfig.Translator;
import com.web.backend.controller.response.CursorResponse;
import com.web.backend.controller.response.NotificationResponse;
import com.web.backend.exception.custom.ResourceNotFoundException;
import com.web.backend.model.postgres.NotificationEntity;
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

    private static final String ERROR_NOTIFICATION_NOT_FOUND_STRING = "error.notification.not_found";

    @Override
    @Transactional
    public void createNotification(String senderUsername, String recipientUsername, NotificationsType type,
            String content) {
        UserEntity sender = userRepository.findByUsername(senderUsername).orElse(null);
        UserEntity recipient = userRepository.findByUsername(recipientUsername).orElse(null);

        if (recipient == null) {
            log.warn("Cannot create notification: recipient '{}' not found", recipientUsername);
            return;
        }

        NotificationEntity entity = NotificationEntity.builder()
                .sender(sender)
                .recipient(recipient)
                .type(type)
                .content(content)
                .isRead(false)
                .build();
        notificationRepository.save(entity);

        try {
            redisTemplate.delete(NOTIF_UNREAD_PREFIX + recipientUsername);
        } catch (Exception e) {
            log.warn("Failed to evict unread notification cache create for user {}", recipientUsername, e);
        }
    }

    @Override
    @Transactional
    public void markNotificationAsRead(UserEntity user, Long notiId) {
        int updateRow = notificationRepository.markAsReadByIdAndRecipientId(notiId, user.getId());
        if (updateRow == 0) {
            log.warn("Notification {} not found or already read for user {}", notiId, user.getUsername());
            throw new ResourceNotFoundException(Translator.tolocale(ERROR_NOTIFICATION_NOT_FOUND_STRING));
        }
        try {
            redisTemplate.delete(NOTIF_UNREAD_PREFIX + user.getUsername());
        } catch (Exception e) {
            log.warn("Failed to evict unread notification cache for user {}", user.getUsername(), e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public CursorResponse<NotificationResponse> getNotifications(UserEntity user, String cursorStr, int size) {
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
    public Long getUnreadNotificationCounts(UserEntity user) {
        String cacheKey = NOTIF_UNREAD_PREFIX + user.getUsername();

        try {
            Object cachedCount = redisTemplate.opsForValue().get(cacheKey);
            if (cachedCount != null) {
                return Long.valueOf(cachedCount.toString());
            }
        } catch (Exception e) {
            log.warn("Failed to get unread notification count from Redis for user {}", user.getUsername(), e);
        }

        long count = notificationRepository.countByRecipientIdAndIsReadFalse(user.getId());

        try {
            redisTemplate.opsForValue().set(cacheKey, count, Duration.ofMinutes(10));
        } catch (Exception e) {
            log.warn("Failed to cache unread notification count for user {}", user.getUsername(), e);
        }
        return count;
    }

    @Override
    @Transactional
    public int markAllNotificationsAsRead(UserEntity user) {
        int updatedCount = notificationRepository.markAllAsReadByRecipientId(user.getId());

        try {
            redisTemplate.delete(NOTIF_UNREAD_PREFIX + user.getUsername());
        } catch (Exception e) {
            log.warn("Failed to evict unread notification cache for user {}", user.getUsername(), e);
        }
        return updatedCount;
    }

}
