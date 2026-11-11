package org.teamsai.saimockbank.domain.user;

import org.junit.jupiter.api.Test;
import org.teamsai.saimockbank.domain.user.dto.UserDTO;
import org.teamsai.saimockbank.domain.user.dto.response.UserResponse;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class UserResponseTest {
    @Test
    void myPageResponseIncludesMemberIdentityAndActualJoinDate() {
        LocalDateTime joinedAt = LocalDateTime.of(2026, 11, 9, 12, 0);
        UserResponse response = UserResponse.from(UserDTO.builder()
                .name("검증 사용자")
                .email("test@example.com")
                .userToken("SAI-TEST")
                .createdAt(joinedAt)
                .build());

        assertThat(response.getName()).isEqualTo("검증 사용자");
        assertThat(response.getEmail()).isEqualTo("test@example.com");
        assertThat(response.getUserToken()).isEqualTo("SAI-TEST");
        assertThat(response.getCreatedAt()).isEqualTo(joinedAt);
    }
}
