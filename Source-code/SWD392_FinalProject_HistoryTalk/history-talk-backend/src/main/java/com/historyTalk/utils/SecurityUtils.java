package com.historyTalk.utils;

import com.historyTalk.security.AuthenticatedPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Utility for extracting authenticated staff info from the current SecurityContext.
 * Works for both JWT-validated requests (AuthenticatedPrincipal) and
 * the header-based testing fallback (String principal).
 */
public class SecurityUtils {

    private SecurityUtils() {}

    /**
     * Returns the staffId of the currently authenticated staff.
     * Extracted from the JWT claim — cannot be spoofed by the client.
     */
    public static String getStaffId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;
        Object principal = auth.getPrincipal();
        if (principal instanceof AuthenticatedPrincipal ap) {
            return ap.getStaffId();
        }
        // Fallback: header-based testing — principal stored as staffId String
        return auth.getName();
    }

    /**
     * Returns the roleName (e.g. "ADMIN", "STAFF") of the currently authenticated staff.
     * Extracted from the JWT claim — cannot be spoofed by the client.
     */
    public static String getRoleName() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return "STAFF";
        Object principal = auth.getPrincipal();
        if (principal instanceof AuthenticatedPrincipal ap) {
            return ap.getRoleName() != null ? ap.getRoleName() : "STAFF";
        }
        // Fallback: derive from granted authorities (header-based testing)
        return auth.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .filter(a -> a.startsWith("ROLE_") && !a.equals("ROLE_STAFF") && !a.equals("ROLE_REGISTERED"))
                .map(a -> a.substring(5))
                .findFirst()
                .orElse("STAFF");
    }
}
