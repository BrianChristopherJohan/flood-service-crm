package com.fyp.floodmonitoring.repository;

import com.fyp.floodmonitoring.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByToken(String token);

    @Query("SELECT rt FROM RefreshToken rt WHERE rt.token = :token AND rt.userId = :userId AND rt.expiresAt > :now")
    Optional<RefreshToken> findValidToken(String token, UUID userId, Instant now);

    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.userId = :userId")
    void deleteAllByUserId(UUID userId);

    /**
     * Idempotent single-token delete used by the logout flow.
     * Returns the number of rows deleted (0 or 1).
     */
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.token = :token")
    int deleteByToken(String token);
}
