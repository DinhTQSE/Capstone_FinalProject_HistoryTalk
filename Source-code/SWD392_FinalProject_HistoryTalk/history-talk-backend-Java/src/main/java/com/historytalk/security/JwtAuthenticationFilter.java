package com.historytalk.security;

import com.historytalk.entity.enums.AccountStatus;
import com.historytalk.entity.user.User;
import com.historytalk.repository.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Extracts JWT from Authorization header, verifies signature/type/issuer,
 * validates live user state (active, passwordChangedAt, role, mustChangePassword),
 * and populates SecurityContext.
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Set<String> MUST_CHANGE_PASSWORD_ALLOWED_PATHS = Set.of(
            "/api/v1/users/me",
            "/api/v1/users/me/password",
            "/api/v1/auth/logout",
            "/api/v1/auth/logout-all"
    );

    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        try {
            String jwt = extractJwt(request);

            if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {
                Claims claims = tokenProvider.getAllClaims(jwt);
                String email = claims.getSubject();
                String uid = claims.get("uid", String.class);
                Date issuedAt = claims.getIssuedAt();

                Optional<User> userOpt = loadUser(uid, email);
                if (userOpt.isEmpty()) {
                    log.debug("JWT rejected: user not found for uid={} email={}", uid, email);
                    filterChain.doFilter(request, response);
                    return;
                }

                User user = userOpt.get();
                if (user.getDeletedAt() != null || user.getAccountStatus() == AccountStatus.DEACTIVATED) {
                    log.debug("JWT rejected: account deactivated/deleted for uid={}", user.getUid());
                    filterChain.doFilter(request, response);
                    return;
                }

                if (isTokenIssuedBeforePasswordChange(issuedAt, user)) {
                    log.debug("JWT rejected: issued before passwordChangedAt for uid={}", user.getUid());
                    filterChain.doFilter(request, response);
                    return;
                }

                String effectiveRole = user.getRole() != null ? user.getRole().name() : "CUSTOMER";
                List<SimpleGrantedAuthority> authorities = List.of(
                        new SimpleGrantedAuthority("ROLE_" + effectiveRole.toUpperCase())
                );

                AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
                        user.getEmail(),
                        user.getUid().toString(),
                        effectiveRole
                );

                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(principal, null, authorities);
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);

                if (Boolean.TRUE.equals(user.getMustChangePassword()) && !isAllowedDuringPasswordChange(request)) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding("UTF-8");
                    response.getWriter().write(
                            "{\"success\":false,\"errorCode\":403,\"status\":\"FORBIDDEN\","
                                    + "\"message\":\"PASSWORD_CHANGE_REQUIRED: Vui lòng đổi mật khẩu mặc định trước khi tiếp tục sử dụng hệ thống\"}"
                    );
                    return;
                }

                log.debug("JWT auth set for user: {}", user.getEmail());
            }
        } catch (Exception ex) {
            log.error("Could not set authentication in security context", ex);
        }

        filterChain.doFilter(request, response);
    }

    private Optional<User> loadUser(String uid, String email) {
        if (StringUtils.hasText(uid)) {
            try {
                return userRepository.findById(UUID.fromString(uid));
            } catch (IllegalArgumentException ignored) {
                return Optional.empty();
            }
        }
        if (StringUtils.hasText(email)) {
            return userRepository.findByEmailIgnoreCase(email);
        }
        return Optional.empty();
    }

    private boolean isTokenIssuedBeforePasswordChange(Date issuedAt, User user) {
        if (user.getPasswordChangedAt() == null) {
            return false;
        }
        if (issuedAt == null) {
            return true;
        }
        // JWT iat has second-level precision; truncate passwordChangedAt to seconds to avoid same-second false rejections.
        Instant passwordChangedInstant = user.getPasswordChangedAt()
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .truncatedTo(ChronoUnit.SECONDS);
        return issuedAt.toInstant().isBefore(passwordChangedInstant);
    }

    private boolean isAllowedDuringPasswordChange(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null) {
            return false;
        }
        String contextPath = request.getContextPath();
        if (StringUtils.hasText(contextPath) && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        String servletPath = request.getServletPath();
        if (StringUtils.hasText(servletPath) && path.startsWith(servletPath) && !servletPath.equals(path)) {
            // spring.mvc.servlet.path=/Historical-tell puts the controller path in pathInfo
            String pathInfo = request.getPathInfo();
            if (StringUtils.hasText(pathInfo)) {
                path = pathInfo;
            }
        }
        if (path.startsWith("/Historical-tell")) {
            path = path.substring("/Historical-tell".length());
        }
        return MUST_CHANGE_PASSWORD_ALLOWED_PATHS.contains(path);
    }

    private String extractJwt(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
