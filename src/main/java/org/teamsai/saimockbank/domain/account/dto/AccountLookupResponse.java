package org.teamsai.saimockbank.domain.account.dto;

import static org.teamsai.saimockbank.domain.account.dto.BankCode.getBankNameByCode;

public record AccountLookupResponse(
        String bankCode,
        String bankName,
        String accountHolderName
) {
    public static AccountLookupResponse from(BankAccountDTO account) {
        return new AccountLookupResponse(
                account.getBankCode(),
                getBankNameByCode(account.getBankCode()),
                account.getAccountHolderName()
        );
    }
}