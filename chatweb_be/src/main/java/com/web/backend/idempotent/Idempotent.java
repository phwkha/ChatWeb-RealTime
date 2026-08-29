package com.web.backend.idempotent;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {

    /**
     * Unique key prefix for this endpoint (e.g. "friend_request", "chat_upload").
     * If blank, defaults to ClassName#methodName.
     */
    String key() default "";

    /**
     * Time-to-live for the idempotency key in Redis. Default 300 (5 minutes).
     */
    int ttl() default 300;

    /**
     * Time unit for TTL. Default is SECONDS.
     */
    TimeUnit unit() default TimeUnit.SECONDS;

    /**
     * Name of the HTTP header carrying the idempotency key. Default: "X-Idempotency-Key".
     */
    String headerName() default "X-Idempotency-Key";

    /**
     * Whether the header is required. If true, missing header returns 400.
     * Default: true.
     */
    boolean required() default true;

    /**
     * i18n message key for duplicate request error.
     */
    String messageKey() default "error.sys.duplicate_request";
}
