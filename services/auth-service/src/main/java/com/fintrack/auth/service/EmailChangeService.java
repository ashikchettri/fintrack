package com.fintrack.auth.service;

import com.fintrack.auth.config.VerificationProperties;
import com.fintrack.auth.domain.EmailChangeRequest;
import com.fintrack.auth.domain.User;
import com.fintrack.auth.repository.EmailChangeRequestRepository;
import com.fintrack.auth.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * Authenticated email change: proves ownership of the NEW address by code
 * before swapping, so the old address stays valid until confirmation. Reuses
 * the ADR 004/005 code hardening (hash at rest, TTL, attempt cap).
 */
@Service
public class EmailChangeService {

    private final EmailChangeRequestRepository requestRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailSender emailSender;
    private final VerificationProperties properties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public EmailChangeService(EmailChangeRequestRepository requestRepository,
                              UserRepository userRepository,
                              PasswordEncoder passwordEncoder,
                              EmailSender emailSender,
                              VerificationProperties properties,
                              Clock clock) {
        this.requestRepository = requestRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailSender = emailSender;
        this.properties = properties;
        this.clock = clock;
    }

    /** Requires the current password; emails a code to the new address (not yet applied). */
    @Transactional
    public void requestChange(UUID userId, String newEmail, String currentPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("no user for subject " + userId));

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new IncorrectCurrentPasswordException();
        }
        String normalized = newEmail.strip().toLowerCase(Locale.ROOT);
        // uniqueness incl. the caller's own current address (no-op change is pointless)
        if (userRepository.existsByEmail(normalized)) {
            throw new EmailAlreadyInUseException();
        }

        requestRepository.deleteByUserId(userId);
        String code = randomNumericCode();
        Instant now = clock.instant();
        requestRepository.save(new EmailChangeRequest(
                user, normalized, TokenService.sha256Hex(code), now, now.plus(properties.codeTtl())));
        // in-transaction: a send failure rolls back the pending request
        emailSender.sendEmailChangeCode(normalized, code);
    }

    /** noRollbackFor: the attempt counter must survive a wrong-code 400. */
    @Transactional(noRollbackFor = InvalidVerificationCodeException.class)
    public void confirmChange(UUID userId, String rawCode) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("no user for subject " + userId));

        Instant now = clock.instant();
        EmailChangeRequest request = requestRepository.findByUserId(userId)
                .orElseThrow(InvalidVerificationCodeException::new);
        if (request.isDead(now, properties.maxAttempts())) {
            throw new InvalidVerificationCodeException();
        }
        if (!request.getCodeHash().equals(TokenService.sha256Hex(rawCode))) {
            request.registerFailedAttempt();
            throw new InvalidVerificationCodeException();
        }
        // guard a race where the address got taken between request and confirm
        if (userRepository.existsByEmail(request.getNewEmail())) {
            throw new EmailAlreadyInUseException();
        }

        request.consume(now);
        user.changeEmail(request.getNewEmail());
    }

    private String randomNumericCode() {
        StringBuilder code = new StringBuilder(properties.codeLength());
        for (int i = 0; i < properties.codeLength(); i++) {
            code.append(secureRandom.nextInt(10));
        }
        return code.toString();
    }
}
