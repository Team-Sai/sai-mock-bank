package org.teamsai.saimockbank.domain.account.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.teamsai.saimockbank.global.exception.BaseErrorCode;
import org.teamsai.saimockbank.global.exception.DomainException;

@Getter
@RequiredArgsConstructor
public enum AccountErrorCode
        implements BaseErrorCode<DomainException> {

    INVALID_ACCOUNT_CREATION(HttpStatus.BAD_REQUEST, "계좌 생성 조건이 올바르지 않습니다."),
    ACCOUNT_CREATION_CONFLICT(HttpStatus.CONFLICT, "같은 요청 키로 다른 조건의 계좌를 생성할 수 없습니다."),

    INVALID_ACCOUNT_REQUEST(
            HttpStatus.BAD_REQUEST,
            "계좌 조회 요청값이 올바르지 않습니다."
    ),

    ACCOUNT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "계좌를 찾을 수 없습니다."
    ),

    ACCOUNT_ACCESS_DENIED(
            HttpStatus.FORBIDDEN,
            "해당 계좌에 접근할 수 없습니다."
    ),
    INVALID_RETURN_URL(
            HttpStatus.BAD_REQUEST,
            "허용되지 않은 콜백 주소입니다."),
    INVALID_LINK_REQUEST(
            HttpStatus.BAD_REQUEST,
            "연동할 계좌를 선택해주세요."),
    INVALID_ACCOUNT_SELECTION(
            HttpStatus.FORBIDDEN,
            "본인 소유의 연동 가능한 계좌만 선택할 수 있습니다.");

    private final HttpStatus httpStatus;
    private final String message;

    @Override
    public DomainException toException() {
        return new DomainException(httpStatus, this);
    }
}