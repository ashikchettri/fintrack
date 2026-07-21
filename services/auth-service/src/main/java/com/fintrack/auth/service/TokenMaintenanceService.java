package com.fintrack.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Housekeeping: refresh-token rows accumulate one per login forever without
 * this. Dead tokens (expired or revoked) are kept for a 30-day audit window
 * — long enough to investigate a reuse-detection incident — then purged.
 */
@Service
public class TokenMaintenanceService {

    static final Duration RETENTION = Duration.ofDays(30);

    private static final Logger log = LoggerFactory.getLogger(TokenMaintenanceService.class);

    private final RefreshTokenStore refreshTokenStore;
    private final Clock clock;

    public TokenMaintenanceService(RefreshTokenStore refreshTokenStore, Clock clock) {
        this.refreshTokenStore = refreshTokenStore;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 4 * * *")   // daily, 4am server time
    public void purgeDeadTokens() {
        Instant cutoff = clock.instant().minus(RETENTION);
        int purged = refreshTokenStore.purgeDeadTokensOlderThan(cutoff);
        if (purged > 0) {
            log.info("Purged {} dead refresh token(s) older than {}", purged, cutoff);
        }
    }
}
