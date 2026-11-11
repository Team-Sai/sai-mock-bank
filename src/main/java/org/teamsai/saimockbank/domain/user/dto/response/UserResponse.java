package org.teamsai.saimockbank.domain.user.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.teamsai.saimockbank.domain.user.dto.UserDTO;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    private String userToken;
    private String email;
    private String name;
    private LocalDate birthDate;
    private LocalDateTime createdAt;

    public static UserResponse from(UserDTO user) {
        return UserResponse.builder()
                .userToken(user.getUserToken())
                .email(user.getEmail())
                .name(user.getName())
                .birthDate(user.getBirthDate())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
