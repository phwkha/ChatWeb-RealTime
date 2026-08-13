package com.web.backend.controller;

import com.web.backend.config.localresolverconfig.Translator;
import com.web.backend.controller.request.MarkReadRequest;
import com.web.backend.controller.request.EditMessageRequest;
import com.web.backend.controller.request.ReactionRequest;
import com.web.backend.controller.request.RevokeMessageRequest;
import com.web.backend.controller.response.ApiResponse;
import com.web.backend.controller.response.ChatMessageResponse;
import com.web.backend.controller.response.CursorResponse;
import com.web.backend.controller.response.UnreadCountsResponse;
import com.web.backend.model.UserEntity;
import com.web.backend.service.MessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Message Controller")
@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
@Slf4j(topic = "MESSAGE-CONTROLLER")
public class MessageController {

        private final MessageService messageService;

        private static final String SUCCESS_MSG_GET_PRIVATE_STRING = "success.msg.get_private";
        private static final String SUCCESS_MSG_GET_UNREAD_STRING = "success.msg.get_unread";
        private static final String SUCCESS_MSG_MARK_READ_STRING = "success.msg.mark_read";
        private static final String SUCCESS_MSG_GET_MESSAGE_STRING = "success.msg.get_message";
        private static final String SUCCESS_MSG_REACTION_STRING = "success.msg.reaction";
        private static final String SUCCESS_MSG_EDIT_STRING = "success.msg.edit";
        private static final String SUCCESS_MSG_REVOKE_STRING = "success.msg.revoke";

        @Operation(summary = "Get private message", description = "API endpoint for get private message")
        @GetMapping("/private")
        public ResponseEntity<ApiResponse<CursorResponse<ChatMessageResponse>>> getPrivateMessage(
                        @RequestParam String user1,
                        @RequestParam String user2,
                        @RequestParam(required = false) String cursor,
                        @RequestParam(defaultValue = "20") int size) {
                log.info("Fetching private messages between {} and {}", user1, user2);

                CursorResponse<ChatMessageResponse> response = messageService.findPrivateMessageWithCursor(user1, user2,
                                cursor,
                                size);

                return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(),
                                Translator.tolocale(SUCCESS_MSG_GET_PRIVATE_STRING), response));
        }

        @Operation(summary = "Get unread counts", description = "API endpoint for get unread counts")
        @GetMapping("/unread-counts")
        public ResponseEntity<ApiResponse<UnreadCountsResponse>> getUnreadCounts(Authentication auth) {
                UserEntity user = (UserEntity) auth.getPrincipal();

                log.info("Fetching unread counts for user: {}", user.getUsername());

                return ResponseEntity
                                .ok(ApiResponse.success(HttpStatus.OK.value(),
                                                Translator.tolocale(SUCCESS_MSG_GET_UNREAD_STRING),
                                                messageService.getUnreadMessageCounts(user.getUsername())));
        }

        @Operation(summary = "Mark as read", description = "API endpoint for mark as read")
        @PostMapping("/mark-as-read")
        public ResponseEntity<ApiResponse<Void>> markAsRead(
                        Authentication auth,
                        @RequestBody @Valid MarkReadRequest request) {

                UserEntity user = (UserEntity) auth.getPrincipal();

                log.info("User {} marking messages from {} as read", user.getUsername(), request.getSender());

                messageService.markMessagesAsRead(user.getUsername(), request.getSender());

                return ResponseEntity.ok(
                                ApiResponse.success(HttpStatus.OK.value(),
                                                Translator.tolocale(SUCCESS_MSG_MARK_READ_STRING), null));
        }

        @Operation(summary = "Get message by ID", description = "API endpoint to fetch a specific message by its ID")
        @GetMapping("/{id}")
        public ResponseEntity<ApiResponse<ChatMessageResponse>> getMessageById(
                        @PathVariable String id,
                        Authentication auth) {

                UserEntity user = (UserEntity) auth.getPrincipal();
                log.info("Fetching message ID {} for user: {}", id, user.getUsername());

                ChatMessageResponse response = messageService.getMessageById(id, user.getUsername());

                return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(),
                                Translator.tolocale(SUCCESS_MSG_GET_MESSAGE_STRING), response));
        }

        @Operation(summary = "React to a message", description = "API endpoint for reacting to a message")
        @PostMapping("/reaction")
        public ResponseEntity<ApiResponse<Void>> reactToMessage(
                        Authentication auth,
                        @RequestBody @Valid ReactionRequest request) {

                UserEntity user = (UserEntity) auth.getPrincipal();

                log.info("User {} reacting to message {}", user.getUsername(), request.getMessageId());

                messageService.reactToMessage(user.getUsername(), request);

                return ResponseEntity.ok(
                                ApiResponse.success(HttpStatus.OK.value(),
                                                Translator.tolocale(SUCCESS_MSG_REACTION_STRING), null));
        }

        @Operation(summary = "Edit a message", description = "API endpoint to edit an existing message")
        @PostMapping("/edit")
        public ResponseEntity<ApiResponse<Void>> editMessage(
                        Authentication auth,
                        @RequestBody @Valid EditMessageRequest request) {

                UserEntity user = (UserEntity) auth.getPrincipal();

                log.info("User {} editing message {}", user.getUsername(), request.getMessageId());

                messageService.editMessage(user.getUsername(), request);

                return ResponseEntity.ok(
                                ApiResponse.success(HttpStatus.OK.value(),
                                                Translator.tolocale(SUCCESS_MSG_EDIT_STRING), null));
        }

        @Operation(summary = "Revoke a message", description = "API endpoint to revoke a message")
        @PostMapping("/revoke")
        public ResponseEntity<ApiResponse<Void>> revokeMessage(
                        Authentication auth,
                        @RequestBody @Valid RevokeMessageRequest request) {

                UserEntity user = (UserEntity) auth.getPrincipal();

                log.info("User {} revoking message {}", user.getUsername(), request.getMessageId());

                messageService.revokeMessage(user.getUsername(), request);

                return ResponseEntity.ok(
                                ApiResponse.success(HttpStatus.OK.value(),
                                                Translator.tolocale(SUCCESS_MSG_REVOKE_STRING), null));
        }
}