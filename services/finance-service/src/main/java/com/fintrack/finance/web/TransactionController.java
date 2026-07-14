package com.fintrack.finance.web;

import com.fintrack.finance.security.AuthenticatedMember;
import com.fintrack.finance.service.TransactionService;
import com.fintrack.finance.web.dto.TransactionResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping
    public List<TransactionResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return transactionService.list(AuthenticatedMember.from(jwt))
                .stream().map(TransactionResponse::from).toList();
    }
}
