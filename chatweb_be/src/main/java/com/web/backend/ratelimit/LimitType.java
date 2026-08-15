package com.web.backend.ratelimit;

public enum LimitType {
    /**
     * Rate limit based on Client IP Address.
     */
    IP,

    /**
     * Rate limit based on Authenticated Username.
     */
    USER,

    /**
     * Rate limit based on both Client IP Address and Username.
     */
    IP_AND_USER,

    /**
     * Rate limit shared globally for this endpoint across all requests.
     */
    GLOBAL
}
