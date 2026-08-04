package org.teamsai.saimockbank.domain.account.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

@AllArgsConstructor
@Getter
public enum BankCode {
    KOOKMIN("004", "국민은행"),
    WOORI("020", "우리은행"),
    SHINHAN("088", "신한은행");

    private final String code;
    private final String bankName;

    public static String getBankNameByCode(String code) {
        return Arrays.stream(values())
                .filter(bankCode -> bankCode.code.equals(code))
                .findFirst()
                .map(BankCode::getBankName)
                .orElse("알 수 없는 은행");
    }
}
