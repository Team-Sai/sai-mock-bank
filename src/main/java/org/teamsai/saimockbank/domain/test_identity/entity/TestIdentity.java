package org.teamsai.saimockbank.domain.test_identity.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class TestIdentity {
    Long identityId;
    String name;
    String email;
    String userKey;
    LocalDateTime issuedAt;
}
