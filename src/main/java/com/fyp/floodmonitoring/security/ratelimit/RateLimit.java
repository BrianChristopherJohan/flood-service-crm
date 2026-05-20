package com.fyp.floodmonitoring.security.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller endpoint as rate-limited. The interceptor reads the
 * windows below, increments per-user counters in Redis (or an in-process
 * fallback), and short-circuits with HTTP 429 when any window is full.
 *
 * <p>Set the windows you care about and leave the rest as {@code -1}.
 * Caps are interpreted as "no more than N successful requests by the
 * same user inside the rolling window starting at the previous bucket
 * boundary".</p>
 *
 * Example:
 * <pre>
 *   &#64;RateLimit(key = "community.posts.create", perMinute = 3, perHour = 10, perDay = 30)
 *   &#64;PostMapping("/posts")
 *   public ResponseEntity&lt;CommunityPostDto&gt; createPost(...) { ... }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
    /** Bucket name. Combined with the user id to form the Redis key. */
    String key();

    /** Max successful requests per 60-second window. -1 disables this window. */
    int perMinute() default -1;

    /** Max successful requests per 3600-second window. -1 disables. */
    int perHour() default -1;

    /** Max successful requests per 86400-second window. -1 disables. */
    int perDay() default -1;
}
