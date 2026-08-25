package com.web.backend.repository.projection;

import com.web.backend.common.UserStatus;

public record UserRsaKeyProjection(
    UserStatus userStatus,
    String encryptedRsaPrivateKey
) {}
