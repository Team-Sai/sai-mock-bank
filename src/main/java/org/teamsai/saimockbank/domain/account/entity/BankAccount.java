package org.teamsai.saimockbank.domain.account.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import org.teamsai.saimockbank.global.common.BaseEntity;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
public class BankAccount extends BaseEntity {
    private Long accountId;
    private String userKey;
    private String bankCode;
    private String accountNumber;
    private String accountName;
    private String ownerName;
    private BigDecimal balance;
    private AccountStatus status;

}
