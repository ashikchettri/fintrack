package com.fintrack.auth.service;

import com.fintrack.auth.domain.User;
import com.fintrack.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ChangePasswordService {

    private static final Logger log = LoggerFactory.getLogger(ChangePasswordService.class);

    private final UserRepository userRepository;
    private final RefreshTokenStore refreshTokenStore;
    private final PasswordEncoder passwordEncoder;

    public ChangePasswordService(UserRepository userRepository,
                                 RefreshTokenStore refreshTokenStore,
                                 PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.refreshTokenStore = refreshTokenStore;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Changes the password of an already-authenticated user (userId from the
     * verified JWT). Requires the current password (defends against a hijacked
     * session or an unattended device) and, like a reset, revokes every active
     * refresh session so other devices must re-authenticate.
     */
    @Transactional
    public void changePassword(UUID userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException(
                        "no user for authenticated subject " + userId));

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new IncorrectCurrentPasswordException();
        }

        user.changePassword(passwordEncoder.encode(newPassword));
        int revoked = refreshTokenStore.revokeAllForUser(userId);
        log.info("Password changed for user {} — revoked {} active session(s)", userId, revoked);
    }
}
