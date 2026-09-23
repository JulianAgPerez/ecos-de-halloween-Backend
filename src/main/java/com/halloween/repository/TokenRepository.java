package com.halloween.repository;

import com.halloween.repository.Token;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TokenRepository extends JpaRepository<Token, Long> {
    @Query(value = """
      SELECT t FROM Token t 
      WHERE t.user.id = :id AND t.expired = false AND t.revoked = false
      """)
    List<Token> findAllValidTokenByUser(Long id);

    Optional<Token> findByToken(String token);

    // Conditional atomic revoke: returns the number of rows updated, so concurrent
    // redemption of the same refresh token makes exactly one winner; losers get 0.
    @Modifying
    @Query("UPDATE Token t SET t.revoked = true, t.expired = true WHERE t.token = :hash AND t.revoked = false AND t.expired = false")
    int revokeTokenIfValid(@Param("hash") String hash);

    // Bulk-deletes invalidated tokens that are past the retention cutoff so the
    // table does not grow without bound; current valid tokens are never touched.
    @Modifying
    @Query("DELETE FROM Token t WHERE (t.expired = true OR t.revoked = true) AND t.createdAt < :cutoff")
    int deleteExpiredOrRevokedBefore(@Param("cutoff") Instant cutoff);
}
