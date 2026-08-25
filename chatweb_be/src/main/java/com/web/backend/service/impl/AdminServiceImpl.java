package com.web.backend.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.web.backend.common.UserStatus;
import com.web.backend.config.localresolverconfig.Translator;
import com.web.backend.controller.request.AddressRequest;
import com.web.backend.controller.request.AdminCreateUserRequest;
import com.web.backend.controller.request.AdminUpdateUserRequest;
import com.web.backend.controller.response.AddressResponse;
import com.web.backend.controller.response.PageResponse;
import com.web.backend.controller.response.UserDetailResponse;
import com.web.backend.controller.response.UserResponse;
import com.web.backend.controller.response.UserSummaryResponse;
import com.web.backend.exception.custom.AccessForbiddenException;
import com.web.backend.exception.custom.ResourceConflictException;
import com.web.backend.exception.custom.ResourceNotFoundException;
import com.web.backend.mapper.UserMapper;
import com.web.backend.model.postgres.AddressEntity;
import com.web.backend.model.postgres.RoleEntity;
import com.web.backend.model.postgres.UserEntity;
import com.web.backend.repository.AddressRepository;
import com.web.backend.repository.MessageRepository;
import com.web.backend.repository.RoleRepository;
import com.web.backend.repository.UserRepository;
import com.web.backend.service.AdminService;
import com.web.backend.service.StorageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j(topic = "ADMIN-SERVICE")
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final UserRepository userRepository;

    private final AddressRepository addressRepository;

    private final MessageRepository messageRepository;

    private final PasswordEncoder passwordEncoder;

    private final UserMapper userMapper;

    private final RoleRepository roleRepository;

    private final StorageService storageService;

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String DELIMITE_STRING = ":";

    private static final String ASC_STRING = "asc";
    private static final String ROLEID_CANNOT_BE_NULL_STRING = "RoleId cannot be null";

    private static final String ID_STRING = "id";

    private static final String USER_DETAILS_STRING = "user_details";
    private static final String USERNAME_STRING = "#username";
    private static final String AVATARS_STRING = "avatars";

    private static final String ERROR_USER_NOT_FOUND_WITH_STRING = "error.user.not_found_with";
    private static final String ERROR_ADMIN_USERNAME_EXISTS_STRING = "error.admin.username_exists";
    private static final String ERROR_ROLE_NOT_FOUND_WITH_STRING = "error.role.not_found_with";
    private static final String ERROR_USER_TARGET_NOT_FOUND_WITH_STRING = "error.user.target_not_found_with";
    private static final String ERROR_ADMIN_EMAIL_EXISTS_STRING = "error.admin.email_exists";
    private static final String ERROR_ROLE_NOT_FOUND_STRING = "error.role.not_found";
    private static final String ERROR_ADMIN_ADDRESS_NOT_OWNED_STRING = "error.admin.address_not_owned";
    private static final String ERROR_USER_ADDRESS_NOT_FOUND_STRING = "error.user.address_not_found";

    private static final String SYS_ACCOUNT_STRING = "sys.account";
    private static final String SYS_DELETED_STRING = "sys.deleted";

    private static final String ONLINE_USERS_KEY = "online_users";

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "id", "username", "email", "phone", "firstName", "lastName",
            "gender", "userStatus", "authProvider", "birthday", "createAt", "updateAt"
    );

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserSummaryResponse> getOnlineUsers(int pageNo, int pageSize) {
        int start = pageNo * pageSize;
        int end = start + pageSize - 1;

        Set<Object> onlineUsernames = redisTemplate.opsForZSet().reverseRange(ONLINE_USERS_KEY, start, end);
        Long totalOnline = redisTemplate.opsForZSet().size(ONLINE_USERS_KEY);

        if (onlineUsernames == null || onlineUsernames.isEmpty()) {
            return PageResponse.<UserSummaryResponse>builder()
                    .content(Collections.emptyList())
                    .pageNo(pageNo)
                    .pageSize(pageSize)
                    .totalElements(0)
                    .totalPages(0)
                    .last(true)
                    .build();
        }

        List<String> usernames = onlineUsernames.stream()
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .toList();

        List<UserSummaryResponse> userSummaries = userRepository.findSummaryByUsernameIn(usernames);

        userSummaries.forEach(dto -> dto.setOnline(true));

        List<UserSummaryResponse> content = userSummaries.stream()
                .sorted(Comparator.comparingInt(dto -> usernames.indexOf(dto.getUsername())))
                .toList();

        long total = totalOnline != null ? totalOnline : 0;
        int totalPages = (int) Math.ceil((double) total / pageSize);

        return PageResponse.<UserSummaryResponse>builder()
                .content(content)
                .pageNo(pageNo)
                .pageSize(pageSize)
                .totalElements(total)
                .totalPages(totalPages)
                .last(pageNo >= totalPages - 1)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserSummaryResponse> getAllUsers(int pageNo, int pageSize, String... sorts) {

        List<Sort.Order> orders = new ArrayList<>();
        if (sorts != null) {
            for (String sortBy : sorts) {
                String[] parts = sortBy.split(DELIMITE_STRING, 2);
                if (parts.length == 2 && !parts[0].isEmpty() && ALLOWED_SORT_FIELDS.contains(parts[0])) {
                    if (parts[1].equalsIgnoreCase(ASC_STRING)) {
                        orders.add(new Sort.Order(Sort.Direction.ASC, parts[0]));
                    } else {
                        orders.add(new Sort.Order(Sort.Direction.DESC, parts[0]));
                    }
                }
            }
        }

        if (orders.isEmpty()) {
            orders.add(new Sort.Order(Sort.Direction.DESC, ID_STRING));
        }
        Pageable pageable = PageRequest.of(pageNo, pageSize,
                Sort.by(orders));

        Page<UserSummaryResponse> pageResult = userRepository.findSummaryByUserStatusNot(UserStatus.INACTIVE, pageable);

        return PageResponse.<UserSummaryResponse>builder()
                .content(pageResult.getContent())
                .pageNo(pageResult.getNumber())
                .pageSize(pageResult.getSize())
                .totalElements(pageResult.getTotalElements())
                .totalPages(pageResult.getTotalPages())
                .last(pageResult.isLast())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetailResponse getUserByUsername(String username) {
        UserEntity userEntity = userRepository.findWithAuthoritiesByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException(
                        Translator.tolocale(ERROR_USER_NOT_FOUND_WITH_STRING, username)));

        if (userEntity.getUserStatus() == UserStatus.INACTIVE) {
            throw new ResourceNotFoundException(Translator.tolocale(ERROR_USER_NOT_FOUND_WITH_STRING, username));
        }
        return userMapper.toUserDetailResponse(userEntity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserResponse adminCreateUser(AdminCreateUserRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new ResourceConflictException(
                    Translator.tolocale(ERROR_ADMIN_USERNAME_EXISTS_STRING, request.getUsername()));
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ResourceConflictException(
                    Translator.tolocale(ERROR_ADMIN_EMAIL_EXISTS_STRING) + request.getEmail());
        }

        UserEntity user = userMapper.toEntity(request);

        user.setPassword(passwordEncoder.encode(request.getPassword()));

        if (user.getUserStatus() == null) {
            user.setUserStatus(UserStatus.ACTIVE);
        }

        Long roleId = Objects.requireNonNull(request.getRoleId(), ROLEID_CANNOT_BE_NULL_STRING);
        RoleEntity role = roleRepository.findById(roleId)
                .orElseThrow(
                        () -> new ResourceNotFoundException(
                                Translator.tolocale(ERROR_ROLE_NOT_FOUND_WITH_STRING, roleId)));

        user.setRole(role);
        UserEntity savedUser = userRepository.save(user);
        log.info("Admin created new user '{}'", savedUser.getUsername());

        return userMapper.toUserResponse(savedUser);
    }

    @Override
    @Transactional
    @CacheEvict(value = USER_DETAILS_STRING, key = USERNAME_STRING)
    public UserResponse lockUser(String username) {
        UserEntity userEntity = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException(
                        Translator.tolocale(ERROR_USER_NOT_FOUND_WITH_STRING, username)));

        userEntity.setUserStatus(UserStatus.LOCKED);
        userEntity.setOnline(false);

        UserEntity savedUser = userRepository.save(userEntity);
        log.info("Admin locked user account '{}'", username);

        return userMapper.toUserResponse(savedUser);
    }

    @Override
    @Transactional
    @CacheEvict(value = USER_DETAILS_STRING, key = USERNAME_STRING)
    public UserResponse unlockUser(String username) {
        UserEntity userEntity = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException(
                        Translator.tolocale(ERROR_USER_NOT_FOUND_WITH_STRING, username)));

        if (userEntity.getUserStatus() == UserStatus.LOCKED) {
            userEntity.setUserStatus(UserStatus.ACTIVE);
            UserEntity savedUser = userRepository.save(userEntity);
            log.info("Admin unlocked user account '{}'", username);

            return userMapper.toUserResponse(savedUser);
        }

        log.warn("Admin attempted to unlock user '{}' who was not in LOCKED status (Status: {})", username,
                userEntity.getUserStatus());

        return userMapper.toUserResponse(userEntity);
    }

    @Override
    @Transactional
    @CacheEvict(value = USER_DETAILS_STRING, key = USERNAME_STRING)
    public void deleteAvatar(String username) {
        UserEntity userEntity = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException(
                        Translator.tolocale(ERROR_USER_NOT_FOUND_WITH_STRING, username)));

        String urlAvatar = userEntity.getAvatar();

        userEntity.setAvatar(null);

        storageService.delete(urlAvatar, AVATARS_STRING);

        log.info("Admin deleted avatar for user '{}'", username);
    }

    @Override
    @Transactional
    @CacheEvict(value = USER_DETAILS_STRING, key = USERNAME_STRING)
    public UserResponse adminUpdateUser(String username, AdminUpdateUserRequest request) {
        UserEntity userEntity = userRepository.findWithAuthoritiesByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException(
                        Translator.tolocale(ERROR_USER_NOT_FOUND_WITH_STRING, username)));

        if (request.getEmail() != null && !request.getEmail().equals(userEntity.getEmail())
                && userRepository.existsByEmail(request.getEmail())) {
            throw new ResourceConflictException(Translator.tolocale(ERROR_ADMIN_EMAIL_EXISTS_STRING));
        }

        userMapper.updateAdminUserFromRequest(request, userEntity);
        if (request.getRoleId() != null) {
            Long roleId = Objects.requireNonNull(request.getRoleId());
            RoleEntity newRole = roleRepository.findById(roleId)
                    .orElseThrow(() -> new ResourceNotFoundException(Translator.tolocale(ERROR_ROLE_NOT_FOUND_STRING)));
            userEntity.setRole(newRole);
        }

        UserEntity saved = userRepository.save(Objects.requireNonNull(userEntity));
        log.info("Admin updated user profile for '{}'", username);
        return userMapper.toUserResponse(saved);
    }

    @Override
    @Transactional
    @CacheEvict(value = USER_DETAILS_STRING, key = "#targetUsername")
    public void adminDeleteUser(String targetUsername, String requesterUsername) {
        UserEntity userEntity = userRepository.findByUsername(targetUsername)
                .orElseThrow(() -> new ResourceNotFoundException(
                        Translator.tolocale(ERROR_USER_NOT_FOUND_WITH_STRING, targetUsername)));
        boolean hasChatHistory = messageRepository.existsBySenderOrRecipient(targetUsername);

        if (hasChatHistory) {
            userEntity.setUserStatus(UserStatus.INACTIVE);
            userEntity.setOnline(false);
            userEntity.setEmail(null);
            userEntity.setPhone(null);
            userEntity.setFirstName(Translator.tolocale(SYS_ACCOUNT_STRING));
            userEntity.setLastName(Translator.tolocale(SYS_DELETED_STRING));
            userEntity.setAvatar(null);
            userRepository.save(userEntity);
            log.info("Admin '{}' soft-deleted user '{}' (retaining chat history)", requesterUsername, targetUsername);
        } else {
            userRepository.delete(Objects.requireNonNull(userEntity));
            log.info("Admin '{}' hard-deleted user '{}'", requesterUsername, targetUsername);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<AddressResponse> adminGetAllAddresses(String targetUsername) {
        if (!userRepository.existsByUsername(targetUsername)) {
            throw new ResourceNotFoundException(
                    Translator.tolocale(ERROR_USER_TARGET_NOT_FOUND_WITH_STRING, targetUsername));
        }

        return addressRepository.findAddressResponsesByUsername(targetUsername);
    }

    @Override
    @Transactional(readOnly = true)
    public AddressResponse adminGetAddressById(String targetUsername, Long addressId) {
        if (!userRepository.existsByUsername(targetUsername)) {
            throw new ResourceNotFoundException(
                    Translator.tolocale(ERROR_USER_TARGET_NOT_FOUND_WITH_STRING, targetUsername));
        }

        return addressRepository.findAddressResponseByIdAndUsername(addressId, targetUsername)
                .orElseThrow(() -> new AccessForbiddenException(
                        Translator.tolocale(ERROR_ADMIN_ADDRESS_NOT_OWNED_STRING)));
    }

    @Override
    @Transactional
    @CacheEvict(value = USER_DETAILS_STRING, key = USERNAME_STRING)
    public UserDetailResponse adminUpdateAddress(String targetUsername, Long addressId, AddressRequest request) {
        UserEntity user = userRepository.findWithAuthoritiesByUsername(targetUsername)
                .orElseThrow(
                        () -> new ResourceNotFoundException(
                                Translator.tolocale(ERROR_USER_TARGET_NOT_FOUND_WITH_STRING, targetUsername)));

        AddressEntity addressToUpdate = addressRepository.findByIdAndUser_Username(addressId, targetUsername)
                .orElseThrow(() -> new ResourceNotFoundException(
                        Translator.tolocale(ERROR_ADMIN_ADDRESS_NOT_OWNED_STRING)));

        userMapper.updateAddressFromRequest(request, addressToUpdate);

        addressRepository.save(addressToUpdate);
        log.info("Admin updated address id={} for user '{}'", addressId, targetUsername);
        return userMapper.toUserDetailResponse(user);
    }

    @Override
    @Transactional
    @CacheEvict(value = USER_DETAILS_STRING, key = USERNAME_STRING)
    public void adminDeleteAddress(String targetUsername, Long addressId) {
        UserEntity user = userRepository.findByUsername(targetUsername)
                .orElseThrow(
                        () -> new ResourceNotFoundException(
                                Translator.tolocale(ERROR_USER_TARGET_NOT_FOUND_WITH_STRING, targetUsername)));

        AddressEntity addressToDelete = addressRepository.findByIdAndUser_Username(addressId, targetUsername)
                .orElseThrow(
                        () -> new ResourceNotFoundException(Translator.tolocale(ERROR_USER_ADDRESS_NOT_FOUND_STRING)));

        user.removeAddress(addressToDelete);
        addressRepository.delete(addressToDelete);

        log.info("Admin deleted address id={} for user '{}'", addressId, targetUsername);
    }

}
