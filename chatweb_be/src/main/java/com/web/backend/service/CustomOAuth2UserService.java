package com.web.backend.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.web.backend.common.AuthProvider;
import com.web.backend.common.UserStatus;
import com.web.backend.model.postgres.UserEntity;
import com.web.backend.oauth2.CustomOAuth2User;
import com.web.backend.oauth2.userinfo.OAuth2UserInfo;
import com.web.backend.oauth2.userinfo.OAuth2UserInfoFactory;
import com.web.backend.repository.RoleRepository;
import com.web.backend.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j(topic = "CUSTOM-OAUTH2-USER-SERVICE")
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    private final RoleRepository roleRepository;

    private final PasswordEncoder passwordEncoder;

    private static final String ERROR_OAUTH2_EMAIL_ALREADY_EXISTS_STRING = "error.oauth2.email_already_exists";
    private static final String ERROR_OAUTH2_EMAIL_MISSING_STRING = "error.oauth2.email_missing";
    private static final String ERROR_ROLE_NOT_FOUND_STRING = "error.role.not_found";

    private static final String USER_STRING = "USER";
    private static final String REGEX_WHITESPACE_STRING = "\\s+";
    private static final String EMPTY_STRING = "";
    private static final String DEFAULT_USERNAME_PREFIX_STRING = "user";
    private static final String DELIMITER_UNDERSCORE_STRING = "_";

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        OAuth2UserInfo userInfo = OAuth2UserInfoFactory.getOAuth2UserInfo(registrationId, oAuth2User.getAttributes());

        if (userInfo.getEmail() == null || userInfo.getEmail().isEmpty()) {
            log.error("OAuth2 authentication failed: Provider returned empty email [providerId={}]", registrationId);
            throw new OAuth2AuthenticationException(ERROR_OAUTH2_EMAIL_MISSING_STRING);
        }

        log.info("Processing OAuth2 login for user '{}'", userInfo.getEmail());
        UserEntity user = processOAuth2User(userRequest, userInfo);
        return new CustomOAuth2User(user, oAuth2User.getAttributes());
    }

    private UserEntity processOAuth2User(OAuth2UserRequest userRequest, OAuth2UserInfo userInfo) {
        Optional<UserEntity> userByProviderId = userRepository.findByProviderId(userInfo.getId());

        if (userByProviderId.isPresent()) {
            log.debug("Found existing user by OAuth2 providerId '{}'", userInfo.getId());
            return userByProviderId.get();
        }

        Optional<UserEntity> userByEmail = userRepository.findByEmail(userInfo.getEmail());
        if (userByEmail.isPresent()) {
            log.warn("OAuth2 login conflict: Email '{}' is already registered with another auth provider",
                    userInfo.getEmail());
            throw new OAuth2AuthenticationException(ERROR_OAUTH2_EMAIL_ALREADY_EXISTS_STRING);
        }

        log.info("Registering new OAuth2 user with email '{}'", userInfo.getEmail());
        return registerNewUser(userRequest, userInfo);
    }

    private UserEntity registerNewUser(OAuth2UserRequest userRequest, OAuth2UserInfo userInfo) {
        UserEntity newUser = new UserEntity();

        newUser.setEmail(userInfo.getEmail());
        newUser.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        newUser.setProviderId(userInfo.getId());
        newUser.setAuthProvider(determineAuthProvider(userRequest));
        newUser.setUserStatus(UserStatus.ACTIVE);

        String username = userInfo.getName();
        if (username != null) {
            username = username.replaceAll(REGEX_WHITESPACE_STRING, EMPTY_STRING);
        } else {
            username = DEFAULT_USERNAME_PREFIX_STRING;
        }
        newUser.setUsername(username + DELIMITER_UNDERSCORE_STRING + System.currentTimeMillis());

        newUser.setRole(roleRepository.findByNameOauth2(USER_STRING)
                .orElseThrow(() -> {
                    log.error("Failed to assign default role: Role 'USER' not found in database");
                    return new OAuth2AuthenticationException(ERROR_ROLE_NOT_FOUND_STRING);
                }));

        newUser.setFirstName(userInfo.getFirstName());
        newUser.setLastName(userInfo.getLastName());
        newUser.setAvatar(userInfo.getAvatarUrl());

        return userRepository.save(newUser);
    }

    private AuthProvider determineAuthProvider(OAuth2UserRequest userRequest) {
        String registrationId = userRequest.getClientRegistration().getRegistrationId().toUpperCase();
        try {
            return AuthProvider.valueOf(registrationId);
        } catch (IllegalArgumentException e) {
            log.warn("Unknown AuthProvider '{}'. Falling back to GOOGLE", registrationId);
            return AuthProvider.GOOGLE;
        }
    }
}
