package org.teamsai.saimockbank.domain.account.dto;

public record AccountLookupResponse(
        String bankCode,
        String bankName,
        String accountHolderName
) {
    public static AccountLookupResponse from(BankAccountDTO account) {
        return new AccountLookupResponse(
                account.getBankCode(),
                resolveBankName(account.getBankCode()),
                account.getAccountHolderName()
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