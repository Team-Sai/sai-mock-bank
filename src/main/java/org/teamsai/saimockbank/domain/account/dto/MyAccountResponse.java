package org.teamsai.saimockbank.domain.account.dto;

import java.math.BigDecimal;

public record MyAccountResponse(
        Long accountId,
        String bankCode,
        String bankName,
        String accountName,
        String accountHolderName,
        String maskedAccountNumber,
        BigDecimal balance,
        AccountStatus status,
        String accountNumber
) {
    public static MyAccountResponse from(BankAccountDTO account) {
        var summary = AccountListResponse.from(account);
        return new MyAccountResponse(
                summary.accountId(),
                summary.bankCode(),
                summary.bankName(),
                summary.accountName(),
                summary.accountHolderName(),
                summary.maskedAccountNumber(),
                summary.balance(),
                summary.status(),
                account.getAccountNumber()
        );
    }
}
