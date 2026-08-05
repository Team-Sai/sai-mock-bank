package org.teamsai.saimockbank.domain.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;


@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {
    Long bankUserId;
    String email;
    String password;
    String name;
    LocalDateTime issuedAt; //userKey 발급날짜
    LocalDateTime createdAt; //bank 가입날짜
    LocalDate birthDate;
    LocalDateTime updatedAt;
    String userToken;
    String userKeyHash;
}
