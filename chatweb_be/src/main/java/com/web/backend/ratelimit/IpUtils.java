package com.web.backend.ratelimit;

import jakarta.servlet.http.HttpServletRequest;

public final class IpUtils {

    private static final String UNKNOWN = "unknown";
    private static final String COMMA = ",";
    private static final String[] IP_HEADERS = {
            "X-Forwarded-For",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_X_FORWARDED_FOR",
            "HTTP_X_FORWARDED",
            "HTTP_X_CLUSTER_CLIENT_IP",
            "HTTP_CLIENT_IP",
            "HTTP_FORWARDED_FOR",
            "HTTP_FORWARDED",
            "X-Real-IP"
    };

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

        for (String header : IP_HEADERS) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !UNKNOWN.equalsIgnoreCase(ip)) {
                // If forwarded through multiple proxies, the first IP is the original client IP
                if (ip.contains(COMMA)) {
                    return ip.split(COMMA)[0].trim();
                }
                return ip.trim();
            }
        }

        String remoteAddr = request.getRemoteAddr();
        return (remoteAddr != null && !remoteAddr.isEmpty()) ? remoteAddr : UNKNOWN;
    }
}
