package org.teamsai.saimockbank.domain.identity.dto;

import jakarta.validation.constraints.NotBlank;

@NotBlank
public record MockBankLinkRequest(String name, String userToken){}
