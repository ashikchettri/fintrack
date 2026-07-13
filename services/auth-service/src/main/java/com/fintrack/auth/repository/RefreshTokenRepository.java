package com.fintrack.auth.repository;

import com.fintrack.auth.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Stolen-token response: when a revoked token is presented again, every
     * live session for that user is killed in one statement.
     *
     * flushAutomatically: pending entity changes (e.g. the new password hash
     * during a reset) must reach the DB before clearAutomatically wipes the
     * persistence context — without it they were silently discarded.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RefreshToken rt set rt.revokedAt = :now "
            + "where rt.user.id = :userId and rt.revokedAt is null")
    int revokeAllActiveForUser(@Param("userId") UUID userId, @Param("now") Instant now);
}
