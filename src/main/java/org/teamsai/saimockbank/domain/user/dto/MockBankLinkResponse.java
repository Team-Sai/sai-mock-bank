package org.teamsai.saimockbank.domain.user.dto;

import java.time.LocalDateTime;

public record MockBankLinkResponse(String userKey, LocalDateTime issuedAt){}
