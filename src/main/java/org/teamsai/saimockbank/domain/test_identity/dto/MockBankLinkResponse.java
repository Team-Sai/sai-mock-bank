package org.teamsai.saimockbank.domain.test_identity.dto;

import java.time.LocalDateTime;

public record MockBankLinkResponse(String userKey, LocalDateTime issuedAt){}
