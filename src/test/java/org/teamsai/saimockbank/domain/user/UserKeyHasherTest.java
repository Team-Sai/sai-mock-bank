package org.teamsai.saimockbank.domain.user;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.teamsai.saimockbank.domain.user.service.UserKeyHasher;
import static org.assertj.core.api.Assertions.assertThat;

class UserKeyHasherTest {
    @Test void issuanceCanBeReplayedAfterRestartButIsBoundToOwnerOperationAndSecret() {
        var first = new UserKeyHasher();
        var restarted = new UserKeyHasher();
        ReflectionTestUtils.setField(first, "secret", "test-only-secret");
        ReflectionTestUtils.setField(restarted, "secret", "test-only-secret");
        String key = first.deriveUserKey(1L, "op");
        assertThat(key).matches("mb_[A-Za-z0-9_-]{43}");
        assertThat(restarted.deriveUserKey(1L, "op")).isEqualTo(key);
        assertThat(first.deriveUserKey(2L, "op")).isNotEqualTo(key);
        assertThat(first.deriveUserKey(1L, "next-op")).isNotEqualTo(key);
        ReflectionTestUtils.setField(restarted, "secret", "different-secret");
        assertThat(restarted.deriveUserKey(1L, "op")).isNotEqualTo(key);
    }
}
