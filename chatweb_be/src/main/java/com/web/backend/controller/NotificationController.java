package com.web.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.web.backend.config.localresolverconfig.Translator;
import com.web.backend.controller.response.ApiResponse;
import com.web.backend.controller.response.CursorResponse;
import com.web.backend.controller.response.NotificationResponse;
import com.web.backend.model.postgres.UserEntity;
import com.web.backend.ratelimit.LimitType;
import com.web.backend.ratelimit.RateLimit;
import com.web.backend.service.NotificationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Tag(name = "Notification Controller")
@RestController
@RequestMapping({ "/api/notifications" })
@RequiredArgsConstructor
@Validated
@Slf4j(topic = "NOTIFICATION-CONTROLLER")
public class NotificationController {

        private final NotificationService notificationService;

        private static final String SUCCESS_SYS_OPERATION_STRING = "success.sys.operation";

        @Operation(summary = "Get notifications", description = "API endpoint for fetching cursor-paginated notifications")
        @ApiResponses(value = {
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Notifications fetched successfully"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request parameter"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing JWT token"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Rate limit exceeded")
        })
        @RateLimit(key = "notification_get", limit = 45, period = 60, type = LimitType.USER)
        @GetMapping
        public ResponseEntity<ApiResponse<CursorResponse<NotificationResponse>>> getNotifications(
                        Authentication auth,
                        @RequestParam(required = false) String cursor,
                        @RequestParam(defaultValue = "20") @Min(value = 1, message = "{valid.size_min}") @Max(value = 100, message = "{valid.size_max}") int size) {
                UserEntity user = (UserEntity) auth.getPrincipal();
                CursorResponse<NotificationResponse> response = notificationService.getNotifications(
                                user, cursor, size);
                return ResponseEntity.ok(ApiResponse.success(
                                HttpStatus.OK.value(),
                                Translator.tolocale(SUCCESS_SYS_OPERATION_STRING),
                                response));
        }

        @Operation(summary = "Get unread notification counts", description = "API endpoint for fetching unread notification counts")
        @ApiResponses(value = {
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Unread notification count fetched successfully"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing JWT token"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Rate limit exceeded")
        })
        @RateLimit(key = "notification_unread_count", limit = 60, period = 60, type = LimitType.USER)
        @GetMapping("/unread-counts")
        public ResponseEntity<ApiResponse<Long>> getUnreadNotificationCounts(Authentication auth) {
                UserEntity user = (UserEntity) auth.getPrincipal();
                Long count = notificationService.getUnreadNotificationCounts(user);
                return ResponseEntity.ok(ApiResponse.success(
                                HttpStatus.OK.value(),
                                Translator.tolocale(SUCCESS_SYS_OPERATION_STRING),
                                count));
        }

        @Operation(summary = "Mark notification as read", description = "API endpoint for marking a single notification as read")
        @ApiResponses(value = {
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Notification marked as read successfully"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid notification ID"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing JWT token"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Notification not found or already read"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Rate limit exceeded")
        })
        @RateLimit(key = "notification_mark_read", limit = 60, period = 60, type = LimitType.USER)
        @PatchMapping("/{id}/read")
        public ResponseEntity<ApiResponse<Void>> markNotificationAsRead(
                        Authentication auth,
                        @PathVariable @Positive(message = "{valid.id_positive}") Long id) {
                UserEntity user = (UserEntity) auth.getPrincipal();
                notificationService.markNotificationAsRead(user, id);
                return ResponseEntity.ok(ApiResponse.success(
                                HttpStatus.OK.value(),
                                Translator.tolocale(SUCCESS_SYS_OPERATION_STRING),
                                null));
        }

        @Operation(summary = "Mark all notifications as read", description = "API endpoint for marking all unread notifications of the current user as read")
        @ApiResponses(value = {
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "All notifications marked as read successfully"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing JWT token"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Rate limit exceeded")
        })
        @RateLimit(key = "notification_mark_all_read", limit = 15, period = 60, type = LimitType.USER)
        @PatchMapping("/read-all")
        public ResponseEntity<ApiResponse<Integer>> markAllAsRead(Authentication auth) {
                UserEntity user = (UserEntity) auth.getPrincipal();
                int updatedCount = notificationService.markAllNotificationsAsRead(user);
                return ResponseEntity.ok(ApiResponse.success(
                                HttpStatus.OK.value(),
                                Translator.tolocale(SUCCESS_SYS_OPERATION_STRING),
                                updatedCount));
        }

}
