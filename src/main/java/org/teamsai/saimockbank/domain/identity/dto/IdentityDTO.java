package org.teamsai.saimockbank.domain.identity.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class IdentityDTO {
    Long identityId;
    String name;
    LocalDateTime issuedAt; //userKey 발급날짜
    LocalDateTime createdAt; //bank 가입날짜
    LocalDateTime updatedAt;
    String userKeyHash;
}
