package com.fintrack.auth.web;

import com.fintrack.auth.service.HouseholdMemberService;
import com.fintrack.auth.service.InviteService;
import com.fintrack.auth.web.dto.AcceptInviteRequest;
import com.fintrack.auth.web.dto.CreateInviteRequest;
import com.fintrack.auth.web.dto.MemberResponse;
import com.fintrack.auth.web.dto.SignupResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Multi-member households: an OWNER invites people who accept into the existing
 * household, and anyone can list the household's members (for the shared view).
 * OWNER-only is enforced in {@link InviteService}, not here.
 */
@RestController
@RequestMapping("/api/v1/households")
public class HouseholdController {

    private final InviteService inviteService;
    private final HouseholdMemberService memberService;

    public HouseholdController(InviteService inviteService, HouseholdMemberService memberService) {
        this.inviteService = inviteService;
        this.memberService = memberService;
    }

    /** OWNER invites an email to the household; a code is emailed to them. */
    @PostMapping("/invites")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void invite(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateInviteRequest request) {
        inviteService.invite(UUID.fromString(jwt.getSubject()), request.email());
    }

    /** Public: the invitee accepts, creating their account inside the household. */
    @PostMapping("/invites/accept")
    @ResponseStatus(HttpStatus.CREATED)
    public SignupResponse accept(@Valid @RequestBody AcceptInviteRequest request) {
        return SignupResponse.from(inviteService.accept(
                request.email(), request.code(), request.password(), request.name()));
    }

    /** The caller's household roster — names + roles for the shared-commitments view. */
    @GetMapping("/members")
    public List<MemberResponse> members(@AuthenticationPrincipal Jwt jwt) {
        return memberService.members(UUID.fromString(jwt.getSubject()));
    }
}
