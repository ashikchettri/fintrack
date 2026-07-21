package com.fintrack.finance.service;

import com.fintrack.finance.domain.HomeLoan;
import com.fintrack.finance.domain.NetWorthItem;
import com.fintrack.finance.domain.NetWorthKind;
import com.fintrack.finance.repository.HomeLoanRepository;
import com.fintrack.finance.repository.NetWorthItemRepository;
import com.fintrack.finance.security.AuthenticatedMember;
import com.fintrack.finance.web.dto.NetWorthItemDto;
import com.fintrack.finance.web.dto.NetWorthItemsResponse;
import com.fintrack.finance.web.dto.NetWorthRequest;
import com.fintrack.finance.web.dto.NetWorthResponse;
import com.fintrack.finance.web.dto.NetWorthResponse.Line;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The household net worth (jointly held → household-scoped, ADR 014). The manual
 * balance sheet is saved wholesale (replace-all). The summary folds the home
 * loan in — its balance as a liability, its offset as an asset — so the mortgage
 * counts without being entered twice.
 */
@Service
public class NetWorthService {

    private static final String MANUAL = "MANUAL";
    private static final String HOME_LOAN = "HOME_LOAN";

    private final NetWorthItemRepository itemRepository;
    private final HomeLoanRepository homeLoanRepository;

    public NetWorthService(NetWorthItemRepository itemRepository, HomeLoanRepository homeLoanRepository) {
        this.itemRepository = itemRepository;
        this.homeLoanRepository = homeLoanRepository;
    }

    @Transactional(readOnly = true)
    public NetWorthItemsResponse items(AuthenticatedMember caller) {
        List<NetWorthItem> saved = itemRepository.findByHouseholdIdOrderBySortOrder(caller.householdId());
        String currency = saved.isEmpty() ? "AUD" : saved.get(0).getCurrency();
        return new NetWorthItemsResponse(currency, saved.stream().map(NetWorthItemDto::from).toList());
    }

    @Transactional
    public NetWorthItemsResponse save(AuthenticatedMember caller, NetWorthRequest req) {
        itemRepository.deleteByHouseholdId(caller.householdId());

        String currency = req.currency() == null ? "AUD" : req.currency().toUpperCase(Locale.ROOT);
        List<NetWorthItemDto> incoming = req.items() == null ? List.of() : req.items();

        List<NetWorthItem> toSave = new ArrayList<>();
        int order = 0;
        for (NetWorthItemDto dto : incoming) {
            // drop blank rows (a template line the user never named or valued)
            if (dto.kind() == null || dto.name() == null || dto.name().isBlank() || dto.value() == null) {
                continue;
            }
            toSave.add(new NetWorthItem(caller.householdId(), dto.kind(),
                    trim(dto.category()), dto.name().strip(), dto.value(), order++, currency));
        }
        itemRepository.saveAll(toSave);
        return new NetWorthItemsResponse(currency, toSave.stream().map(NetWorthItemDto::from).toList());
    }

    @Transactional(readOnly = true)
    public NetWorthResponse summary(AuthenticatedMember caller) {
        List<NetWorthItem> items = itemRepository.findByHouseholdIdOrderBySortOrder(caller.householdId());

        List<Line> assets = new ArrayList<>();
        List<Line> liabilities = new ArrayList<>();
        for (NetWorthItem item : items) {
            Line line = new Line(item.getName(), item.getCategory(), item.getValue(), MANUAL);
            (item.getKind() == NetWorthKind.ASSET ? assets : liabilities).add(line);
        }

        // fold the home loan in (its balance is a liability, its offset an asset)
        homeLoanRepository.findByHouseholdId(caller.householdId())
                .filter(HomeLoan::isHasHomeLoan)
                .ifPresent(loan -> {
                    if (loan.getLoanAmount() != null && loan.getLoanAmount().signum() > 0) {
                        liabilities.add(new Line("Home loan", "Mortgage", loan.getLoanAmount(), HOME_LOAN));
                    }
                    if (loan.isHasOffset() && loan.getOffsetBalance() != null
                            && loan.getOffsetBalance().signum() > 0) {
                        assets.add(new Line("Offset savings", "Savings & cash", loan.getOffsetBalance(), HOME_LOAN));
                    }
                });

        BigDecimal totalAssets = sum(assets);
        BigDecimal totalLiabilities = sum(liabilities);
        String currency = items.isEmpty()
                ? homeLoanRepository.findByHouseholdId(caller.householdId())
                        .map(HomeLoan::getCurrency).orElse("AUD")
                : items.get(0).getCurrency();

        return new NetWorthResponse(currency, totalAssets, totalLiabilities,
                totalAssets.subtract(totalLiabilities), assets, liabilities);
    }

    private static BigDecimal sum(List<Line> lines) {
        return lines.stream().map(Line::value).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
