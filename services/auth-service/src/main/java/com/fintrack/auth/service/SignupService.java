package com.fintrack.auth.service;

import com.fintrack.auth.domain.Household;
import com.fintrack.auth.domain.HouseholdMember;
import com.fintrack.auth.domain.HouseholdRole;
import com.fintrack.auth.domain.User;
import com.fintrack.auth.repository.HouseholdMemberRepository;
import com.fintrack.auth.repository.HouseholdRepository;
import com.fintrack.auth.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class SignupService {

    private final UserRepository userRepository;
    private final HouseholdRepository householdRepository;
    private final HouseholdMemberRepository householdMemberRepository;
    private final PasswordEncoder passwordEncoder;
    private final VerificationService verificationService;

    public SignupService(UserRepository userRepository,
                         HouseholdRepository householdRepository,
                         HouseholdMemberRepository householdMemberRepository,
                         PasswordEncoder passwordEncoder,
                         VerificationService verificationService) {
        this.userRepository = userRepository;
        this.householdRepository = householdRepository;
        this.householdMemberRepository = householdMemberRepository;
        this.passwordEncoder = passwordEncoder;
        this.verificationService = verificationService;
    }

    /**
     * Creates a user, an auto-created single-member household, and an OWNER
     * membership — atomically. One transaction: a user without a household
     * would break every downstream household-scoped query.
     */
    @Transactional
    public SignupResult signup(String email, String rawPassword) {
        // Normalize before both the existence check and the insert so the DB
        // unique constraint behaves case-insensitively.
        String normalizedEmail = email.strip().toLowerCase(Locale.ROOT);

        // Fast path for the common case; the unique constraint is the real
        // guarantee — a concurrent duplicate insert is caught below.
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyInUseException();
        }

        User user = new User(normalizedEmail, passwordEncoder.encode(rawPassword));
        Household household = new Household(defaultHouseholdName(normalizedEmail));
        HouseholdMember member = new HouseholdMember(household, user, HouseholdRole.OWNER);

        try {
            userRepository.save(user);
            householdRepository.save(household);
            householdMemberRepository.save(member);
            // Flush inside the try so a race on the unique constraint surfaces
            // here (as 409), not as a 500 at commit time after we've returned.
            userRepository.flush();
        } catch (DataIntegrityViolationException _) {
            throw new EmailAlreadyInUseException();
        }

        // same transaction: no user row without a pending code + sent email (ADR 004)
        verificationService.issueFor(user);

        return new SignupResult(user, household, member);
    }

    private String defaultHouseholdName(String email) {
        // "jane@example.com" → "jane's household"; user-renamable in phase 7
        String localPart = email.substring(0, email.indexOf('@'));
        return localPart + "'s household";
    }
}