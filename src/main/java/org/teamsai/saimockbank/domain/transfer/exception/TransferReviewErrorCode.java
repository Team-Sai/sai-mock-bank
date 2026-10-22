
package org.teamsai.saimockbank.domain.transfer.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.teamsai.saimockbank.global.exception.BaseErrorCode;
import org.teamsai.saimockbank.global.exception.DomainException;

@Getter
@RequiredArgsConstructor
public enum TransferReviewErrorCode
        implements BaseErrorCode<DomainException> {

    FDS_UNAVAILABLE(
            HttpStatus.SERVICE_UNAVAILABLE,
            "FDS 위험 평가를 수행할 수 없습니다."
    ),

    REVIEW_NOT_PENDING(
            HttpStatus.CONFLICT,
            "심사 대기 중인 이체가 아닙니다."
    ),

    INVALID_REVIEW_REASON(
            HttpStatus.BAD_REQUEST,
            "심사 사유가 올바르지 않습니다."
    ),

    INVALID_PAGINATION(
            HttpStatus.BAD_REQUEST,
            "페이지는 0 이상, 크기는 1~100이어야 합니다."
    );

    private final HttpStatus httpStatus;
    private final String message;

    @Override
    public DomainException toException() {
        return new DomainException(httpStatus, this);
    }
}
