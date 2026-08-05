package org.teamsai.saimockbank.domain.account.dto;

import org.teamsai.saimockbank.global.util.AccountNumberMasker;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
                resolveBankName(account.getBankCode()),
                account.getAccountName(),
                account.getAccountHolderName(),
                AccountNumberMasker.mask(account.getAccountNumber()),
                account.getBalance(),
                account.getStatus(),
                account.getCreatedAt(),
                account.getUpdatedAt()
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
