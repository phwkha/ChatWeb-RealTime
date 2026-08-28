package com.web.backend.service.impl;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.web.backend.common.AuthProvider;
import com.web.backend.common.OtpType;
import com.web.backend.common.TokenType;
import com.web.backend.common.UserStatus;
import com.web.backend.config.localresolverconfig.Translator;
import com.web.backend.controller.request.CreateUserRequest;
import com.web.backend.controller.request.LoginRequest;
import com.web.backend.controller.request.VerifyOtpRequest;
import com.web.backend.model.redis.RegisterData;
import com.web.backend.controller.response.LoginResponse;
import com.web.backend.controller.response.TokenResponse;
import com.web.backend.controller.response.UserResponse;
import com.web.backend.exception.custom.AccessForbiddenException;
import com.web.backend.exception.custom.AuthenticationFailedException;
import com.web.backend.exception.custom.InvalidDataException;
import com.web.backend.exception.custom.InvalidOtpException;
import com.web.backend.exception.custom.ResourceConflictException;
import com.web.backend.exception.custom.ResourceNotFoundException;
import com.web.backend.kafka.producer.EmailProducer;
import com.web.backend.mapper.UserMapper;
import com.web.backend.model.postgres.RoleEntity;
import com.web.backend.model.postgres.UserEntity;
import com.web.backend.repository.RoleRepository;
import com.web.backend.repository.UserRepository;
import com.web.backend.service.AuthenticationService;
import com.web.backend.service.CuckooFilterService;
import com.web.backend.service.JwtService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j(topic = "AUTHENTICATION-SERVICE")
@RequiredArgsConstructor
public class AuthenticationServiceImpl implements AuthenticationService {

    private final UserRepository userRepository;

    private final AuthenticationManager authenticationManager;

    private final UserMapper userMapper;

    private final JwtService jwtService;

    private final CacheManager cacheManager;

    private final EmailProducer emailKafkaProducer;

    private final PasswordEncoder passwordEncoder;

    private final RoleRepository roleRepository;

    private final RedisTemplate<String, Object> redisTemplate;

    private final CuckooFilterService cuckooFilterService;

    private SecureRandom secureRandom = new SecureRandom();

    @Value("${spring.mail.expiration-minutes}")
    private int expirationMinutes;

    private static final String EMAIL_FILTER_KEY = "filter:emails";
    private static final String USERNAME_FILTER_KEY = "filter:usernames";

    private static final String LOGGED_OUT_STRING = "logged_out";
    private static final String ROTATED_STRING = "rotated";

    private static final String USER_STRING = "USER";

    private static final String REGISTER_STRING = "register:";
    private static final String BLACKLIST_STRING = "blacklist:";
    private static final String USER_DETAILS_STRING = "user_details";
    private static final String COOLDOWN_RESEND_STRING = "cooldown:resend:";
    private static final String OTP_PREFIX_STRING = "otp:";
    private static final String ATTEMPTS_SUFFIX_STRING = ":attempts";
    private static final String DELIMITER_COLON_STRING = ":";
    private static final String EMPTY_STRING = "";
    private static final String ONE_STRING = "1";
    private static final String TOKEN_VERSION_CLAIM_STRING = "v";

    private static final String ERROR_AUTH_INVALID_OTP_ATTEMPTS_STRING = "error.auth.invalid_otp_attempts";
    private static final String ERROR_AUTH_EMAIL_USED_STRING = "error.auth.email_used";
    private static final String ERROR_AUTH_USERNAME_EXISTS_STRING = "error.auth.username_exists";
    private static final String ERROR_AUTH_ROLE_USER_MISSING_STRING = "error.auth.role_user_missing";
    private static final String ERROR_AUTH_ACCOUNT_LOCKED_STRING = "error.auth.account_locked";
    private static final String ERROR_AUTH_INVALID_CREDENTIALS_STRING = "error.auth.invalid_credentials";
    private static final String ERROR_USER_NOT_FOUND_STRING = "error.user.not_found";
    private static final String ERROR_AUTH_MISSING_REFRESH_STRING = "error.auth.missing_refresh";
    private static final String ERROR_AUTH_REFRESH_REVOKED_STRING = "error.auth.refresh_revoked";
    private static final String ERROR_AUTH_REFRESH_EXPIRED_STRING = "error.auth.refresh_expired";
    private static final String ERROR_AUTH_EMAIL_NOT_FOUND_STRING = "error.auth.email_not_found";
    private static final String ERROR_AUTH_OTP_EXPIRED_OR_EMAIL_MISSING_STRING = "error.auth.otp_expired_or_email_missing";
    private static final String ERROR_AUTH_INVALID_OTP_STRING = "error.auth.invalid_otp";
    private static final String ERROR_AUTH_REGISTERED_BY_OTHER_STRING = "error.auth.registered_by_other";
    private static final String ERROR_ROLE_NOT_FOUND_STRING = "error.role.not_found";
    private static final String ERROR_ROLE_DEFAULT_NOT_FOUND_STRING = "error.role.default_not_found";
    private static final String ERROR_AUTH_WAIT_60S_STRING = "error.auth.wait_60s";
    private static final String ERROR_AUTH_ALREADY_ACTIVE_STRING = "error.auth.already_active";
    private static final String ERROR_AUTH_ACCOUNT_LOCKED_DELETED_STRING = "error.auth.account_locked_deleted";
    private static final String ERROR_AUTH_REG_EXPIRED_STRING = "error.auth.reg_expired";
    private static final String ERROR_USER_EMAIL_USER_NOT_FOUND_STRING = "error.user.email_user_not_found";
    private static final String ERROR_AUTH_OTP_EXPIRED_OR_REQ_MISSING_STRING = "error.auth.otp_expired_or_req_missing";
    private static final String ERROR_AUTH_OTP_CANCELED_5_TIMES_STRING = "error.auth.otp_canceled_5_times";
    private static final String ERROR_AUTH_REQ_EXPIRED_STRING = "error.auth.req_expired";

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserResponse createUser(CreateUserRequest createUserRequest) {

        boolean mightExistEmail = cuckooFilterService.exists(EMAIL_FILTER_KEY, createUserRequest.getEmail());
        if (mightExistEmail && userRepository.existsByEmail(createUserRequest.getEmail())) {
            throw new ResourceConflictException(Translator.tolocale(ERROR_AUTH_EMAIL_USED_STRING));
        }

        boolean mightExistUsername = cuckooFilterService.exists(USERNAME_FILTER_KEY, createUserRequest.getUsername());
        if (mightExistUsername && userRepository.existsByUsername(createUserRequest.getUsername())) {
            throw new ResourceConflictException(Translator.tolocale(ERROR_AUTH_USERNAME_EXISTS_STRING));
        }

        String otp = String.valueOf(100000 + this.secureRandom.nextInt(900000));
        RoleEntity defaultRole = roleRepository.findByName(USER_STRING)
                .orElseThrow(
                        () -> new ResourceNotFoundException(Translator.tolocale(ERROR_AUTH_ROLE_USER_MISSING_STRING)));

        RegisterData data = RegisterData.builder()
                .username(createUserRequest.getUsername())
                .email(createUserRequest.getEmail())
                .password(passwordEncoder.encode(createUserRequest.getPassword()))
                .roleId(defaultRole.getId())
                .otp(otp)
                .build();

        String redisKey = REGISTER_STRING + createUserRequest.getEmail();
        redisTemplate.opsForValue().set(redisKey, Objects.requireNonNull(data), 5, TimeUnit.MINUTES);

        emailKafkaProducer.sendOtpEmailTask(createUserRequest.getEmail(), createUserRequest.getUsername(), otp);

        log.info("User '{}' initiated registration [email='{}']", createUserRequest.getUsername(),
                createUserRequest.getEmail());
        return UserResponse.builder()
                .username(createUserRequest.getUsername())
                .email(createUserRequest.getEmail())
                .userStatus(UserStatus.UNVERIFIED)
                .build();
    }

    @Override
    public LoginResponse login(LoginRequest loginRequest) {
        List<String> authorities = new ArrayList<>();

        Integer tokenVersion;
        UserEntity userPrincipal;
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getUsername(),
                            loginRequest.getPassword()));
            authentication.getAuthorities().forEach(authority -> authorities.add(authority.getAuthority()));
            userPrincipal = (UserEntity) authentication.getPrincipal();
            tokenVersion = userPrincipal.getTokenVersion();
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (LockedException e) {
            throw new AccessForbiddenException(Translator.tolocale(ERROR_AUTH_ACCOUNT_LOCKED_STRING));
        } catch (AuthenticationException e) {
            throw new AuthenticationFailedException(Translator.tolocale(ERROR_AUTH_INVALID_CREDENTIALS_STRING));
        }

        String accessToken = jwtService.generateAccessToken(
                loginRequest.getUsername(),
                authorities,
                tokenVersion

        );

        String refreshToken = jwtService.generateRefreshToken(
                loginRequest.getUsername(),
                authorities,
                tokenVersion);

        UserResponse userResponse = userMapper.toUserResponse(userPrincipal);
        log.info("User '{}' logged in successfully", loginRequest.getUsername());
        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userResponse(userResponse)
                .build();
    }

    @Override
    public void logout(String token, TokenType tokenType) {
        long remainingTime = jwtService.getRemainingTime(token, tokenType);

        if (remainingTime > 0) {
            String key = BLACKLIST_STRING + token;
            redisTemplate.opsForValue().set(key, LOGGED_OUT_STRING, remainingTime, TimeUnit.MILLISECONDS);
        }
        log.debug("Access token added to blacklist with TTL: {} ms", remainingTime);
    }

    @Override
    @Transactional
    public void logoutAllDevices(String username) {
        if (!userRepository.existsByUsername(username)) {
            throw new ResourceNotFoundException(Translator.tolocale(ERROR_USER_NOT_FOUND_STRING));
        }

        userRepository.incrementTokenVersion(username);

        Cache userCache = cacheManager.getCache(USER_DETAILS_STRING);
        if (userCache != null) {
            userCache.evict(Objects.requireNonNull(username));
        }
        log.info("User '{}' logged out from all devices (token version incremented)", username);
    }

    @Override
    public TokenResponse refreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isEmpty()) {
            throw new InvalidDataException(Translator.tolocale(ERROR_AUTH_MISSING_REFRESH_STRING));
        }
        String username = jwtService.extractUsername(refreshToken, TokenType.REFRESH_TOKEN);
        UserEntity user = userRepository.findWithAuthoritiesByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException(Translator.tolocale(ERROR_USER_NOT_FOUND_STRING)));

        String blacklistKey = BLACKLIST_STRING + refreshToken;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(blacklistKey))) {
            user.setTokenVersion(user.getTokenVersion() == null ? 0 : user.getTokenVersion() + 1);
            userRepository.save(user);
            Cache userCache = cacheManager.getCache(USER_DETAILS_STRING);
            if (userCache != null && user.getUsername() != null) {
                userCache.evict(Objects.requireNonNull(user.getUsername()));
            }
            throw new AccessForbiddenException(Translator.tolocale(ERROR_AUTH_REFRESH_REVOKED_STRING));
        }

        Integer tokenVersionInJwt = jwtService.extractClaim(refreshToken, TokenType.REFRESH_TOKEN,
                claims -> claims.get(TOKEN_VERSION_CLAIM_STRING, Integer.class));
        Integer currentVersion = user.getTokenVersion() == null ? 0 : user.getTokenVersion();

        if (tokenVersionInJwt == null || !tokenVersionInJwt.equals(currentVersion)) {
            throw new AccessForbiddenException(Translator.tolocale(ERROR_AUTH_REFRESH_EXPIRED_STRING));
        }

        List<String> authorities = new ArrayList<>();
        user.getAuthorities().forEach(authority -> authorities.add(authority.getAuthority()));
        if (user.getUserStatus() == UserStatus.INACTIVE || user.getUserStatus() == UserStatus.LOCKED) {
            throw new AccessForbiddenException(Translator.tolocale(ERROR_AUTH_ACCOUNT_LOCKED_DELETED_STRING));
        }

        String newAccessToken = jwtService.generateAccessToken(user.getUsername(), authorities, currentVersion);
        String newRefreshToken = jwtService.generateRefreshToken(user.getUsername(), authorities, currentVersion);

        long remainingTime = jwtService.getRemainingTime(refreshToken, TokenType.REFRESH_TOKEN);
        if (remainingTime > 0) {
            redisTemplate.opsForValue().set(blacklistKey, ROTATED_STRING, remainingTime, TimeUnit.MILLISECONDS);
        }

        log.info("User '{}' refreshed access token", username);
        return TokenResponse.builder().accessToken(newAccessToken).refreshToken(newRefreshToken).build();
    }

    @Override
    @Transactional
    public void initiateForgotPassword(String email) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(
                        () -> new ResourceNotFoundException(Translator.tolocale(ERROR_AUTH_EMAIL_NOT_FOUND_STRING)));

        if (user.getUserStatus() == UserStatus.INACTIVE || user.getUserStatus() == UserStatus.LOCKED) {
            throw new AccessForbiddenException(Translator.tolocale(ERROR_AUTH_ACCOUNT_LOCKED_DELETED_STRING));
        }

        generateAndSenResponseToken(user, OtpType.PASSWORD_RESET, null, user.getEmail());

        log.info("Password reset initiated for email '{}'", email);
    }

    private void generateAndSenResponseToken(UserEntity user, OtpType type, String extraData, String targetEmail) {

        String otp = String.valueOf(100000 + this.secureRandom.nextInt(900000));

        String redisKey = OTP_PREFIX_STRING + type.name() + DELIMITER_COLON_STRING + user.getUsername();

        String redisValue = otp + (extraData != null ? DELIMITER_COLON_STRING + extraData : EMPTY_STRING);

        redisTemplate.opsForValue().set(redisKey, redisValue, expirationMinutes, TimeUnit.MINUTES);

        emailKafkaProducer.sendOtpEmailTask(targetEmail, user.getUsername(), otp);
    }

    @Override
    @Transactional
    public void verifyUser(VerifyOtpRequest request) {
        String redisKey = REGISTER_STRING + request.getEmail();

        RegisterData data = (RegisterData) redisTemplate.opsForValue().get(redisKey);

        if (data == null) {
            throw new ResourceNotFoundException(Translator.tolocale(ERROR_AUTH_OTP_EXPIRED_OR_EMAIL_MISSING_STRING));
        }

        if (!data.getOtp().equals(request.getOtp())) {
            throw new InvalidOtpException(Translator.tolocale(ERROR_AUTH_INVALID_OTP_STRING));
        }

        if (userRepository.existsByUsername(data.getUsername()) || userRepository.existsByEmail(data.getEmail())) {
            throw new ResourceConflictException(Translator.tolocale(ERROR_AUTH_REGISTERED_BY_OTHER_STRING));
        }

        UserEntity newUser = new UserEntity();
        newUser.setUsername(data.getUsername());
        newUser.setEmail(data.getEmail());
        newUser.setPassword(data.getPassword());
        newUser.setUserStatus(UserStatus.ACTIVE);
        newUser.setAuthProvider(AuthProvider.LOCAL);
        newUser.setCreateAt(Instant.now());

        Long roleId = data.getRoleId();
        RoleEntity role;
        if (roleId != null) {
            role = roleRepository.findById(roleId)
                    .orElseThrow(() -> new ResourceNotFoundException(Translator.tolocale(ERROR_ROLE_NOT_FOUND_STRING)));
        } else {
            role = roleRepository.findByName(USER_STRING)
                    .orElseThrow(
                            () -> new ResourceNotFoundException(
                                    Translator.tolocale(ERROR_ROLE_DEFAULT_NOT_FOUND_STRING)));
        }
        newUser.setRole(role);

        userRepository.save(Objects.requireNonNull(newUser));

        cuckooFilterService.add(USERNAME_FILTER_KEY, newUser.getUsername());
        cuckooFilterService.add(EMAIL_FILTER_KEY, newUser.getEmail());

        redisTemplate.delete(redisKey);

        log.info("User '{}' verified and activated account with role '{}'", newUser.getUsername(), role.getName());
    }

    @Override
    @Transactional
    public void resendOtp(String email) {
        String redisKey = REGISTER_STRING + email;

        String cooldownKey = COOLDOWN_RESEND_STRING + email;

        if (Boolean.TRUE.equals(redisTemplate.hasKey(cooldownKey))) {
            throw new ResourceConflictException(Translator.tolocale(ERROR_AUTH_WAIT_60S_STRING));
        }

        RegisterData data = (RegisterData) redisTemplate.opsForValue().get(redisKey);

        if (data != null) {
            String otp = String.valueOf(100000 + this.secureRandom.nextInt(900000));

            data.setOtp(otp);
            redisTemplate.opsForValue().set(redisKey, Objects.requireNonNull(data), expirationMinutes,
                    TimeUnit.MINUTES);
            redisTemplate.opsForValue().set(cooldownKey, ONE_STRING, 60, TimeUnit.SECONDS);
            emailKafkaProducer.sendOtpEmailTask(data.getEmail(), data.getUsername(), otp);
            log.info("Resent registration OTP for user '{}' to email '{}'", data.getUsername(), data.getEmail());
            return;
        }

        Optional<UserEntity> userOpt = userRepository.findByEmail(email);
        if (userOpt.isPresent()) {
            UserEntity user = userOpt.get();
            if (user.getUserStatus() == UserStatus.ACTIVE) {
                throw new ResourceConflictException(Translator.tolocale(ERROR_AUTH_ALREADY_ACTIVE_STRING));
            }
            if (user.getUserStatus() == UserStatus.INACTIVE || user.getUserStatus() == UserStatus.LOCKED) {
                throw new ResourceConflictException(Translator.tolocale(ERROR_AUTH_ACCOUNT_LOCKED_DELETED_STRING));
            }
        }

        throw new ResourceNotFoundException(Translator.tolocale(ERROR_AUTH_REG_EXPIRED_STRING));
    }

    @Override
    @Transactional
    public void verifyPasswordReset(String email, String otp, String newPassword) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException(Translator.tolocale(ERROR_USER_NOT_FOUND_STRING)));

        if (user.getUserStatus() == UserStatus.INACTIVE || user.getUserStatus() == UserStatus.LOCKED) {
            throw new AccessForbiddenException(Translator.tolocale(ERROR_AUTH_ACCOUNT_LOCKED_DELETED_STRING));
        }

        validateRedisOtp(user.getUsername(), OtpType.PASSWORD_RESET, otp);

        user.setTokenVersion((user.getTokenVersion() == null ? 0 : user.getTokenVersion()) + 1);
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        Cache userCache = cacheManager.getCache(USER_DETAILS_STRING);
        if (userCache != null && user.getUsername() != null) {
            userCache.evict(Objects.requireNonNull(user.getUsername()));
        }
        log.info("Password reset completed successfully for user '{}'", user.getUsername());
    }

    @Override
    @Transactional
    public void resendForgotPasswordOtp(String email) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(
                        () -> new ResourceNotFoundException(
                                Translator.tolocale(ERROR_USER_EMAIL_USER_NOT_FOUND_STRING)));

        if (user.getUserStatus() == UserStatus.INACTIVE || user.getUserStatus() == UserStatus.LOCKED) {
            throw new AccessForbiddenException(Translator.tolocale(ERROR_AUTH_ACCOUNT_LOCKED_DELETED_STRING));
        }

        resendRedisOtp(user.getUsername(), OtpType.PASSWORD_RESET, email);
    }

    private String validateRedisOtp(String identifier, OtpType type, String inputOtp) {
        String redisKey = OTP_PREFIX_STRING + type.name() + DELIMITER_COLON_STRING + identifier;

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
        String redisKey = OTP_PREFIX_STRING + type.name() + DELIMITER_COLON_STRING + identifier;

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
        emailKafkaProducer.sendOtpEmailTask(emailToSend, usernameForMail, newOtp);
        log.info("Resent {} OTP to '{}'", type, emailToSend);
    }

}
