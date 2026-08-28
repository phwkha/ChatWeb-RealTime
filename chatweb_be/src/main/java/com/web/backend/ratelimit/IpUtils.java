package com.web.backend.ratelimit;

import jakarta.servlet.http.HttpServletRequest;

public final class IpUtils {

    private static final String UNKNOWN = "unknown";


    private IpUtils() {
        // Utility class
    }

    /**
     * Extracts real client IP address from HttpServletRequest considering proxy headers.
     *
     * @param request HttpServletRequest
     * @return Client IP address
     */
    public static String getClientIpAddress(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN;
        }

        String remoteAddr = request.getRemoteAddr();
        return (remoteAddr != null && !remoteAddr.isEmpty()) ? remoteAddr : UNKNOWN;
    }
}
