package com.web.backend.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.web.backend.controller.response.NotificationResponse;
import com.web.backend.model.postgres.NotificationEntity;

public interface NotificationRepository extends JpaRepository<NotificationEntity, Long> {

  @Query("""
          SELECT new com.web.backend.controller.response.NotificationResponse(
              n.id, n.type, n.targetType, n.targetId, n.content, n.isRead, n.createAt,
              s.username, s.firstName, s.lastName, s.avatar
          )
          FROM NotificationEntity n
          LEFT JOIN n.sender s
          WHERE n.recipient.id = :recipientId
          ORDER BY n.createAt DESC, n.id DESC
      """)
  List<NotificationResponse> findInitialNotifications(
      @Param("recipientId") Long recipientId,
      Pageable pageable);

  @Query("""
          SELECT new com.web.backend.controller.response.NotificationResponse(
              n.id, n.type, n.targetType, n.targetId, n.content, n.isRead, n.createAt,
              s.username, s.firstName, s.lastName, s.avatar
          )
          FROM NotificationEntity n
          LEFT JOIN n.sender s
          WHERE n.recipient.id = :recipientId
            AND (
                n.createAt < :cursorTime
                OR (n.createAt = :cursorTime AND n.id < :cursorId)
            )
          ORDER BY n.createAt DESC, n.id DESC
      """)
  List<NotificationResponse> findNotificationsByCursor(
      @Param("recipientId") Long recipientId,
      @Param("cursorTime") Instant cursorTime,
      @Param("cursorId") Long cursorId,
      Pageable pageable);

  @Modifying
  @Query("""
          DELETE FROM NotificationEntity n
          WHERE n.isRead = true
            AND n.createAt < :cutoffTime
      """)
  int deleteReadNotificationsBefore(@Param("cutoffTime") Instant cutoffTime);

  @Modifying
  @Query("""
          UPDATE NotificationEntity n
          SET n.isRead = true
          WHERE n.id = :id
            AND n.recipient.id = :recipientId
            AND n.isRead = false
      """)
  int markAsReadByIdAndRecipientId(@Param("id") Long id, @Param("recipientId") Long recipientId);

  @Modifying
  @Query("""
          UPDATE NotificationEntity n
          SET n.isRead = true
          WHERE n.recipient.id = :recipientId
            AND n.isRead = false
      """)
  int markAllAsReadByRecipientId(@Param("recipientId") Long recipientId);

  long countByRecipientIdAndIsReadFalse(Long recipientId);

}
