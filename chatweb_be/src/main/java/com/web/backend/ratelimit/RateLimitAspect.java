package com.web.backend.ratelimit;

import com.web.backend.exception.custom.TooManyRequestsException;
import com.web.backend.service.RateLimitingService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j(topic = "RATE-LIMIT-ASPECT")
public class RateLimitAspect {

    private final RateLimitingService rateLimitingService;

    private static final String DELIMITER = ":";
    private static final String ANONYMOUS = "anonymous";
    private static final String DELIMITER_HASH_STRING = "#";
    private static final String PREFIX_USER_STRING = "user_";
    private static final String PREFIX_IP_STRING = "ip_";
    private static final String GLOBAL_STRING = "global";

    @Before("@annotation(rateLimit)")
    public void handleRateLimit(JoinPoint joinPoint, RateLimit rateLimit) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes != null ? attributes.getRequest() : null;

        String keyPrefix = rateLimit.key();
        if (keyPrefix == null || keyPrefix.trim().isEmpty()) {
            keyPrefix = joinPoint.getSignature().getDeclaringType().getSimpleName() + DELIMITER_HASH_STRING
                    + joinPoint.getSignature().getName();
        }

        String identifier = resolveIdentifier(rateLimit.type(), request);
        String targetKey = keyPrefix + DELIMITER + identifier;

        long windowSeconds = rateLimit.unit().toSeconds(rateLimit.period());
        if (windowSeconds <= 0) {
            windowSeconds = 1;
        }

        boolean allowed = rateLimitingService.isAllowed(targetKey, rateLimit.limit(), windowSeconds);

        if (!allowed) {
            log.warn("Rate limit exceeded for key '{}' (limit: {} / {}s)", targetKey, rateLimit.limit(), windowSeconds);
            throw new TooManyRequestsException(rateLimit.messageKey(), windowSeconds);
        }
    }

    private String resolveIdentifier(LimitType limitType, HttpServletRequest request) {
        String clientIp = IpUtils.getClientIpAddress(request);
        String username = getAuthenticatedUsername();

        switch (limitType) {
            case USER:
                return username != null ? PREFIX_USER_STRING + username : PREFIX_IP_STRING + clientIp;
            case IP_AND_USER:
                return (username != null ? PREFIX_USER_STRING + username : ANONYMOUS) + DELIMITER + PREFIX_IP_STRING + clientIp;
            case GLOBAL:
                return GLOBAL_STRING;
            case IP:
            default:
                return PREFIX_IP_STRING + clientIp;
        }
    }

    private String getAuthenticatedUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            return auth.getName();
        }
        return null;
    }
}
