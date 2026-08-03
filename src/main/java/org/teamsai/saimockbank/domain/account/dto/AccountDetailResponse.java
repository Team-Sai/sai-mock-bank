package org.teamsai.saimockbank.domain.account.dto;

import org.teamsai.saimockbank.global.util.AccountNumberMasker;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AccountDetailResponse(
        Long accountId,
        Long bankIdentityId,
        String bankCode,
        String accountHolderName,
        String accountName,
        String accountNumber,
        BigDecimal balance,
        AccountStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static AccountDetailResponse from(BankAccountDTO account) {
        return new AccountDetailResponse(
                account.getAccountId(),
                account.getBankIdentityId(),
                resolveBankName(account.getBankCode()),
                account.getAccountHolderName(),
                account.getAccountName(),
                AccountNumberMasker.mask(
                        account.getAccountNumber()
                ),
                account.getBalance(),
                account.getStatus(),
                account.getCreatedAt(),
                account.getUpdatedAt()
        );
    }

    public static AccountDetailResponse from(AccountWithOwnerDTO account) {
        return new AccountDetailResponse(
                account.getAccountId(),
                account.getBankIdentityId(),
                resolveBankName(account.getBankCode()),
                account.getAccountHolderName(),
                account.getAccountName(),
                AccountNumberMasker.mask(account.getAccountNumber()),
                account.getBalance(),
                account.getStatus(),
                account.getCreatedAt(),
                account.getUpdatedAt()
        );
    }
    
    private static String resolveBankName(String bankCode) {
        return BankCode.getBankNameByCode(bankCode);
    }
}
