package com.web.backend.controller;

import com.web.backend.config.localresolverconfig.Translator;
import com.web.backend.controller.request.AddressRequest;
import com.web.backend.controller.request.AdminCreateUserRequest;
import com.web.backend.controller.request.AdminUpdateUserRequest;
import com.web.backend.controller.response.*;
import com.web.backend.model.postgres.UserEntity;
import com.web.backend.service.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Admin Controller")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j(topic = "ADMIN-CONTROLLER")
public class AdminController {

        private final AdminService adminService;

        private static final String STR_10_STRING = "10";

        private static final String SUCCESS_ADMIN_CREATE_USER_STRING = "success.admin.create_user";
        private static final String SUCCESS_ADMIN_DEL_AVATAR_STRING = "success.admin.del_avatar";
        private static final String SUCCESS_ADMIN_DEL_USER_STRING = "success.admin.del_user";
        private static final String SUCCESS_ADMIN_DELETED_ADDRESS_WITH_STRING = "success.admin.deleted_address_with";
        private static final String SUCCESS_ADMIN_GET_ALL_ADDRESS_WITH_STRING = "success.admin.get_all_address_with";
        private static final String SUCCESS_ADMIN_GET_ONLINE_USERS_STRING = "success.admin.get_online_users";
        private static final String SUCCESS_ADMIN_GET_USER_STRING = "success.admin.get_user";
        private static final String SUCCESS_ADMIN_GET_USERS_STRING = "success.admin.get_users";
        private static final String SUCCESS_ADMIN_LOCK_USER_STRING = "success.admin.lock_user";
        private static final String SUCCESS_ADMIN_UNLOCK_USER_STRING = "success.admin.unlock_user";
        private static final String SUCCESS_ADMIN_UPDATE_USER_STRING = "success.admin.update_user";
        private static final String SUCCESS_ADMIN_UPDATED_ADDRESS_WITH_STRING = "success.admin.updated_address_with";

        private static final String SUCCESS_USER_GET_ADDRESS_STRING = "success.user.get_address";

        @Operation(summary = "Get all users", description = "API endpoint for get all users")
        @GetMapping("/users")
        @PreAuthorize("hasAuthority('ADMIN_VIEW')")
        public ResponseEntity<ApiResponse<PageResponse<UserSummaryResponse>>> getAllUsers(
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = STR_10_STRING) int size,
                        @RequestParam(required = false) String... sorts) {
                PageResponse<UserSummaryResponse> users = adminService.getAllUsers(page, size, sorts);
                return ResponseEntity
                                .ok(ApiResponse.success(HttpStatus.OK.value(),
                                                Translator.tolocale(SUCCESS_ADMIN_GET_USERS_STRING), users));
        }

        @Operation(summary = "Get online users", description = "API endpoint for get online users")
        @GetMapping("/online")
        @PreAuthorize("hasAuthority('ADMIN_VIEW')")
        public ResponseEntity<ApiResponse<PageResponse<UserSummaryResponse>>> getOnlineUsers(
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = STR_10_STRING) int size) {
                PageResponse<UserSummaryResponse> userPageResponse = adminService.getOnlineUsers(page, size);
                return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(),
                                Translator.tolocale(SUCCESS_ADMIN_GET_ONLINE_USERS_STRING),
                                userPageResponse));
        }

        @Operation(summary = "Get user by username", description = "API endpoint for get user by username")
        @GetMapping("/user/{username}")
        @PreAuthorize("hasAuthority('ADMIN_VIEW')")
        public ResponseEntity<ApiResponse<UserDetailResponse>> getUserByUsername(@PathVariable String username) {
                UserDetailResponse user = adminService.getUserByUsername(username);
                return ResponseEntity
                                .ok(ApiResponse.success(HttpStatus.OK.value(),
                                                Translator.tolocale(SUCCESS_ADMIN_GET_USER_STRING), user));
        }

        @Operation(summary = "Add user", description = "API endpoint for add user")
        @PostMapping("/add")
        @PreAuthorize("hasAuthority('ADMIN_CREATE')")
        public ResponseEntity<ApiResponse<UserResponse>> addUser(Authentication authentication,
                        @RequestBody @Valid AdminCreateUserRequest request) {
                UserEntity userEntityPrincipal = (UserEntity) authentication.getPrincipal();
                log.debug("Admin '{}' creating new user '{}'", userEntityPrincipal.getUsername(),
                                request.getUsername());
                UserResponse newUser = adminService.adminCreateUser(request);
                return ResponseEntity.status(HttpStatus.CREATED)
                                .body(ApiResponse.success(HttpStatus.CREATED.value(),
                                                Translator.tolocale(SUCCESS_ADMIN_CREATE_USER_STRING),
                                                newUser));
        }

        @Operation(summary = "Unlock user", description = "API endpoint for unlock user")
        @PostMapping("/{username}/unlock")
        @PreAuthorize("hasAuthority('ADMIN_UNLOCK')")
        public ResponseEntity<ApiResponse<UserResponse>> unlockUser(Authentication authentication,
                        @PathVariable String username) {
                UserEntity userEntityPrincipal = (UserEntity) authentication.getPrincipal();
                log.debug("Admin '{}' unlocking user '{}'", userEntityPrincipal.getUsername(), username);
                UserResponse unlockedUser = adminService.unlockUser(username);
                return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(),
                                Translator.tolocale(SUCCESS_ADMIN_UNLOCK_USER_STRING), unlockedUser));
        }

        @Operation(summary = "Lock user", description = "API endpoint for lock user")
        @PostMapping("/{username}/lock")
        @PreAuthorize("hasAuthority('ADMIN_LOCK')")
        public ResponseEntity<ApiResponse<UserResponse>> lockUser(Authentication authentication,
                        @PathVariable String username) {
                UserEntity userEntityPrincipal = (UserEntity) authentication.getPrincipal();
                log.debug("Admin '{}' locking user '{}'", userEntityPrincipal.getUsername(), username);
                UserResponse lockedUser = adminService.lockUser(username);
                return ResponseEntity.ok(
                                ApiResponse.success(HttpStatus.OK.value(),
                                                Translator.tolocale(SUCCESS_ADMIN_LOCK_USER_STRING), lockedUser));
        }

        @Operation(summary = "Delete avatar", description = "API endpoint for delete avatar")
        @PostMapping("/{username}/delete-avatar")
        @PreAuthorize("hasAuthority('ADMIN_DELETE_AVATAR')")
        public ResponseEntity<ApiResponse<Void>> deleteAvatar(Authentication authentication,
                        @PathVariable String username) {
                UserEntity userEntity = (UserEntity) authentication.getPrincipal();
                log.debug("Admin '{}' deleting avatar for user '{}'", userEntity.getUsername(), username);
                adminService.deleteAvatar(username);
                return ResponseEntity
                                .ok(ApiResponse.success(HttpStatus.OK.value(),
                                                Translator.tolocale(SUCCESS_ADMIN_DEL_AVATAR_STRING), null));
        }

        @Operation(summary = "Update user", description = "API endpoint for update user")
        @PutMapping("/{username}")
        @PreAuthorize("hasAuthority('ADMIN_UPDATE')")
        public ResponseEntity<ApiResponse<UserResponse>> updateUser(
                        Authentication authentication,
                        @PathVariable String username,
                        @RequestBody @Valid AdminUpdateUserRequest request) {
                UserEntity userEntityPrincipal = (UserEntity) authentication.getPrincipal();
                log.debug("Admin '{}' updating user '{}'", userEntityPrincipal.getUsername(), username);
                UserResponse updatedUser = adminService.adminUpdateUser(username, request);
                return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(),
                                Translator.tolocale(SUCCESS_ADMIN_UPDATE_USER_STRING), updatedUser));
        }

        @Operation(summary = "Delete user", description = "API endpoint for delete user")
        @DeleteMapping("/{username}")
        @PreAuthorize("hasAuthority('ADMIN_DELETE')")
        public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable String username,
                        Authentication authentication) {
                UserEntity adminPrincipal = (UserEntity) authentication.getPrincipal();
                log.debug("Admin '{}' deleting user '{}'", adminPrincipal.getUsername(), username);

                adminService.adminDeleteUser(username, adminPrincipal.getUsername());

                return ResponseEntity.status(HttpStatus.NO_CONTENT)
                                .body(ApiResponse.success(HttpStatus.NO_CONTENT.value(),
                                                Translator.tolocale(SUCCESS_ADMIN_DEL_USER_STRING),
                                                null));
        }

        @Operation(summary = "Get all addresses for user", description = "API endpoint for get all addresses for user")
        @GetMapping("/user/{username}/addresses")
        @PreAuthorize("hasAuthority('ADMIN_VIEW')")
        public ResponseEntity<ApiResponse<List<AddressResponse>>> getAllAddressesForUser(
                        @PathVariable String username) {
                List<AddressResponse> addresses = adminService.adminGetAllAddresses(username);
                return ResponseEntity.ok(ApiResponse.success(
                                HttpStatus.OK.value(),
                                Translator.tolocale(SUCCESS_ADMIN_GET_ALL_ADDRESS_WITH_STRING, username),
                                addresses));
        }

        @Operation(summary = "Get address by id for user", description = "API endpoint for get address by id for user")
        @GetMapping("/user/{username}/address/{addressId}")
        @PreAuthorize("hasAuthority('ADMIN_VIEW')")
        public ResponseEntity<ApiResponse<AddressResponse>> getAddressByIdForUser(
                        @PathVariable String username,
                        @PathVariable Long addressId) {
                AddressResponse address = adminService.adminGetAddressById(username, addressId);
                return ResponseEntity.ok(ApiResponse.success(
                                HttpStatus.OK.value(),
                                Translator.tolocale(SUCCESS_USER_GET_ADDRESS_STRING),
                                address));
        }

        @Operation(summary = "Update address for user", description = "API endpoint for update address for user")
        @PutMapping("/user/{username}/address/{addressId}")
        @PreAuthorize("hasAuthority('ADMIN_UPDATE')")
        public ResponseEntity<ApiResponse<UserDetailResponse>> updateAddressForUser(
                        Authentication authentication,
                        @PathVariable String username,
                        @PathVariable Long addressId,
                        @RequestBody @Valid AddressRequest addressRequest) {
                UserEntity userEntityPrincipal = (UserEntity) authentication.getPrincipal();
                log.debug("Admin '{}' updating address id={} for user '{}'", userEntityPrincipal.getUsername(),
                                addressId, username);

                UserDetailResponse result = adminService.adminUpdateAddress(username, addressId, addressRequest);

                return ResponseEntity.ok(ApiResponse.success(
                                HttpStatus.OK.value(),
                                Translator.tolocale(SUCCESS_ADMIN_UPDATED_ADDRESS_WITH_STRING, username),
                                result));
        }

        @Operation(summary = "Delete address for user", description = "API endpoint for delete address for user")
        @DeleteMapping("/user/{username}/address/{addressId}")
        @PreAuthorize("hasAuthority('ADMIN_DELETE')")
        public ResponseEntity<ApiResponse<Void>> deleteAddressForUser(
                        Authentication authentication,
                        @PathVariable String username,
                        @PathVariable Long addressId) {
                UserEntity userEntityPrincipal = (UserEntity) authentication.getPrincipal();
                log.debug("Admin '{}' deleting address id={} for user '{}'", userEntityPrincipal.getUsername(),
                                addressId, username);

                adminService.adminDeleteAddress(username, addressId);

                return ResponseEntity.status(HttpStatus.NO_CONTENT)
                                .body(ApiResponse.success(
                                                HttpStatus.NO_CONTENT.value(),
                                                Translator.tolocale(SUCCESS_ADMIN_DELETED_ADDRESS_WITH_STRING,
                                                                username),
                                                null));
        }
}