package com.fyp.floodmonitoring.config;

import jakarta.servlet.http.HttpServletResponse;
import com.fyp.floodmonitoring.security.ratelimit.RateLimiter;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

/**
 * Slimmed-down security configuration for {@code @WebMvcTest} controller tests.
 *
 * <p>Mirrors the production {@link SecurityConfig} URL rules and enables
 * {@code @PreAuthorize} / method-level security, but omits the
 * {@code JwtAuthenticationFilter} dependency so the test context can start
 * without a real JWT filter or database-backed UserDetailsService.</p>
 *
 * <p>Import in each controller test class:
 * <pre>{@code @Import(TestSecurityConfig.class)}</pre>
 * and exclude the real {@link SecurityConfig} from the component scan:
 * <pre>{@code
 * excludeFilters = {
 *   @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
 *                         classes = {JwtAuthenticationFilter.class, SecurityConfig.class})
 * }
 * }</pre></p>
 */
@TestConfiguration
@EnableWebSecurity
@EnableMethodSecurity
public class TestSecurityConfig {

    @Bean
    public RateLimiter rateLimiter(ObjectProvider<StringRedisTemplate> redisProvider) {
        return new RateLimiter(redisProvider) {
            @Override
            public Decision check(String bucketName, String userKey, List<Window> windows) {
                return new Decision(true, 0);
            }
        };
    }

    @Bean
    public SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // ── Public auth endpoints (no JWT required) ──────────────────
                .requestMatchers(HttpMethod.POST,
                    "/auth/login", "/auth/register", "/auth/refresh",
                    "/auth/forgot-password", "/auth/verify-reset-code",
                    "/auth/reset-password", "/ingest").permitAll()
                // ── Public read endpoints (mirrors production SecurityConfig) ─────
                .requestMatchers(HttpMethod.GET,
                    "/sensors", "/sensors/**",
                    "/blogs", "/blogs/**",
                    "/safety", "/safety/**",
                    "/community/posts", "/community/posts/**",
                    "/community/groups", "/community/groups/**").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                // ── Everything else requires a valid session ──────────────────
                .anyRequest().authenticated()
            )
            // Return 401 (not 403) for unauthenticated requests to protected resources.
            // Spring Security's default with stateless sessions uses Http403ForbiddenEntryPoint;
            // an explicit entry point ensures tests expecting 401 behave correctly.
            .exceptionHandling(exc -> exc
                .authenticationEntryPoint((req, resp, ex) ->
                    resp.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized"))
            );
        return http.build();
    }
}
