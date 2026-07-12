package com.fintrack.auth.service;

import com.fintrack.auth.config.VerificationProperties;
import com.fintrack.auth.domain.PasswordResetCode;
import com.fintrack.auth.domain.User;
import com.fintrack.auth.repository.PasswordResetCodeRepository;
import com.fintrack.auth.repository.RefreshTokenRepository;
import com.fintrack.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

/**
 * Password reset via emailed one-time code (ADR 005). Reuses the ADR 004
 * hardening (hash at rest, TTL, attempt cap, cooldown) with a 6-digit code —
 * reset grants full account control, so it gets the larger keyspace.
 */
@Service
public class PasswordResetService {

    // reset-specific keyspace; other knobs shared with verification (ADR 005)
    static final int RESET_CODE_LENGTH = 6;

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    private final PasswordResetCodeRepository codeRepository;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttemptService;
    private final EmailSender emailSender;
    private final VerificationProperties properties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordResetService(PasswordResetCodeRepository codeRepository,
                                UserRepository userRepository,
                                RefreshTokenRepository refreshTokenRepository,
                                PasswordEncoder passwordEncoder,
                                LoginAttemptService loginAttemptService,
                                EmailSender emailSender,
                                VerificationProperties properties,
                                Clock clock) {
        this.codeRepository = codeRepository;
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.loginAttemptService = loginAttemptService;
        this.emailSender = emailSender;
        this.properties = properties;
        this.clock = clock;
    }

    /** Always silent for unknown emails and inside the cooldown (no enumeration). */
    @Transactional
    public void requestReset(String email) {
        Optional<User> user = userRepository.findByEmail(normalize(email));
        if (user.isEmpty()) {
            return;
        }
        boolean inCooldown = codeRepository.findByUserId(user.get().getId())
                .map(existing -> clock.instant()
                        .isBefore(existing.getCreatedAt().plus(properties.resendCooldown())))
                .orElse(false);
        if (inCooldown) {
            return;
        }

        codeRepository.deleteByUserId(user.get().getId());
        String code = randomNumericCode();
        Instant now = clock.instant();
        codeRepository.save(new PasswordResetCode(
                user.get(), TokenService.sha256Hex(code), now, now.plus(properties.codeTtl())));
        emailSender.sendPasswordResetCode(user.get().getEmail(), code);
    }

    /**
     * noRollbackFor: the failed-attempt counter must survive the 400 —
     * otherwise brute-forcing the reset code would be free.
     */
    @Transactional(noRollbackFor = InvalidResetCodeException.class)
    public void reset(String email, String rawCode, String newPassword) {
        String normalizedEmail = normalize(email);
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(InvalidResetCodeException::new);

        Instant now = clock.instant();
        PasswordResetCode code = codeRepository.findByUserId(user.getId())
                .orElseThrow(InvalidResetCodeException::new);
        if (code.isDead(now, properties.maxAttempts())) {
            throw new InvalidResetCodeException();
        }
        if (!code.getCodeHash().equals(TokenService.sha256Hex(rawCode))) {
            code.registerFailedAttempt();
            throw new InvalidResetCodeException();
        }

        // ALL entity mutations must happen before the bulk revoke below: it
        // flushes then clears the persistence context, detaching these entities
        code.consume(now);
        user.changePassword(passwordEncoder.encode(newPassword));
        if (!user.isEmailVerified()) {
            // typing an emailed code is mailbox proof — unverified users unstick here
            user.markEmailVerified(now);
        }

        // stolen sessions die with the old password (ADR 005)
        int revoked = refreshTokenRepository.revokeAllActiveForUser(user.getId(), now);
        log.info("Password reset for user {} — revoked {} active session(s)", user.getId(), revoked);

        // stale failures shouldn't lock out the recovered account
        loginAttemptService.recordSuccess(normalizedEmail);
    }

    private String randomNumericCode() {
        int bound = (int) Math.pow(10, RESET_CODE_LENGTH);
        return String.format("%0" + RESET_CODE_LENGTH + "d", secureRandom.nextInt(bound));
    }

    private static String normalize(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
