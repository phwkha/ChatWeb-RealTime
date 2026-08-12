package com.web.backend.controller;

import com.web.backend.config.localresolverconfig.Translator;
import com.web.backend.controller.request.FriendRequest;
import com.web.backend.controller.response.PageResponse;
import com.web.backend.controller.response.UserSummaryResponse;
import com.web.backend.controller.response.wrapper.ApiResponse;
import com.web.backend.model.UserEntity;
import com.web.backend.service.FriendService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Friend Controller")
@RestController
@RequestMapping("/api/friends")
@RequiredArgsConstructor
@Slf4j(topic = "FRIEND-CONTROLLER")
public class FriendController {

        private final FriendService friendService;

        private static final String SUCCESS_FRIEND_BLOCKED_WITH_STRING = "success.friend.blocked_with";
        private static final String SUCCESS_FRIEND_GET_INVITES_STRING = "success.friend.get_invites";
        private static final String SUCCESS_FRIEND_GET_SENT_INVITES_STRING = "success.friend.get_sent_invites";
        private static final String SUCCESS_FRIEND_GET_FRIENDS_STRING = "success.friend.get_friends";

        private static final String SUCCESS_SYS_OPERATION_STRING = "success.sys.operation";

        @Operation(summary = "Get friend requests", description = "API endpoint for get friend requests")
        @GetMapping("/requests")
        public ResponseEntity<ApiResponse<PageResponse<UserSummaryResponse>>> getFriendRequests(
                        Authentication auth,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "10") int size,
                        @RequestParam(defaultValue = "desc") String sortDir) {
                UserEntity user = (UserEntity) auth.getPrincipal();
                log.info("Get friend invites for: {}", user.getUsername());

                return ResponseEntity.ok(ApiResponse.success(
                                HttpStatus.OK.value(),
                                Translator.tolocale(SUCCESS_FRIEND_GET_INVITES_STRING),
                                friendService.getPendingRequests(user.getUsername(), page, size, sortDir)));
        }

        @Operation(summary = "Get sent requests", description = "API endpoint for get sent requests")
        @GetMapping("/sent")
        public ResponseEntity<ApiResponse<PageResponse<UserSummaryResponse>>> getSentRequests(
                        Authentication auth,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "10") int size,
                        @RequestParam(defaultValue = "desc") String sortDir) {
                UserEntity user = (UserEntity) auth.getPrincipal();
                return ResponseEntity.ok(ApiResponse.success(
                                HttpStatus.OK.value(),
                                Translator.tolocale(SUCCESS_FRIEND_GET_SENT_INVITES_STRING),
                                friendService.getSentRequests(user.getUsername(), page, size, sortDir)));
        }

        @Operation(summary = "Get friends list", description = "API endpoint for get friends list")
        @GetMapping
        public ResponseEntity<ApiResponse<PageResponse<UserSummaryResponse>>> getFriendsList(
                        Authentication auth,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "10") int size,
                        @RequestParam(defaultValue = "desc") String sortDir) {
                UserEntity user = (UserEntity) auth.getPrincipal();
                log.info("Get friend list for: {}", user.getUsername());

                return ResponseEntity.ok(ApiResponse.success(
                                HttpStatus.OK.value(),
                                Translator.tolocale(SUCCESS_FRIEND_GET_FRIENDS_STRING),
                                friendService.getFriendsList(user.getUsername(), page, size, sortDir)));
        }

        @Operation(summary = "Delete friendship", description = "API endpoint for delete friendship")
        @DeleteMapping("/{username}")
        public ResponseEntity<ApiResponse<Void>> deleteFriendship(
                        Authentication auth,
                        @PathVariable String username) {

                UserEntity user = (UserEntity) auth.getPrincipal();
                friendService.deleteFriendship(user.getUsername(), username);

                return ResponseEntity.ok(ApiResponse.success(
                                HttpStatus.OK.value(),
                                Translator.tolocale(SUCCESS_SYS_OPERATION_STRING),
                                null));
        }

        @Operation(summary = "Block user", description = "API endpoint for block user")
        @PostMapping("/block/{username}")
        public ResponseEntity<ApiResponse<Void>> blockUser(Authentication auth, @PathVariable String username) {
                UserEntity user = (UserEntity) auth.getPrincipal();
                friendService.blockUser(user.getUsername(), username);

                return ResponseEntity.ok(ApiResponse.success(
                                HttpStatus.OK.value(),
                                Translator.tolocale(SUCCESS_FRIEND_BLOCKED_WITH_STRING, username),
                                null));
        }

        @Operation(summary = "Send friend request", description = "API endpoint to send a friend request")
        @PostMapping("/request")
        public ResponseEntity<ApiResponse<Void>> sendFriendRequest(
                        Authentication auth,
                        @RequestBody @Valid FriendRequest request) {

                UserEntity user = (UserEntity) auth.getPrincipal();
                log.info("User {} sending friend request to {}", user.getUsername(), request.getTargetUsername());

                friendService.sendFriendRequest(user.getUsername(), request.getTargetUsername());

                return ResponseEntity.ok(ApiResponse.success(
                                HttpStatus.OK.value(),
                                Translator.tolocale(SUCCESS_SYS_OPERATION_STRING),
                                null));
        }

        @Operation(summary = "Accept friend request", description = "API endpoint to accept a friend request")
        @PostMapping("/accept")
        public ResponseEntity<ApiResponse<Void>> acceptFriendRequest(
                        Authentication auth,
                        @RequestBody @Valid FriendRequest request) {

                UserEntity user = (UserEntity) auth.getPrincipal();
                log.info("User {} accepting friend request from {}", user.getUsername(), request.getTargetUsername());

                friendService.acceptFriendRequest(user.getUsername(), request.getTargetUsername());

                return ResponseEntity.ok(ApiResponse.success(
                                HttpStatus.OK.value(),
                                Translator.tolocale(SUCCESS_SYS_OPERATION_STRING),
                                null));
        }
}