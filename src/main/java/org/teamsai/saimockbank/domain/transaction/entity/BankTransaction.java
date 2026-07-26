package org.teamsai.saimockbank.domain.transaction.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import org.teamsai.saimockbank.global.common.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class BankTransaction extends BaseEntity {
    private Long transactionId;
    private String transactionKey;
    private TransactionType transactionType;
    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private String counterpartyName;
    private String counterpartyAccountNumber;
    private String memo;
    private LocalDateTime transactionAt;

    private Long transferId;
    private Long accountId;

}
