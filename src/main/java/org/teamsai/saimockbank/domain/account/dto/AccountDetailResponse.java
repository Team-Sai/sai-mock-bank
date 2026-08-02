package org.teamsai.saimockbank.domain.account.dto;

import org.teamsai.saimockbank.global.util.AccountNumberMasker;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Date;

public record AccountDetailResponse(
        Long accountId,
        Long identityId,
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
                account.getIdentityId(),
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
    private static String resolveBankName(String bankCode) {
        return switch (bankCode) {
            case "004" -> "국민은행";
            case "020" -> "우리은행";
            case "088" -> "신한은행";
            default -> "기타은행";
        };
    }
}
