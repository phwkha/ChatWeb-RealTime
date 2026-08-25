package com.web.backend.service.impl;

import com.web.backend.common.UserStatus;
import com.web.backend.config.localresolverconfig.Translator;
import com.web.backend.exception.custom.AccessForbiddenException;
import com.web.backend.exception.custom.ResourceNotFoundException;
import com.web.backend.repository.UserRepository;
import com.web.backend.repository.projection.UserRsaKeyProjection;
import com.web.backend.service.KeyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j(topic = "KEY-SERVICE")
@RequiredArgsConstructor
public class KeyServiceImpl implements KeyService {

    private final UserRepository userRepository;

    private static final String USER_DETAILS_STRING = "user_details";
    private static final String USERNAME_STRING = "#username";

    private static final String ERROR_USER_NOT_FOUND_WITH_STRING = "error.user.not_found_with";
    private static final String ERROR_KEY_LOCKED_INACTIVE_STRING = "error.key.locked_inactive";

    @Override
    @Transactional
    public void saveRsaKey(String username, String encryptedKey) {
        if (!userRepository.existsByUsername(username)) {
            throw new ResourceNotFoundException(
                    Translator.tolocale(ERROR_USER_NOT_FOUND_WITH_STRING, username));
        }

        userRepository.updateEncryptedRsaPrivateKey(username, encryptedKey);
        log.info("Encrypted RSA private key updated for user '{}'", username);
    }

    @Override
    public String getRsaKey(String username) {
        UserRsaKeyProjection projection = userRepository.findRsaKeyProjectionByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException(
                        Translator.tolocale(ERROR_USER_NOT_FOUND_WITH_STRING, username)));

        if (projection.userStatus() != UserStatus.ACTIVE) {
            throw new AccessForbiddenException(Translator.tolocale(ERROR_KEY_LOCKED_INACTIVE_STRING));
        }

        String key = projection.encryptedRsaPrivateKey();
        if (key == null) {
            log.warn("Encrypted RSA private key not found for user '{}'", username);
            return null;
        }
        return key;
    }

    @Override
    public String getPublicKey(String username) {
        if (!userRepository.existsByUsername(username)) {
            throw new ResourceNotFoundException(
                    Translator.tolocale(ERROR_USER_NOT_FOUND_WITH_STRING, username));
        }
        return userRepository.findPublicKeyByUsername(username).orElse(null);
    }

    @Override
    @CacheEvict(value = USER_DETAILS_STRING, key = USERNAME_STRING)
    @Transactional
    public void savePublicKey(String username, String publicKey) {
        if (!userRepository.existsByUsername(username)) {
            throw new ResourceNotFoundException(
                    Translator.tolocale(ERROR_USER_NOT_FOUND_WITH_STRING, username));
        }
        userRepository.updatePublicKey(username, publicKey);
        log.info("Public key updated for user '{}'", username);
    }
}