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
        BigDecimal balance,
        AccountStatus status
) {
    public static AccountListResponse from(BankAccountDTO account) {
        return new AccountListResponse(
                account.getAccountId(),
                account.getBankCode(),
                resolveBankName(account.getBankCode()),
                account.getAccountHolderName(),
                account.getAccountName(),
                AccountNumberMasker.mask(
                        account.getAccountNumber()
                ),
                account.getBalance(),
                account.getStatus()
        );
    }
    private static String resolveBankName(String bankCode) {
        return switch (bankCode) {
            case "004" -> "국민은행";
            case "020" -> "우리은행";
            case "088" -> "신한은행";
            default -> "기타은행";
        };
    }
}
