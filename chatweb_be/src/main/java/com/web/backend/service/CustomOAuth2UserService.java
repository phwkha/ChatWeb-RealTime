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

    private static final String EMAIL_STRING = "email";
    private static final String FACEBOOK_STRING = "facebook";
    private static final String FAMILY_NAME_STRING = "family_name";
    private static final String GITHUB_STRING = "github";
    private static final String GIVEN_NAME_STRING = "given_name";
    private static final String GOOGLE_STRING = "google";
    private static final String NAME_STRING = "name";
    private static final String PICTURE_STRING = "picture";
    private static final String SUB_STRING = "sub";

    private static final String ID_STRING = "id";
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
        String providerId = extractProviderId(oAuth2User, registrationId);
        String email = oAuth2User.getAttribute(EMAIL_STRING);

        if (email == null || email.isEmpty()) {
            log.error("OAuth2 authentication failed: Provider returned empty email [providerId={}]", providerId);
            throw new OAuth2AuthenticationException(ERROR_OAUTH2_EMAIL_MISSING_STRING);
        }

        log.info("Processing OAuth2 login for user '{}'", email);
        UserEntity user = processOAuth2User(userRequest, oAuth2User, providerId, email);
        return new CustomOAuth2User(user, oAuth2User.getAttributes());
    }

    private String extractProviderId(OAuth2User oAuth2User, String registrationId) {
        if (GOOGLE_STRING.equalsIgnoreCase(registrationId)) {
            return oAuth2User.getAttribute(SUB_STRING);
        } else if (GITHUB_STRING.equalsIgnoreCase(registrationId) || FACEBOOK_STRING.equalsIgnoreCase(registrationId)) {
            Object idAttr = oAuth2User.getAttribute(ID_STRING);
            return idAttr != null ? String.valueOf(idAttr) : null;
        }

        Object sub = oAuth2User.getAttribute(SUB_STRING);
        if (sub != null)
            return String.valueOf(sub);
        Object id = oAuth2User.getAttribute(ID_STRING);
        return id != null ? String.valueOf(id) : null;
    }

    private UserEntity processOAuth2User(OAuth2UserRequest userRequest, OAuth2User oAuth2User, String providerId,
            String email) {
        Optional<UserEntity> userByProviderId = userRepository.findByProviderId(providerId);

        if (userByProviderId.isPresent()) {
            log.debug("Found existing user by OAuth2 providerId '{}'", providerId);
            return userByProviderId.get();
        }

        Optional<UserEntity> userByEmail = userRepository.findByEmail(email);
        if (userByEmail.isPresent()) {
            log.warn("OAuth2 login conflict: Email '{}' is already registered with another auth provider", email);
            throw new OAuth2AuthenticationException(ERROR_OAUTH2_EMAIL_ALREADY_EXISTS_STRING);
        }

        log.info("Registering new OAuth2 user with email '{}'", email);
        return registerNewUser(userRequest, oAuth2User, providerId, email);
    }

    private UserEntity registerNewUser(OAuth2UserRequest userRequest, OAuth2User oAuth2User, String providerId,
            String email) {
        UserEntity newUser = new UserEntity();

        newUser.setEmail(email);
        newUser.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        newUser.setProviderId(providerId);
        newUser.setAuthProvider(determineAuthProvider(userRequest));
        newUser.setUserStatus(UserStatus.ACTIVE);

        String username = oAuth2User.getAttribute(NAME_STRING);
        if (username != null) {
            username = username.replaceAll(REGEX_WHITESPACE_STRING, EMPTY_STRING);
        } else {
            username = DEFAULT_USERNAME_PREFIX_STRING;
        }
        newUser.setUsername(username + DELIMITER_UNDERSCORE_STRING + System.currentTimeMillis());

        newUser.setRole(roleRepository.findByName(USER_STRING)
                .orElseThrow(() -> {
                    log.error("Failed to assign default role: Role 'USER' not found in database");
                    return new OAuth2AuthenticationException(ERROR_ROLE_NOT_FOUND_STRING);
                }));

        newUser.setFirstName(oAuth2User.getAttribute(GIVEN_NAME_STRING));
        newUser.setLastName(oAuth2User.getAttribute(FAMILY_NAME_STRING));
        newUser.setAvatar(oAuth2User.getAttribute(PICTURE_STRING));

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
