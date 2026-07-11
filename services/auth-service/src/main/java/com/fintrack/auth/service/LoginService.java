package com.fintrack.auth.service;

import com.fintrack.auth.domain.HouseholdMember;
import com.fintrack.auth.domain.User;
import com.fintrack.auth.repository.HouseholdMemberRepository;
import com.fintrack.auth.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class LoginService {

    private final UserRepository userRepository;
    private final HouseholdMemberRepository householdMemberRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    /**
     * Hash of a random value nobody knows. Verified against on unknown-email
     * logins so both failure paths cost one full Argon2 check — otherwise the
     * fast "no such user" 401 leaks account existence through response timing.
     */
    private final String timingEqualizerHash;

    public LoginService(UserRepository userRepository,
                        HouseholdMemberRepository householdMemberRepository,
                        PasswordEncoder passwordEncoder,
                        TokenService tokenService) {
        this.userRepository = userRepository;
        this.householdMemberRepository = householdMemberRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.timingEqualizerHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public LoginResult login(String email, String rawPassword) {
        String normalizedEmail = email.strip().toLowerCase(Locale.ROOT);

        Optional<User> user = userRepository.findByEmail(normalizedEmail);
        if (user.isEmpty()) {
            passwordEncoder.matches(rawPassword, timingEqualizerHash);
            throw new InvalidCredentialsException();
        }
        if (!passwordEncoder.matches(rawPassword, user.get().getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        // signup guarantees a membership; its absence is data corruption, not a 401
        HouseholdMember member = householdMemberRepository.findByUserId(user.get().getId())
                .orElseThrow(() -> new IllegalStateException(
                        "user %s has no household membership".formatted(user.get().getId())));

        return new LoginResult(
                tokenService.issueAccessToken(member),
                tokenService.issueRefreshToken(user.get()).rawToken());
    }
}
