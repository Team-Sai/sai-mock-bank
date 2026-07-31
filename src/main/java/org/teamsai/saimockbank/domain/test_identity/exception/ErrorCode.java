package org.teamsai.saimockbank.domain.test_identity.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCode {
    CORRECT_USER_NOT_FOUND("401","일치하는 회원 정보가 없습니다."),
    ALREADY_LINKED_USER("400","이미 연동된 회원입니다."),
    INVALID_USER_KEY("401","타당하지 않은 사용자 키입니다.");
    private final String code;
    private final String message;
}
