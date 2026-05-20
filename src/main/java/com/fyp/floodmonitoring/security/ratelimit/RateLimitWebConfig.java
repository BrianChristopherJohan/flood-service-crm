package com.fyp.floodmonitoring.security.ratelimit;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Plugs the {@link RateLimitInterceptor} into the MVC pipeline. The
 * interceptor is registered on every path; the {@code @RateLimit}
 * annotation on the matched handler decides whether the bucket is
 * actually checked. Endpoints without the annotation cost a single
 * cheap reflective lookup and pass through.
 */
@Configuration
@RequiredArgsConstructor
public class RateLimitWebConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor interceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor);
    }
}
