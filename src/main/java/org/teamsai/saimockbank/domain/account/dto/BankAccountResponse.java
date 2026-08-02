package org.teamsai.saimockbank.domain.account.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BankAccountResponse(
    Long identityId,
    String userKey,
    String userToken,
    String bankCode,
    String accountNumber,
    String accountName,
    String accountHolderName,
    BigDecimal balance,
    String status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
){}
