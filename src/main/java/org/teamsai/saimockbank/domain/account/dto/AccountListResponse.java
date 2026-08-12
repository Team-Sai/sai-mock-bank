package org.teamsai.saimockbank.domain.account.dto;
import org.teamsai.saimockbank.global.util.AccountNumberMasker;

import java.math.BigDecimal;

import static org.teamsai.saimockbank.domain.account.dto.BankCode.getBankNameByCode;

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
                getBankNameByCode(account.getBankCode()),
                account.getAccountHolderName(),
                account.getAccountName(),
                AccountNumberMasker.mask(
                        account.getAccountNumber()
                ),
                account.getBalance(),
                account.getStatus()
        );
    }
}
