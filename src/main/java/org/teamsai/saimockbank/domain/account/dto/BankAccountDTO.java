package org.teamsai.saimockbank.domain.account.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@ToString
public class BankAccountDTO{
    private Long accountId;
    private Long identityId;
    private String bankCode;
    private String accountNumber;
    private String accountName;
    private String accountHolderName;
    private BigDecimal balance;
    private AccountStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
