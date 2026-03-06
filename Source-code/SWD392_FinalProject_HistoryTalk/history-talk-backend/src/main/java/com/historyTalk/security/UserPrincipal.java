package com.historyTalk.security;

import com.historyTalk.entity.Staff;
import com.historyTalk.entity.User;
import com.historyTalk.entity.UserType;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Spring Security UserDetails adapter for the User entity.
 * Username  = email (used as JWT subject)
 * Authorities derived from userType + linked Staff role (if any)
 */
@Getter
public class UserPrincipal implements UserDetails {

    private final String uid;
    private final String userName;
    private final String email;
    private final String password;
    private final UserType userType;
    private final String roleName; // non-null only when userType = STAFF
    private final Collection<? extends GrantedAuthority> authorities;

    public UserPrincipal(User user) {
        this.uid = user.getUid();
        this.userName = user.getUserName();
        this.email = user.getEmail();
        this.password = user.getPassword();
        this.userType = user.getUserType();

        // Resolve role name from linked Staff entity
        String resolvedRole = null;
        Staff staff = user.getStaff();
        if (staff != null && staff.getRole() != null) {
            resolvedRole = staff.getRole().getRoleName();
        }
        this.roleName = resolvedRole;
        this.authorities = buildAuthorities(user.getUserType(), resolvedRole);
    }

    private static List<SimpleGrantedAuthority> buildAuthorities(UserType userType, String roleName) {
        List<SimpleGrantedAuthority> auths = new ArrayList<>();
        if (userType == UserType.STAFF) {
            auths.add(new SimpleGrantedAuthority("ROLE_STAFF"));
            if (roleName != null && !roleName.isBlank()) {
                auths.add(new SimpleGrantedAuthority("ROLE_" + roleName.toUpperCase()));
            }
        } else {
            auths.add(new SimpleGrantedAuthority("ROLE_REGISTERED"));
        }
        return auths;
    }

    /** JWT subject is the user's email */
    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return true; }
}
