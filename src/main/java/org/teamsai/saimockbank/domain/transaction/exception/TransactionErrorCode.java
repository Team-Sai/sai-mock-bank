package org.teamsai.saimockbank.domain.transaction.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.teamsai.saimockbank.global.exception.BaseErrorCode;
import org.teamsai.saimockbank.global.exception.DomainException;

@Getter
@RequiredArgsConstructor
public enum TransactionErrorCode
        implements BaseErrorCode<DomainException> {

    INVALID_TRANSACTION_REQUEST(
            HttpStatus.BAD_REQUEST,
            "거래내역 조회 요청값이 올바르지 않습니다."
    ),

    ACCOUNT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "계좌를 찾을 수 없습니다."
    ),

    ACCOUNT_ACCESS_DENIED(
            HttpStatus.FORBIDDEN,
            "해당 계좌에 접근할 수 없습니다."
    );

    private final HttpStatus httpStatus;
    private final String message;

    @Override
    public DomainException toException() {
        return new DomainException(httpStatus, this);
    }
}