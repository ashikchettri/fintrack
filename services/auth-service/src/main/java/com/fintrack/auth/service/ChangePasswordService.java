package com.fintrack.auth.service;

import com.fintrack.auth.domain.User;
import com.fintrack.auth.repository.RefreshTokenRepository;
import com.fintrack.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
public class ChangePasswordService {

    private static final Logger log = LoggerFactory.getLogger(ChangePasswordService.class);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public ChangePasswordService(UserRepository userRepository,
                                 RefreshTokenRepository refreshTokenRepository,
                                 PasswordEncoder passwordEncoder,
                                 Clock clock) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
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
        int revoked = refreshTokenRepository.revokeAllActiveForUser(userId, clock.instant());
        log.info("Password changed for user {} — revoked {} active session(s)", userId, revoked);
    }
}
