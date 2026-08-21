package com.halloween.entities;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    @Test
    void equals_isBasedOnIdOnly() {
        User a = User.builder().id(1L).name("Admin").email("a@test.com").password("secret").build();
        User b = User.builder().id(1L).name("Other").email("b@test.com").password("other-secret").build();
        User c = User.builder().id(2L).name("Admin").email("a@test.com").password("secret").build();

        assertThat(a).isEqualTo(b);
        assertThat(a).isNotEqualTo(c);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void toString_doesNotLeakPassword() {
        User user = User.builder().id(1L).name("Admin").email("a@test.com").password("super-secret").build();

        assertThat(user.toString()).doesNotContain("super-secret");
    }
}
