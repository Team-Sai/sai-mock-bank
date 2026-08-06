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
    private Long bankUserId;
    private String email;
    private String password;
    private String name;
    private LocalDateTime issuedAt; //userKey 발급날짜
    private LocalDateTime createdAt; //bank 가입날짜
    private LocalDate birthDate;
    private LocalDateTime updatedAt;
    private String userToken;
    private String userKeyHash;
}
