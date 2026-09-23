package com.halloween.repository;

import com.halloween.entities.User;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TokenTest {

    @Test
    void equals_isBasedOnIdOnly() {
        Token a = Token.builder().id(1L).token("digest-a").build();
        Token b = Token.builder().id(1L).token("digest-b").build();
        Token c = Token.builder().id(2L).token("digest-a").build();

        assertThat(a).isEqualTo(b);
        assertThat(a).isNotEqualTo(c);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void toString_doesNotLeakTokenDigestOrUser() {
        User user = User.builder().id(1L).email("a@test.com").build();
        Token token = Token.builder().id(1L).token("sha256-digest-value").user(user)
                .createdAt(Instant.now()).build();

        assertThat(token.toString()).doesNotContain("sha256-digest-value");
        assertThat(token.toString()).doesNotContain("a@test.com");
    }
}
