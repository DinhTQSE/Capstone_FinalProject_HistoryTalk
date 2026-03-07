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
import java.util.ArrayList;
import java.util.List;

/**
 * ⚠️ SHARED FILE – Team Coordination Required
 *
 * Extracts JWT from Authorization header and populates SecurityContext.
 *
 * JWT Claim Reading:
 *   - sub       → email (username / principal name)
 *   - uid       → user UUID
 *   - userType  → "REGISTERED" or "STAFF"
 *   - roleName  → e.g. "ADMIN" (only when userType = STAFF)
 *
 * Authorities built:
 *   - REGISTERED → [ROLE_REGISTERED]
 *   - STAFF      → [ROLE_STAFF, ROLE_<roleName>]
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

                List<SimpleGrantedAuthority> authorities = buildAuthoritiesFromClaims(claims);

                String uid      = claims.get("uid",      String.class);
                String staffId  = claims.get("staffId",  String.class);
                String roleName = claims.get("roleName", String.class);
                String userType = claims.get("userType", String.class);
                AuthenticatedPrincipal principal =
                        new AuthenticatedPrincipal(email, uid, staffId, roleName, userType);

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
                    List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                    authorities.add(new SimpleGrantedAuthority("ROLE_STAFF"));
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + staffRole.toUpperCase()));

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

    private List<SimpleGrantedAuthority> buildAuthoritiesFromClaims(Claims claims) {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        String userType = claims.get("userType", String.class);

        if ("STAFF".equalsIgnoreCase(userType)) {
            authorities.add(new SimpleGrantedAuthority("ROLE_STAFF"));
            String roleName = claims.get("roleName", String.class);
            if (StringUtils.hasText(roleName)) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + roleName.toUpperCase()));
            }
        } else {
            authorities.add(new SimpleGrantedAuthority("ROLE_REGISTERED"));
        }

        return authorities;
    }

    private String extractJwt(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
