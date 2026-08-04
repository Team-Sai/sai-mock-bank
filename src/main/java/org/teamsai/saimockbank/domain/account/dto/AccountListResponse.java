package org.teamsai.saimockbank.domain.account.dto;

import org.teamsai.saimockbank.global.util.AccountNumberMasker;

import java.math.BigDecimal;

public record AccountListResponse(
        Long accountId,
        String bankCode,
        String bankName,
        String accountName,
        String accountHolderName,
        String maskedAccountNumber,
        AccountStatus status,
        BigDecimal balance
) {
    public static AccountListResponse from(BankAccountDTO account) {
        return new AccountListResponse(
                account.getAccountId(),
                account.getBankCode(),
                resolveBankName(account.getBankCode()),
                account.getAccountName(),
                account.getAccountHolderName(),
                AccountNumberMasker.mask(
                        account.getAccountNumber()
                ),
                account.getStatus(),
                account.getBalance()
        );
    }
    private static String resolveBankName(String bankCode) {
        return BankCode.getBankNameByCode(bankCode);
    }
}
