package com.fintrack.auth.service;

import com.fintrack.auth.config.VerificationProperties;
import com.fintrack.auth.domain.EmailVerificationCode;
import com.fintrack.auth.domain.User;
import com.fintrack.auth.repository.EmailVerificationCodeRepository;
import com.fintrack.auth.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

@Service
public class VerificationService {

    private final EmailVerificationCodeRepository codeRepository;
    private final UserRepository userRepository;
    private final EmailSender emailSender;
    private final VerificationProperties properties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public VerificationService(EmailVerificationCodeRepository codeRepository,
                               UserRepository userRepository,
                               EmailSender emailSender,
                               VerificationProperties properties,
                               Clock clock) {
        this.codeRepository = codeRepository;
        this.userRepository = userRepository;
        this.emailSender = emailSender;
        this.properties = properties;
        this.clock = clock;
    }

    /** Generates, stores (hashed), and emails a fresh code — replacing any prior one. */
    @Transactional
    public void issueFor(User user) {
        doIssue(user);
    }

    // shared by issueFor and resend WITHOUT self-invoking a @Transactional
    // method (the proxy would be bypassed — Sonar S6809); both public entry
    // points carry the annotation instead
    private void doIssue(User user) {
        codeRepository.deleteByUserId(user.getId());

        String code = randomNumericCode();
        Instant now = clock.instant();
        codeRepository.save(new EmailVerificationCode(
                user, TokenService.sha256Hex(code), now, now.plus(properties.codeTtl())));
        // in-transaction on purpose: if the email can't be sent, the code row
        // rolls back too and the client sees the failure immediately
        emailSender.sendVerificationCode(user.getEmail(), code);
    }

    /**
     * noRollbackFor: the failed-attempt counter must survive the 400 —
     * otherwise brute-forcing wouldn't be counted (same lesson as refresh
     * reuse detection).
     */
    @Transactional(noRollbackFor = InvalidVerificationCodeException.class)
    public void verify(String email, String rawCode) {
        User user = userRepository.findByEmail(normalize(email))
                .orElseThrow(InvalidVerificationCodeException::new);
        if (user.isEmailVerified()) {
            return; // idempotent: re-verifying an already-verified account succeeds
        }

        Instant now = clock.instant();
        EmailVerificationCode code = codeRepository.findByUserId(user.getId())
                .orElseThrow(InvalidVerificationCodeException::new);
        if (code.isDead(now, properties.maxAttempts())) {
            throw new InvalidVerificationCodeException();
        }
        if (!code.getCodeHash().equals(TokenService.sha256Hex(rawCode))) {
            code.registerFailedAttempt();
            throw new InvalidVerificationCodeException();
        }

        code.consume(now);
        user.markEmailVerified(now);
    }

    /** Silent for unknown/already-verified emails (no enumeration) and inside the cooldown. */
    @Transactional
    public void resend(String email) {
        Optional<User> user = userRepository.findByEmail(normalize(email));
        if (user.isEmpty() || user.get().isEmailVerified()) {
            return;
        }
        boolean inCooldown = codeRepository.findByUserId(user.get().getId())
                .map(existing -> clock.instant()
                        .isBefore(existing.getCreatedAt().plus(properties.resendCooldown())))
                .orElse(false);
        if (inCooldown) {
            return;
        }
        doIssue(user.get());
    }

    private String randomNumericCode() {
        // digit-by-digit: uniform, and no dynamic format strings (Sonar S3457)
        StringBuilder code = new StringBuilder(properties.codeLength());
        for (int i = 0; i < properties.codeLength(); i++) {
            code.append(secureRandom.nextInt(10));
        }
        return code.toString();
    }

    private static String normalize(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
