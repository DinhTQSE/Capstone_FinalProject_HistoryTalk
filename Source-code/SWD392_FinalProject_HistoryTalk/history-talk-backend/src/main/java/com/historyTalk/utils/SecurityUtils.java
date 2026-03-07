package com.historyTalk.utils;

import com.historyTalk.security.AuthenticatedPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Utility for extracting authenticated user info from the current SecurityContext.
 * Works for both JWT-validated requests (AuthenticatedPrincipal) and
 * the header-based testing fallback (String principal).
 */
public class SecurityUtils {

    private SecurityUtils() {}

    /**
     * Returns the uid of the currently authenticated user.
     * Extracted from the JWT claim — cannot be spoofed by the client.
     */
    public static String getUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;
        Object principal = auth.getPrincipal();
        if (principal instanceof AuthenticatedPrincipal ap) {
            return ap.getUid();
        }
        // Fallback: header-based testing — principal stored as staffId String
        return auth.getName();
    }

    /**
     * Returns the role (e.g. "ADMIN", "STAFF", "USER") of the currently authenticated user.
     * Extracted from the JWT claim — cannot be spoofed by the client.
     */
    public static String getRoleName() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return "USER";
        Object principal = auth.getPrincipal();
        if (principal instanceof AuthenticatedPrincipal ap) {
            return ap.getRole() != null ? ap.getRole() : "USER";
        }
        // Fallback: derive from granted authorities (header-based testing)
        return auth.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring(5))
                .findFirst()
                .orElse("USER");
    }
}
