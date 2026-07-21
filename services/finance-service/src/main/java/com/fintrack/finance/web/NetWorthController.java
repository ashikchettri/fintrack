package com.fintrack.finance.web;

import com.fintrack.finance.security.AuthenticatedMember;
import com.fintrack.finance.service.NetWorthService;
import com.fintrack.finance.web.dto.NetWorthItemsResponse;
import com.fintrack.finance.web.dto.NetWorthRequest;
import com.fintrack.finance.web.dto.NetWorthResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The household net worth (ADR 014). Household-scoped; the summary folds in the
 * home loan, the items endpoint is the editable manual balance sheet (replace-all).
 */
@RestController
@RequestMapping("/api/v1/household/net-worth")
public class NetWorthController {

    private final NetWorthService netWorthService;

    public NetWorthController(NetWorthService netWorthService) {
        this.netWorthService = netWorthService;
    }

    /** Totals + breakdown (manual items + the home loan). */
    @GetMapping
    public NetWorthResponse summary(@AuthenticationPrincipal Jwt jwt) {
        return netWorthService.summary(AuthenticatedMember.from(jwt));
    }

    /** The editable manual balance sheet. */
    @GetMapping("/items")
    public NetWorthItemsResponse items(@AuthenticationPrincipal Jwt jwt) {
        return netWorthService.items(AuthenticatedMember.from(jwt));
    }

    /** Replace the manual balance sheet wholesale. */
    @PutMapping("/items")
    public NetWorthItemsResponse save(@AuthenticationPrincipal Jwt jwt,
                                      @Valid @RequestBody NetWorthRequest request) {
        return netWorthService.save(AuthenticatedMember.from(jwt), request);
    }
}
