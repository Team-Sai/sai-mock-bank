package org.teamsai.saimockbank.domain.transaction.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class BankTransactionDTO{
    private Long transactionId;
    private String transactionKey;
    private TransactionType transactionType;
    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private String counterpartyName;
    private String counterpartyAccountNumber;
    private String memo;
    private LocalDateTime transactionAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private Long transferId;
    private Long accountId;

    @Builder
    private BankTransactionDTO(
            String transactionKey,
            TransactionType transactionType,
            BigDecimal amount,
            BigDecimal balanceAfter,
            String counterpartyName,
            String counterpartyAccountNumber,
            String memo,
            LocalDateTime transactionAt,
            Long transferId,
            Long accountId,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.transactionKey = transactionKey;
        this.transactionType = transactionType;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.counterpartyName = counterpartyName;
        this.counterpartyAccountNumber = counterpartyAccountNumber;
        this.memo = memo;
        this.transactionAt = transactionAt;
        this.transferId = transferId;
        this.accountId = accountId;
        this.createdAt = createdAt;
        this.updatedAt =updatedAt;
    }

}
