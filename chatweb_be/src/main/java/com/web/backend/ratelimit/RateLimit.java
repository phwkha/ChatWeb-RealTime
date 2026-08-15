package com.web.backend.ratelimit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /**
     * Unique key prefix for identifying the rate limit target (e.g. "login", "register", "send_otp").
     * If left blank, it will default to class_name#method_name.
     */
    String key() default "";

    /**
     * Maximum number of allowed requests within the time window.
     */
    int limit() default 10;

    /**
     * Time window duration. Default is 60.
     */
    int period() default 60;

    /**
     * Time unit for the duration. Default is SECONDS.
     */
    TimeUnit unit() default TimeUnit.SECONDS;

    /**
     * Target type for rate limiting (IP, USER, IP_AND_USER, GLOBAL). Default is IP.
     */
    LimitType type() default LimitType.IP;

    /**
     * Localization message key to return when rate limit is exceeded.
     */
    String messageKey() default "error.auth.too_many_attempts";
}
