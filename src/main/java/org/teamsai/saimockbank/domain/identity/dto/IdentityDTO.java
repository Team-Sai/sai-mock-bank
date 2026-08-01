package org.teamsai.saimockbank.domain.identity.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class IdentityDTO {
    Long identityId;
    String name;
    String email;
    String userKey;
    LocalDateTime issuedAt;
}
