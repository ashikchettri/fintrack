package com.fintrack.auth.service;

import com.fintrack.auth.domain.HouseholdMember;
import com.fintrack.auth.repository.HouseholdMemberRepository;
import com.fintrack.auth.web.dto.MemberResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * The household roster — who's in the caller's household, with a human name.
 * Powers the shared-commitments view so it shows real names instead of
 * "Housemate".
 */
@Service
public class HouseholdMemberService {

    private final HouseholdMemberRepository memberRepository;

    public HouseholdMemberService(HouseholdMemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> members(UUID callerUserId) {
        HouseholdMember caller = memberRepository.findByUserId(callerUserId)
                .orElseThrow(() -> new IllegalStateException("authenticated user has no membership"));
        UUID householdId = caller.getHousehold().getId();

        return memberRepository.findByHouseholdIdOrderByCreatedAt(householdId).stream()
                .map(m -> new MemberResponse(
                        m.getId(),
                        displayName(m),
                        m.getRole().name(),
                        m.getId().equals(caller.getId())))
                .toList();
    }

    private String displayName(HouseholdMember member) {
        if (member.getDisplayName() != null) {
            return member.getDisplayName();
        }
        String email = member.getUser().getEmail();
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }
}
