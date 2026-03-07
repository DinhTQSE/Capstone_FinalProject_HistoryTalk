package com.historyTalk.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import com.historyTalk.security.AuthenticatedPrincipal;

import java.io.IOException;
import java.util.List;

/**
 * ⚠️ SHARED FILE – Team Coordination Required
 *
 * Extracts JWT from Authorization header and populates SecurityContext.
 *
 * JWT Claim Reading:
 *   - sub   → email (username / principal name)
 *   - uid   → user UUID
 *   - role  → "USER", "STAFF", or "ADMIN"
 *
 * Authorities built:
 *   - ROLE_<role>  (single authority matching the role claim)
 *
 * Fallback (for Swagger / manual testing):
 *   Header X-Staff-Id + X-Staff-Role supplied → simulate staff authentication.
 *   Only used when no JWT is present. Remove in production if desired.
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        try {
            String jwt = extractJwt(request);

            if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {
                String email = tokenProvider.getUsernameFromToken(jwt);
                Claims claims = tokenProvider.getAllClaims(jwt);

                String uid  = claims.get("uid",  String.class);
                String role = claims.get("role", String.class);

                List<SimpleGrantedAuthority> authorities = List.of(
                        new SimpleGrantedAuthority("ROLE_" + (role != null ? role.toUpperCase() : "USER"))
                );

                AuthenticatedPrincipal principal = new AuthenticatedPrincipal(email, uid, role);

                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(principal, null, authorities);
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
                log.debug("JWT auth set for user: {}", email);

            } else {
                // Fallback: X-Staff-Id / X-Staff-Role headers (testing only)
                String staffId   = request.getHeader("X-Staff-Id");
                String staffRole = request.getHeader("X-Staff-Role");

                if (StringUtils.hasText(staffId)) {
                    if (!StringUtils.hasText(staffRole)) {
                        staffRole = "STAFF";
                    }
                    List<SimpleGrantedAuthority> authorities = List.of(
                            new SimpleGrantedAuthority("ROLE_" + staffRole.toUpperCase())
                    );

                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(staffId, null, authorities);
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    log.debug("Header-based auth set for staff: {}", staffId);
                }
            }
        } catch (Exception ex) {
            log.error("Could not set authentication in security context", ex);
        }

        filterChain.doFilter(request, response);
    }

    private String extractJwt(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
