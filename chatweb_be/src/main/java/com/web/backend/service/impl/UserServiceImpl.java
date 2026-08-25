package com.web.backend.service.impl;

import com.web.backend.common.AuthProvider;
import com.web.backend.common.OtpType;
import com.web.backend.common.UserStatus;
import com.web.backend.common.NotificationsType;
import com.web.backend.config.localresolverconfig.Translator;
import com.web.backend.controller.request.*;
import com.web.backend.controller.response.*;
import com.web.backend.exception.custom.AccessForbiddenException;
import com.web.backend.exception.custom.InvalidDataException;
import com.web.backend.exception.custom.InvalidOtpException;
import com.web.backend.exception.custom.InvalidPasswordException;
import com.web.backend.exception.custom.PasswordMismatchException;
import com.web.backend.exception.custom.ResourceConflictException;
import com.web.backend.exception.custom.ResourceNotFoundException;
import com.web.backend.kafka.payload.FriendPayload;
import com.web.backend.mapper.UserMapper;
import com.web.backend.model.postgres.AddressEntity;
import com.web.backend.model.postgres.UserEntity;
import com.web.backend.repository.AddressRepository;
import com.web.backend.repository.MessageRepository;
import com.web.backend.repository.UserRepository;
import com.web.backend.repository.FriendshipRepository;
import com.web.backend.service.CuckooFilterService;
import com.web.backend.service.EmailService;
import com.web.backend.service.StorageService;
import com.web.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.multipart.MultipartFile;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j(topic = "USER-SERVICE")
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    private final AddressRepository addressRepository;

    private final MessageRepository messageRepository;

    private final EmailService emailService;

    private final StorageService storageService;

    private final PasswordEncoder passwordEncoder;

    private final UserMapper userMapper;

    private final RedisTemplate<String, Object> redisTemplate;

    private final CuckooFilterService cuckooFilterService;

    private final FriendshipRepository friendshipRepository;

    private final ApplicationEventPublisher eventPublisher;

    private SecureRandom secureRandom = new SecureRandom();

    @Value("${spring.mail.expiration-minutes}")
    private int expirationMinutes;

    @Value("${spring.kafka.topic.friend.friend-topic}")
    private String friendTopic;

    private static final String EMAIL_FILTER_KEY = "filter:emails";
    private static final String USER_DETAILS_STRING = "user_details";
    private static final String USERNAME_STRING = "#username";
    private static final String AVATARS_STRING = "avatars";
    private static final String OTP_STRING = "otp:";
    private static final String COOLDOWN_RESEND_STRING = "cooldown:resend:";
    private static final String ATTEMPTS_SUFFIX_STRING = ":attempts";
    private static final String DELIMITER_COLON_STRING = ":";
    private static final String EMPTY_STRING = "";
    private static final String ONE_STRING = "1";

    private static final String ERROR_USER_NOT_FOUND_WITH_STRING = "error.user.not_found_with";
    private static final String ERROR_AUTH_INVALID_OTP_ATTEMPTS_STRING = "error.auth.invalid_otp_attempts";
    private static final String ERROR_USER_NOT_FOUND_STRING = "error.user.not_found";
    private static final String ERROR_USER_SOCIAL_ACCOUNT_NOT_ALLOWED_STRING = "error.user.social_account_not_allowed";
    private static final String ERROR_USER_PW_INCORRECT_STRING = "error.user.pw_incorrect";
    private static final String ERROR_USER_EMAIL_EXISTS_STRING = "error.user.email_exists";
    private static final String ERROR_USER_ADDRESS_NOT_OWNED_STRING = "error.user.address_not_owned";
    private static final String ERROR_USER_ADDRESS_NOT_FOUND_STRING = "error.user.address_not_found";
    private static final String ERROR_USER_CURRENT_PW_INCORRECT_STRING = "error.user.current_pw_incorrect";
    private static final String ERROR_USER_NEW_PW_SAME_STRING = "error.user.new_pw_same";
    private static final String ERROR_USER_INVALID_NEW_EMAIL_STRING = "error.user.invalid_new_email";
    private static final String ERROR_USER_INVALID_NEW_PHONE_STRING = "error.user.invalid_new_phone";
    private static final String ERROR_USER_REQ_NOT_FOUND_STRING = "error.user.req_not_found";
    private static final String ERROR_USER_MISSING_NEW_EMAIL_STRING = "error.user.missing_new_email";
    private static final String ERROR_AUTH_OTP_EXPIRED_OR_REQ_MISSING_STRING = "error.auth.otp_expired_or_req_missing";
    private static final String ERROR_AUTH_OTP_CANCELED_5_TIMES_STRING = "error.auth.otp_canceled_5_times";
    private static final String ERROR_AUTH_WAIT_60S_STRING = "error.auth.wait_60s";
    private static final String ERROR_AUTH_REQ_EXPIRED_STRING = "error.auth.req_expired";

    private static final String SYS_ACCOUNT_STRING = "sys.account";
    private static final String SYS_DELETED_STRING = "sys.deleted";

    @Override
    @Transactional(readOnly = true)
    public UserResponse getMe(String username) {
        UserEntity user = userRepository.findWithAuthoritiesByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException(
                        Translator.tolocale(ERROR_USER_NOT_FOUND_WITH_STRING, username)));

        if (user.getUserStatus() != UserStatus.ACTIVE) {
            throw new ResourceNotFoundException(Translator.tolocale(ERROR_USER_NOT_FOUND_WITH_STRING, username));
        }

        return userMapper.toUserResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetailResponse getProfileUser(String username) {
        UserEntity user = userRepository.findWithAuthoritiesByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException(
                        Translator.tolocale(ERROR_USER_NOT_FOUND_WITH_STRING, username)));

        if (user.getUserStatus() != UserStatus.ACTIVE) {
            throw new ResourceNotFoundException(Translator.tolocale(ERROR_USER_NOT_FOUND_WITH_STRING, username));
        }

        return userMapper.toUserDetailResponse(user);
    }

    @Override
    @Transactional
    @CacheEvict(value = USER_DETAILS_STRING, key = USERNAME_STRING)
    public UserDetailResponse updateUser(String username, UpdateUserRequest request) {
        UserEntity userEntity = userRepository.findWithAuthoritiesByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException(
                        Translator.tolocale(ERROR_USER_NOT_FOUND_WITH_STRING, username)));

        userMapper.updateUserFromRequest(request, userEntity);

        UserEntity updatedUser = userRepository.save(Objects.requireNonNull(userEntity));
        log.info("User '{}' updated profile", username);
        return userMapper.toUserDetailResponse(updatedUser);
    }

    @Override
    @CacheEvict(value = USER_DETAILS_STRING, key = USERNAME_STRING)
    @Transactional
    public String updateAvatar(String username, MultipartFile avatarFile) {
        String oldAvatar = userRepository.findAvatarByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException(Translator.tolocale(ERROR_USER_NOT_FOUND_STRING)));
        String newUrl = storageService.uploadAvatar(avatarFile);

        if (oldAvatar != null) {
            try {
                storageService.delete(oldAvatar, AVATARS_STRING);
            } catch (Exception e) {
                log.warn("Failed to delete old avatar image from storage: '{}'", oldAvatar, e);
            }
        }
        userRepository.updateAvatar(username, newUrl);
        log.info("User '{}' updated avatar", username);
        return newUrl;
    }

    private void generateAndSenResponseToken(UserEntity user, OtpType type, String extraData, String targetEmail) {

        String otp = String.valueOf(100000 + this.secureRandom.nextInt(900000));

        String redisKey = OTP_STRING + type.name() + DELIMITER_COLON_STRING + user.getUsername();

        String redisValue = otp + (extraData != null ? DELIMITER_COLON_STRING + extraData : EMPTY_STRING);

        redisTemplate.opsForValue().set(redisKey, redisValue, expirationMinutes, TimeUnit.MINUTES);

        emailService.sendOtpEmail(targetEmail, user.getUsername(), otp);
    }

    @Override
    @Transactional
    public void initiateEmailChange(String username, String newEmail, String currentPassword) {
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException(Translator.tolocale(ERROR_USER_NOT_FOUND_STRING)));

        if (user.getAuthProvider() != AuthProvider.LOCAL) {
            throw new AccessForbiddenException(Translator.tolocale(ERROR_USER_SOCIAL_ACCOUNT_NOT_ALLOWED_STRING));
        }

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new InvalidPasswordException(Translator.tolocale(ERROR_USER_PW_INCORRECT_STRING));
        }

        if (userRepository.existsByEmail(newEmail)) {
            throw new ResourceConflictException(Translator.tolocale(ERROR_USER_EMAIL_EXISTS_STRING));
        }

        generateAndSenResponseToken(user, OtpType.EMAIL_CHANGE, newEmail, newEmail);

        log.info("User '{}' initiated email change to '{}'", username, newEmail);
    }

    @Override
    @Transactional
    public void initiatePhoneChange(String username, String newPhone, String currentPassword) {
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException(Translator.tolocale(ERROR_USER_NOT_FOUND_STRING)));

        if (user.getAuthProvider() != AuthProvider.LOCAL) {
            throw new AccessForbiddenException(Translator.tolocale(ERROR_USER_SOCIAL_ACCOUNT_NOT_ALLOWED_STRING));
        }

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new InvalidPasswordException(Translator.tolocale(ERROR_USER_PW_INCORRECT_STRING));
        }

        generateAndSenResponseToken(user, OtpType.PHONE_CHANGE, newPhone, user.getEmail());

        log.info("User '{}' initiated phone change", username);
    }

    @Override
    @Transactional
    public UserDetailResponse addAddress(String username, AddressRequest request) {
        UserEntity user = userRepository.findWithAuthoritiesByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException(
                        Translator.tolocale(ERROR_USER_NOT_FOUND_WITH_STRING, username)));
        AddressEntity newAddress = userMapper.toAddressEntity(request);

        user.addAddress(newAddress);
        addressRepository.save(newAddress);
        log.info("User '{}' added new address", username);
        return userMapper.toUserDetailResponse(user);
    }

    @Override
    @Transactional
    @CacheEvict(value = USER_DETAILS_STRING, key = USERNAME_STRING)
    public UserDetailResponse updateAddress(String username, Long addressId, AddressRequest request) {
        UserEntity user = userRepository.findWithAuthoritiesByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException(
                        Translator.tolocale(ERROR_USER_NOT_FOUND_WITH_STRING, username)));

        AddressEntity addressToUpdate = addressRepository.findByIdAndUser_Username(addressId, username)
                .orElseThrow(
                        () -> new ResourceNotFoundException(Translator.tolocale(ERROR_USER_ADDRESS_NOT_OWNED_STRING)));

        userMapper.updateAddressFromRequest(request, addressToUpdate);

        addressRepository.save(addressToUpdate);
        log.info("User '{}' updated address id={}", username, addressId);
        return userMapper.toUserDetailResponse(user);
    }

    @Override
    @Transactional
    @CacheEvict(value = USER_DETAILS_STRING, key = USERNAME_STRING)
    public UserDetailResponse deleteAddress(String username, Long addressId) {
        UserEntity user = userRepository.findWithAuthoritiesByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException(
                        Translator.tolocale(ERROR_USER_NOT_FOUND_WITH_STRING, username)));

        AddressEntity addressToDelete = addressRepository.findByIdAndUser_Username(addressId, username)
                .orElseThrow(
                        () -> new ResourceNotFoundException(Translator.tolocale(ERROR_USER_ADDRESS_NOT_FOUND_STRING)));

        user.removeAddress(addressToDelete);
        addressRepository.delete(addressToDelete);

        log.info("User '{}' deleted address id={}", username, addressId);
        return userMapper.toUserDetailResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AddressResponse> getAllAddresses(String username) {
        if (!userRepository.existsByUsername(username)) {
            throw new ResourceNotFoundException(
                    Translator.tolocale(ERROR_USER_NOT_FOUND_WITH_STRING, username));
        }

        return addressRepository.findAddressResponsesByUsername(username);
    }

    @Override
    @Transactional(readOnly = true)
    public AddressResponse getAddressById(String username, Long addressId) {
        if (!userRepository.existsByUsername(username)) {
            throw new ResourceNotFoundException(
                    Translator.tolocale(ERROR_USER_NOT_FOUND_WITH_STRING, username));
        }

        return addressRepository.findAddressResponseByIdAndUsername(addressId, username)
                .orElseThrow(
                        () -> new AccessForbiddenException(Translator.tolocale(ERROR_USER_ADDRESS_NOT_OWNED_STRING)));
    }

    @Override
    @Transactional
    @CacheEvict(value = USER_DETAILS_STRING, key = USERNAME_STRING)
    public void deleteUser(String username) {
        UserEntity userEntity = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException(
                        Translator.tolocale(ERROR_USER_NOT_FOUND_WITH_STRING, username)));
        boolean hasChatHistory = messageRepository.existsBySenderOrRecipient(username);

        if (hasChatHistory) {
            userEntity.setUserStatus(UserStatus.INACTIVE);
            userEntity.setOnline(false);
            userEntity.setEmail(null);
            userEntity.setPhone(null);
            userEntity.setFirstName(Translator.tolocale(SYS_ACCOUNT_STRING));
            userEntity.setLastName(Translator.tolocale(SYS_DELETED_STRING));
            userEntity.setAvatar(null);
            userRepository.save(userEntity);
            log.info("Soft-deleted user '{}' (retaining chat history)", username);
        } else {
            userRepository.delete(Objects.requireNonNull(userEntity));
            log.info("Hard-deleted user '{}' (no chat history)", username);
        }
    }

    @Override
    @Transactional
    @CacheEvict(value = USER_DETAILS_STRING, key = USERNAME_STRING)
    public void changePassword(String username, String currentPassword, String newPassword) {
        UserEntity userEntity = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException(
                        Translator.tolocale(ERROR_USER_NOT_FOUND_WITH_STRING, username)));

        if (userEntity.getAuthProvider() != AuthProvider.LOCAL) {
            throw new AccessForbiddenException(Translator.tolocale(ERROR_USER_SOCIAL_ACCOUNT_NOT_ALLOWED_STRING));
        }

        if (!passwordEncoder.matches(currentPassword, userEntity.getPassword())) {
            throw new InvalidPasswordException(Translator.tolocale(ERROR_USER_CURRENT_PW_INCORRECT_STRING));
        }

        if (passwordEncoder.matches(newPassword, userEntity.getPassword())) {
            throw new PasswordMismatchException(Translator.tolocale(ERROR_USER_NEW_PW_SAME_STRING));
        }

        userEntity.setPassword(passwordEncoder.encode(newPassword));

        int currentVersion = userEntity.getTokenVersion() == null ? 0 : userEntity.getTokenVersion();
        userEntity.setTokenVersion(currentVersion + 1);

        userRepository.save(userEntity);
        log.info("User '{}' changed password successfully", username);
    }

    public boolean userExists(String username) {
        return userRepository.existsByUsername(username);
    }

    @Override
    @CacheEvict(value = USER_DETAILS_STRING, key = USERNAME_STRING)
    @Async
    public void setUserOnlineStatus(String username, boolean isOnline) {
        userRepository.updateOnlineStatus(username, isOnline);
        log.debug("Updated online status for user '{}': isOnline={}", username, isOnline);

        List<String> friends = friendshipRepository.findAllFriendUsernamesByUsername(username);

        if (friends != null && !friends.isEmpty()) {
            eventPublisher.publishEvent(FriendPayload.builder()
                    .recipientUsernames(friends)
                    .recipientType(
                            isOnline ? NotificationsType.USER_ONLINE : NotificationsType.USER_OFFLINE)
                    .senderDisplayName(username)
                    .build());
        }
    }

    @Override
    @Transactional
    public void verifyEmailChange(String username, String otp) {
        String oldEmail = userRepository.findEmailByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException(Translator.tolocale(ERROR_USER_NOT_FOUND_STRING)));

        String newEmail = validateRedisOtp(username, OtpType.EMAIL_CHANGE, otp);

        if (newEmail == null || newEmail.isEmpty()) {
            throw new InvalidDataException(Translator.tolocale(ERROR_USER_INVALID_NEW_EMAIL_STRING));
        }

        userRepository.updateEmail(username, newEmail);

        cuckooFilterService.delete(EMAIL_FILTER_KEY, oldEmail);
        cuckooFilterService.add(EMAIL_FILTER_KEY, newEmail);
        log.info("User '{}' verified email change successfully", username);
    }

    @Override
    @Transactional
    public void verifyPhoneChange(String username, String otp) {
        if (!userRepository.existsByUsername(username)) {
            throw new ResourceNotFoundException(Translator.tolocale(ERROR_USER_NOT_FOUND_STRING));
        }

        String newPhone = validateRedisOtp(username, OtpType.PHONE_CHANGE, otp);

        if (newPhone == null || newPhone.isEmpty()) {
            throw new InvalidDataException(Translator.tolocale(ERROR_USER_INVALID_NEW_PHONE_STRING));
        }

        userRepository.updatePhone(username, newPhone);
        log.info("User '{}' verified phone change successfully", username);
    }

    @Override
    @Transactional
    public void resendEmailChangeOtp(String username) {
        if (!userRepository.existsByUsername(username)) {
            throw new ResourceNotFoundException(Translator.tolocale(ERROR_USER_NOT_FOUND_STRING));
        }

        String redisKey = OTP_STRING + OtpType.EMAIL_CHANGE.name() + DELIMITER_COLON_STRING + username;
        String oldValue = (String) redisTemplate.opsForValue().get(redisKey);
        if (oldValue == null)
            throw new ResourceNotFoundException(Translator.tolocale(ERROR_USER_REQ_NOT_FOUND_STRING));

        String[] parts = oldValue.split(DELIMITER_COLON_STRING);
        String newEmail = parts.length > 1 ? parts[1] : null;

        if (newEmail == null)
            throw new InvalidDataException(Translator.tolocale(ERROR_USER_MISSING_NEW_EMAIL_STRING));

        resendRedisOtp(username, OtpType.EMAIL_CHANGE, newEmail);
    }

    @Override
    @Transactional
    public void resendPhoneChangeOtp(String username) {
        String email = userRepository.findEmailByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException(Translator.tolocale(ERROR_USER_NOT_FOUND_STRING)));

        resendRedisOtp(username, OtpType.PHONE_CHANGE, email);
    }

    private String validateRedisOtp(String identifier, OtpType type, String inputOtp) {
        String redisKey = OTP_STRING + type.name() + DELIMITER_COLON_STRING + identifier;

        String attemptKey = redisKey + ATTEMPTS_SUFFIX_STRING;

        String value = (String) redisTemplate.opsForValue().get(redisKey);

        if (value == null) {
            throw new InvalidOtpException(Translator.tolocale(ERROR_AUTH_OTP_EXPIRED_OR_REQ_MISSING_STRING));
        }

        String[] parts = value.split(DELIMITER_COLON_STRING);
        String savedOtp = parts[0];
        String extraData = parts.length > 1 ? parts[1] : null;

        if (!savedOtp.equals(inputOtp)) {
            Long attempts = redisTemplate.opsForValue().increment(attemptKey);
            redisTemplate.expire(attemptKey, 5, TimeUnit.MINUTES);

            if (attempts != null && attempts >= 5) {
                redisTemplate.delete(redisKey);
                redisTemplate.delete(attemptKey);
                throw new InvalidOtpException(Translator.tolocale(ERROR_AUTH_OTP_CANCELED_5_TIMES_STRING));
            }

            throw new InvalidOtpException(Translator.tolocale(ERROR_AUTH_INVALID_OTP_ATTEMPTS_STRING, attempts));
        }

        redisTemplate.delete(redisKey);
        redisTemplate.delete(attemptKey);
        return extraData;
    }

    private void resendRedisOtp(String identifier, OtpType type, String emailToSend) {
        String redisKey = OTP_STRING + type.name() + DELIMITER_COLON_STRING + identifier;

        String cooldownKey = COOLDOWN_RESEND_STRING + identifier;

        if (Boolean.TRUE.equals(redisTemplate.hasKey(cooldownKey))) {
            throw new ResourceConflictException(Translator.tolocale(ERROR_AUTH_WAIT_60S_STRING));
        }

        String oldValue = (String) redisTemplate.opsForValue().get(redisKey);

        if (oldValue == null) {
            throw new ResourceNotFoundException(Translator.tolocale(ERROR_AUTH_REQ_EXPIRED_STRING));
        }

        String[] parts = oldValue.split(DELIMITER_COLON_STRING);
        String extraData = parts.length > 1 ? parts[1] : EMPTY_STRING;

        String newOtp = String.valueOf(100000 + this.secureRandom.nextInt(900000));

        String newValue = newOtp + (extraData.isEmpty() ? EMPTY_STRING : DELIMITER_COLON_STRING + extraData);

        redisTemplate.opsForValue().set(redisKey, newValue, expirationMinutes, TimeUnit.MINUTES);

        String usernameForMail = identifier;

        if (type == OtpType.PASSWORD_RESET) {
            Optional<UserEntity> u = userRepository.findByEmail(identifier);
            if (u.isPresent())
                usernameForMail = u.get().getUsername();
        }
        redisTemplate.opsForValue().set(cooldownKey, ONE_STRING, 60, TimeUnit.SECONDS);
        emailService.sendOtpEmail(emailToSend, usernameForMail, newOtp);
        log.info("Resent {} OTP to '{}'", type, emailToSend);
    }
}
