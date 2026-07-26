package org.teamsai.saimockbank.global.util;

public final class AccountNumberMasker {

    private AccountNumberMasker() {
    }

    public static String mask(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 6) {
            return accountNumber;
        }

        int visibleLength = 4;

        return "*".repeat(accountNumber.length() - visibleLength)
                + accountNumber.substring(
                accountNumber.length() - visibleLength
        );
    }
}