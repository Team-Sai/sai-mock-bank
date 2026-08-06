package org.teamsai.saimockbank.domain.user.dto.response;

import lombok.Builder;
import lombok.Getter;
import org.teamsai.saimockbank.domain.user.dto.UserDTO;

@Getter
@Builder
public class UserTokenLookupResponse {

    private String userToken;
    private String name;

    public static UserTokenLookupResponse from(UserDTO user){
        return UserTokenLookupResponse.builder()
                .userToken(user.getUserToken())
                .name(user.getName())
                .build();
    }
}
