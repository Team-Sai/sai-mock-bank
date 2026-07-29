package org.teamsai.saimockbank.domain.transfer.exception;


import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.teamsai.saimockbank.global.exception.BaseErrorCode;
import org.teamsai.saimockbank.global.exception.DomainException;

@Getter
@RequiredArgsConstructor
public enum TransferErrorCode
        implements BaseErrorCode<DomainException> {

    INVALID_TRANSFER_REQUEST(
            HttpStatus.BAD_REQUEST,
            "이체 요청값이 올바르지 않습니다."
    ),

    SAME_ACCOUNT_TRANSFER(
            HttpStatus.BAD_REQUEST,
            "동일한 계좌로 이체할 수 없습니다."
    ),

    ACCOUNT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "계좌를 찾을 수 없습니다."
    ),

    ACCOUNT_ACCESS_DENIED(
            HttpStatus.FORBIDDEN,
            "출금계좌의 소유자가 아닙니다."
    ),

    ACCOUNT_UNAVAILABLE(
            HttpStatus.BAD_REQUEST,
            "사용할 수 없는 계좌입니다."
    ),

    INSUFFICIENT_BALANCE(
            HttpStatus.BAD_REQUEST,
            "출금계좌 잔액이 부족합니다."
    ),

    DUPLICATE_REQUEST_KEY(
            HttpStatus.CONFLICT,
            "이미 사용된 요청 고유키입니다."
    ),

    TRANSFER_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "이체 내역을 찾을 수 없습니다."
    ),

    BALANCE_UPDATE_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "계좌 잔액 변경에 실패했습니다."
    ),

    TRANSFER_SAVE_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "이체 기록 저장에 실패했습니다."
    ),

    TRANSACTION_SAVE_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "거래내역 저장에 실패했습니다."
    );

    private final HttpStatus httpStatus;
    private final String message;

    @Override
    public DomainException toException() {
        return new DomainException(httpStatus, this);
    }
}