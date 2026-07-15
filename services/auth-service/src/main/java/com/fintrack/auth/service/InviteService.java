package com.fintrack.auth.service;

import com.fintrack.auth.config.VerificationProperties;
import com.fintrack.auth.domain.Household;
import com.fintrack.auth.domain.HouseholdInvite;
import com.fintrack.auth.domain.HouseholdMember;
import com.fintrack.auth.domain.HouseholdRole;
import com.fintrack.auth.domain.User;
import com.fintrack.auth.repository.HouseholdInviteRepository;
import com.fintrack.auth.repository.HouseholdMemberRepository;
import com.fintrack.auth.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;

/**
 * Household invitations (multi-member households). An OWNER invites someone by
 * email; the invitee accepts with a one-time code and joins the EXISTING
 * household — the thing that makes shared commitments (ADR 006) real rather than
 * a household of one. Same code hardening as verification/reset.
 */
@Service
public class InviteService {

    private final HouseholdInviteRepository inviteRepository;
    private final HouseholdMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailSender emailSender;
    private final VerificationProperties properties;
    private final java.time.Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public InviteService(HouseholdInviteRepository inviteRepository,
                         HouseholdMemberRepository memberRepository,
                         UserRepository userRepository,
                         PasswordEncoder passwordEncoder,
                         EmailSender emailSender,
                         VerificationProperties properties,
                         java.time.Clock clock) {
        this.inviteRepository = inviteRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailSender = emailSender;
        this.properties = properties;
        this.clock = clock;
    }

    /** OWNER invites {@code inviteEmail} to their household as an ADULT member. */
    @Transactional
    public void invite(java.util.UUID callerUserId, String inviteEmail) {
        HouseholdMember owner = memberRepository.findByUserId(callerUserId)
                .orElseThrow(NotHouseholdOwnerException::new);
        if (owner.getRole() != HouseholdRole.OWNER) {
            throw new NotHouseholdOwnerException();
        }

        String email = normalize(inviteEmail);
        // accept creates a fresh account, so an existing email can't be invited
        // (existing-user "join" is a later slice); also blocks self-invite
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyInUseException();
        }

        Household household = owner.getHousehold();
        inviteRepository.deleteByHouseholdAndEmail(household.getId(), email); // replace any prior pending

        String code = randomNumericCode();
        Instant now = clock.instant();
        inviteRepository.save(new HouseholdInvite(household, owner, email, HouseholdRole.ADULT,
                TokenService.sha256Hex(code), now, now.plus(EmailContent.INVITE_TTL)));

        // in-transaction: if the email can't be sent, the invite row rolls back too
        emailSender.sendHouseholdInvite(email, inviterName(owner), household.getName(), code);
    }

    /**
     * The invitee accepts: verifies the code, creates their account (email already
     * proven by the invite), and joins the household.
     *
     * <p>noRollbackFor mirrors verification: a wrong guess must still increment
     * the attempt counter (otherwise the cap is toothless).
     */
    @Transactional(noRollbackFor = InvalidInviteException.class)
    public SignupResult accept(String email, String rawCode, String rawPassword, String displayName) {
        String normalizedEmail = normalize(email);
        HouseholdInvite invite = inviteRepository.findFirstByEmailOrderByCreatedAtDesc(normalizedEmail)
                .orElseThrow(InvalidInviteException::new);

        Instant now = clock.instant();
        if (invite.isDead(now, properties.maxAttempts())) {
            throw new InvalidInviteException();
        }
        if (!invite.getCodeHash().equals(TokenService.sha256Hex(rawCode))) {
            invite.registerFailedAttempt();
            throw new InvalidInviteException();
        }
        // a concurrent signup could have taken the email between invite and accept
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyInUseException();
        }

        User user = new User(normalizedEmail, passwordEncoder.encode(rawPassword));
        user.markEmailVerified(now); // the invite email proves address ownership
        HouseholdMember member = new HouseholdMember(
                invite.getHousehold(), user, invite.getRole(), cleanName(displayName));

        userRepository.save(user);
        memberRepository.save(member);
        invite.accept(now);

        return new SignupResult(user, invite.getHousehold(), member);
    }

    private String inviterName(HouseholdMember owner) {
        return owner.getDisplayName() != null ? owner.getDisplayName() : localPart(owner.getUser().getEmail());
    }

    private String cleanName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return null;
        }
        String trimmed = displayName.strip();
        return trimmed.length() > 100 ? trimmed.substring(0, 100) : trimmed;
    }

    private static String localPart(String email) {
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }

    private String randomNumericCode() {
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
