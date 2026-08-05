package org.teamsai.saimockbank.domain.user.dto;

import jakarta.validation.constraints.NotBlank;

@NotBlank
public record MockBankLinkRequest(String name, String userToken){}
