package com.fyp.floodmonitoring.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Intercepts every request and validates the JWT Bearer token.
 * If valid, populates the Spring {@link SecurityContextHolder} so that
 * downstream controllers can access the authenticated principal.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final CustomUserDetailsService userDetailsService;
    private final RevokedTokenStore revokedTokenStore;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String token = extractToken(request);
            if (StringUtils.hasText(token) && tokenProvider.validateAccessToken(token)) {
                // Revocation check — must happen AFTER signature
                // validation but BEFORE we trust any claims from the
                // token. A revoked jti behaves identically to an
                // expired token: we don't populate the security
                // context, so the next authorisation gate returns 401.
                //
                // Tokens minted before the jti feature shipped have
                // no `jti` claim; `getJtiFromAccessToken` returns null
                // and `isRevoked(null)` returns false. So upgrading
                // doesn't invalidate in-flight sessions — they age
                // out naturally as users refresh.
                String jti = tokenProvider.getJtiFromAccessToken(token);
                if (revokedTokenStore.isRevoked(jti)) {
                    log.info("JWT filter — rejecting revoked jti={}", jti);
                    filterChain.doFilter(request, response);
                    return;
                }

                String userId = tokenProvider.getSubjectFromAccessToken(token).toString();
                UserDetails userDetails = userDetailsService.loadUserByUsername(userId);

                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        } catch (JwtException | IllegalArgumentException ex) {
            log.warn("JWT filter error: {}", ex.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
