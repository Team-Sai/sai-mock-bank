package org.teamsai.saimockbank.domain.user.dto;

import jakarta.validation.constraints.NotBlank;

public record MockBankLinkRequest(
        @NotBlank(message = "이름은 필수입니다.")
        String name,
        @NotBlank(message = "userToken은 필수입니다.")
        String userToken){}
