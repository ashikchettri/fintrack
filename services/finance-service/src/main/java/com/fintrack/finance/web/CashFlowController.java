package com.fintrack.finance.web;

import com.fintrack.finance.security.AuthenticatedMember;
import com.fintrack.finance.service.CashFlowService;
import com.fintrack.finance.web.dto.CashFlowResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The household's monthly cash-flow snapshot — income, loan repayment, average
 * spending and the resulting surplus. The affordability "what-if" (modelling a
 * new loan) runs in the UI against this surplus, so it's instant and every
 * assumption stays visible.
 */
@RestController
@RequestMapping("/api/v1/household/cash-flow")
public class CashFlowController {

    private final CashFlowService cashFlowService;

    public CashFlowController(CashFlowService cashFlowService) {
        this.cashFlowService = cashFlowService;
    }

    @GetMapping
    public CashFlowResponse cashFlow(@AuthenticationPrincipal Jwt jwt) {
        return cashFlowService.summary(AuthenticatedMember.from(jwt));
    }
}
