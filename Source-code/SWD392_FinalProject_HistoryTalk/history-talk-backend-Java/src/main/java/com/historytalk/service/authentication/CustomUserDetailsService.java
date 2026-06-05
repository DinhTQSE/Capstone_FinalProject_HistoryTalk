package com.historytalk.service.authentication;

import com.historytalk.entity.user.User;
import com.historytalk.repository.UserRepository;
import com.historytalk.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Loads user by email (email is used as JWT subject / Spring Security username).
     * Rejects soft-deleted (deactivated) users with DisabledException.
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));

        if (user.getDeletedAt() != null) {
            throw new DisabledException("Tài khoản đã bị vô hiệu hóa");
        }

        return new UserPrincipal(user);
    }
}
