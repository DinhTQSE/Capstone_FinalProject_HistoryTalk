package com.historyTalk.security;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Lightweight principal stored in SecurityContextHolder after JWT validation.
 * Populated directly from JWT claims — no DB call required.
 */
@Getter
@AllArgsConstructor
public class AuthenticatedPrincipal {

    private final String email;
    private final String uid;
    private final String staffId;   // null for REGISTERED users
    private final String roleName;  // e.g. "ADMIN", "STAFF" — null for REGISTERED users
    private final String userType;  // "STAFF" or "REGISTERED"
}
