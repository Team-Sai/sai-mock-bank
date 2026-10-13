package org.teamsai.saimockbank.domain.account.dto;

import org.teamsai.saimockbank.global.util.AccountNumberMasker;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.teamsai.saimockbank.domain.account.dto.BankCode.getBankNameByCode;

public record AccountDetailResponse(
        Long accountId,
        Long bankUserId,
        String bankCode,
        String bankName,
        String accountName,
        String accountHolderName,
        String maskedAccountNumber,
        BigDecimal balance,
        AccountStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static AccountDetailResponse from(BankAccountDTO account) {
        return new AccountDetailResponse(
                account.getAccountId(),
                account.getBankUserId(),
                account.getBankCode(),
                account.getBankName() == null ? getBankNameByCode(account.getBankCode()) : account.getBankName(),
                account.getAccountName(),
                account.getAccountHolderName(),
                AccountNumberMasker.mask(account.getAccountNumber()),
                account.getBalance(),
                account.getStatus(),
                account.getCreatedAt(),
                account.getUpdatedAt()
        );
    }
}
