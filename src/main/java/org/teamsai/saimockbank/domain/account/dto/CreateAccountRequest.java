package org.teamsai.saimockbank.domain.account.dto;

import org.teamsai.saimockbank.domain.account.exception.AccountErrorCode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.math.BigDecimal;
import java.math.RoundingMode;

public record CreateAccountRequest(String bankCode, String accountName, String bankName, BigDecimal initialBalance) {
    public CreateAccountRequest(String bankCode, String accountName) {
        this(bankCode, accountName, null, BigDecimal.ZERO);
    }
    @com.fasterxml.jackson.annotation.JsonAnySetter
    public void rejectUnknownCondition(String key, Object value) {
        throw AccountErrorCode.INVALID_ACCOUNT_CREATION.toException();
    }

    public static CreateAccountRequest defaults() {
        return new CreateAccountRequest("088", "입출금통장");
    }

    public CreateAccountRequest normalized() {
        String code = bankCode == null ? "088" : bankCode.strip();
        String name = accountName == null ? "입출금통장" : accountName.strip();
        boolean custom = "CUSTOM".equals(code);
        var knownBank = Arrays.stream(BankCode.values()).filter(bank -> bank.getCode().equals(code)).findFirst();
        String displayName = custom ? (bankName == null ? "" : bankName.strip())
                : knownBank.map(BankCode::getBankName).orElse("");
        if ((!custom && knownBank.isEmpty()) || invalidText(name) || invalidText(displayName)
                || (!custom && bankName != null && !bankName.strip().equals(displayName))) {
            throw AccountErrorCode.INVALID_ACCOUNT_CREATION.toException();
        }
        BigDecimal amount = initialBalance == null ? BigDecimal.ZERO : initialBalance;
        if (amount.signum() < 0 || amount.compareTo(new BigDecimal("99999999999999999.99")) > 0) {
            throw AccountErrorCode.INVALID_ACCOUNT_CREATION.toException();
        }
        try {
            amount = amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw AccountErrorCode.INVALID_ACCOUNT_CREATION.toException();
        }
        return new CreateAccountRequest(code, name, displayName, amount);
    }

    private static boolean invalidText(String text) {
        return text.isBlank() || text.length() > 100 || text.codePoints().anyMatch(Character::isISOControl);
    }

    // 계좌명이나 잔액이 변경되어도 최초 생성 요청 조건을 비교하기 위해 저장합니다.
    public String fingerprint() {
        try {
            var canonical = normalized();
            String value = "v2\n"
                    + canonical.bankCode() + "\n"
                    + canonical.accountName() + "\n"
                    + canonical.bankName() + "\n"
                    + canonical.initialBalance().toPlainString();

            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
