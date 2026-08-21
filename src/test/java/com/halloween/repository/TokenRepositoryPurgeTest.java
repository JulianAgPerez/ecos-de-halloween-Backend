package com.halloween.repository;

import com.halloween.entities.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class TokenRepositoryPurgeTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private TokenRepository tokenRepository;

    @Test
    void deleteExpiredOrRevokedBefore_removesOnlyInvalidTokensPastCutoff() {
        Instant now = Instant.now();
        User alice = entityManager.persistAndFlush(User.builder()
                .name("Alice")
                .email("alice-purge@test.com")
                .password("hashed")
                .build());

        entityManager.persist(token("stale-expired", true, false, now.minusSeconds(60), alice));
        entityManager.persist(token("stale-revoked", false, true, now.minusSeconds(60), alice));
        entityManager.persist(token("fresh-expired", true, false, now.plusSeconds(60), alice));
        entityManager.persist(token("valid-old", false, false, now.minusSeconds(60), alice));
        entityManager.flush();

        int deleted = tokenRepository.deleteExpiredOrRevokedBefore(now);

        assertThat(deleted).isEqualTo(2);
        List<Token> remaining = tokenRepository.findAll();
        assertThat(remaining)
                .extracting(Token::getToken)
                .containsExactlyInAnyOrder("fresh-expired", "valid-old");
    }

    private Token token(String value, boolean expired, boolean revoked, Instant createdAt, User user) {
        return Token.builder()
                .user(user)
                .token(value)
                .expired(expired)
                .revoked(revoked)
                .createdAt(createdAt)
                .build();
    }
}
