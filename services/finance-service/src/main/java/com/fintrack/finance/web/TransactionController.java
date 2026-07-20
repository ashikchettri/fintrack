package com.fintrack.finance.web;

import com.fintrack.finance.security.AuthenticatedMember;
import com.fintrack.finance.service.SharedCommitmentService;
import com.fintrack.finance.service.TransactionService;
import com.fintrack.finance.web.dto.RecategorizeResponse;
import com.fintrack.finance.web.dto.TransactionResponse;
import com.fintrack.finance.web.dto.UpdateVisibilityRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionService transactionService;
    private final SharedCommitmentService sharedCommitmentService;

    public TransactionController(TransactionService transactionService,
                                 SharedCommitmentService sharedCommitmentService) {
        this.transactionService = transactionService;
        this.sharedCommitmentService = sharedCommitmentService;
    }

    @GetMapping
    public List<TransactionResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return transactionService.list(AuthenticatedMember.from(jwt))
                .stream().map(TransactionResponse::from).toList();
    }

    /**
     * Re-run the categorizer over the caller's existing transactions (ADR 009),
     * so already-imported rows pick up AI (or improved-rule) categories without
     * re-uploading. Returns how many were reviewed and how many changed.
     */
    @PostMapping("/recategorize")
    public RecategorizeResponse recategorize(@AuthenticationPrincipal Jwt jwt) {
        return transactionService.recategorize(AuthenticatedMember.from(jwt));
    }

    /**
     * Mark/unmark a transaction as a shared commitment (ADR 006). Member-scoped:
     * a caller can only change the visibility of their own transaction.
     */
    @PatchMapping("/{id}/visibility")
    public TransactionResponse setVisibility(@AuthenticationPrincipal Jwt jwt,
                                             @PathVariable UUID id,
                                             @Valid @RequestBody UpdateVisibilityRequest request) {
        return TransactionResponse.from(sharedCommitmentService.setVisibility(
                AuthenticatedMember.from(jwt), id, request.toVisibility()));
    }
}
