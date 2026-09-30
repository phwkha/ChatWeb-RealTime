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

import com.web.backend.common.NotificationTargetType;
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
    public NotificationEntity createNotification(String senderUsername, String recipientUsername, NotificationsType type,
            NotificationTargetType targetType, String targetId, String content) {
        UserEntity recipient = userRepository.findByUsername(recipientUsername).orElse(null);
        if (recipient == null) {
            log.warn("Cannot create notification: recipient '{}' not found", recipientUsername);
            return null;
        }

        UserEntity sender = null;
        if (senderUsername != null && !senderUsername.isBlank()) {
            sender = userRepository.findByUsername(senderUsername).orElse(null);
            if (sender == null) {
                log.warn("Notification sender '{}' not found, proceeding as system sender", senderUsername);
            }
        }

        NotificationEntity entity = NotificationEntity.builder()
                .sender(sender)
                .recipient(recipient)
                .type(type)
                .targetType(targetType)
                .targetId(targetId)
                .content(content)
                .isRead(false)
                .build();
        NotificationEntity saved = notificationRepository.save(entity);

        try {
            redisTemplate.delete(NOTIF_UNREAD_PREFIX + recipientUsername);
        } catch (Exception e) {
            log.warn("createNotification Failed to evict unread notification cache for user {}", recipientUsername, e);
        }

        return saved;
    }

    @Override
    @Transactional
    public void markNotificationAsRead(UserEntity user, Long notiId) {
        int updateRow = notificationRepository.markAsReadByIdAndRecipientId(notiId, user.getId());
        if (updateRow == 0) {
            boolean exists = notificationRepository.existsByIdAndRecipientId(notiId, user.getId());
            if (!exists) {
                log.warn("Notification {} not found for user {}", notiId, user.getUsername());
                throw new ResourceNotFoundException(Translator.tolocale(ERROR_NOTIFICATION_NOT_FOUND_STRING));
            }
            log.info("Notification {} already read for user {}", notiId, user.getUsername());
            return;
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
        int pageSize = resolvePageSize(size);
        NotificationCursor cursor = parseCursor(cursorStr);

        Pageable pageable = PageRequest.of(0, pageSize + 1);
        List<NotificationResponse> notifications = new ArrayList<>(fetchNotifications(user.getId(), cursor, pageable));

        boolean hasMore = notifications.size() > pageSize;
        if (hasMore) {
            notifications.remove(notifications.size() - 1);
        }

        String nextCursor = buildNextCursor(notifications, hasMore);
        return new CursorResponse<>(notifications, nextCursor, hasMore);
    }

    private record NotificationCursor(Instant time, Long id) {
    }

    private int resolvePageSize(int size) {
        if (size <= 0 || size > MAX_PAGE_SIZE) {
            return DEFAULT_PAGE_SIZE;
        }
        return size;
    }

    private NotificationCursor parseCursor(String cursorStr) {
        if (cursorStr == null || cursorStr.isBlank()) {
            return null;
        }
        try {
            if (cursorStr.contains("_")) {
                String[] parts = cursorStr.split("_", 2);
                return new NotificationCursor(Instant.parse(parts[0]), Long.parseLong(parts[1]));
            }
            return new NotificationCursor(Instant.parse(cursorStr), Long.MAX_VALUE);
        } catch (Exception e) {
            log.warn("Invalid cursor format: {}, defaulting to first page", cursorStr);
            return null;
        }
    }

    private List<NotificationResponse> fetchNotifications(Long userId, NotificationCursor cursor, Pageable pageable) {
        if (cursor == null) {
            return notificationRepository.findInitialNotifications(userId, pageable);
        }
        return notificationRepository.findNotificationsByCursor(userId, cursor.time(), cursor.id(), pageable);
    }

    private String buildNextCursor(List<NotificationResponse> notifications, boolean hasMore) {
        if (notifications.isEmpty() || !hasMore) {
            return null;
        }
        NotificationResponse last = notifications.get(notifications.size() - 1);
        if (last.getCreatedAt() == null || last.getId() == null) {
            return null;
        }
        return last.getCreatedAt().toString() + "_" + last.getId();
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
