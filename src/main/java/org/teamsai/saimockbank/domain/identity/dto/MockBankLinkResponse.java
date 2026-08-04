package org.teamsai.saimockbank.domain.identity.dto;

import java.time.LocalDateTime;

public record MockBankLinkResponse(String userKey, LocalDateTime issuedAt){}
