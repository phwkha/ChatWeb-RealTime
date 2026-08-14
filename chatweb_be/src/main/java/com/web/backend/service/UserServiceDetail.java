package com.web.backend.service;

import com.web.backend.config.localresolverconfig.Translator;
import com.web.backend.common.UserStatus;
import com.web.backend.model.UserEntity;
import com.web.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j(topic = "USER-SERVICE-DETAIL")
public class UserServiceDetail implements UserDetailsService {

    private final UserRepository userRepository;

    private static final String USER_DETAILS_STRING = "user_details";
    private static final String USERNAME_STRING = "#username";

    private static final String ERROR_USER_NOT_FOUND_WITH_STRING = "error.user.not_found_with";
    private static final String ERROR_AUTH_LOCKED_ADMIN_STRING = "error.auth.locked_admin";
    private static final String ERROR_AUTH_ACCOUNT_UNVERIFIED_STRING = "error.auth.account_unverified";
    private static final String ERROR_AUTH_ACCOUNT_INACTIVE_STRING = "error.auth.account_inactive";

    @Override
    @Cacheable(value = USER_DETAILS_STRING, key = USERNAME_STRING)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(
                        Translator.tolocale(ERROR_USER_NOT_FOUND_WITH_STRING, username)));
        if (!user.isAccountNonLocked()) {
            log.warn("Authentication rejected: Account for user '{}' is LOCKED", username);
            throw new LockedException(Translator.tolocale(ERROR_AUTH_LOCKED_ADMIN_STRING));
        }

        if (user.getUserStatus() == UserStatus.UNVERIFIED) {
            log.warn("Authentication rejected: Account for user '{}' is UNVERIFIED", username);
            throw new DisabledException(Translator.tolocale(ERROR_AUTH_ACCOUNT_UNVERIFIED_STRING));
        }

        if (user.getUserStatus() == UserStatus.INACTIVE) {
            log.warn("Authentication rejected: Account for user '{}' is INACTIVE", username);
            throw new DisabledException(Translator.tolocale(ERROR_AUTH_ACCOUNT_INACTIVE_STRING));
        }

        return user;
    }
}