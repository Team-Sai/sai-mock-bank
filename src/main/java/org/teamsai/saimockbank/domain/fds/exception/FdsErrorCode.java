package org.teamsai.saimockbank.domain.fds.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.teamsai.saimockbank.global.exception.BaseErrorCode;
import org.teamsai.saimockbank.global.exception.DomainException;

@Getter
@RequiredArgsConstructor
public enum FdsErrorCode
        implements BaseErrorCode<DomainException> {

    EVALUATION_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "FDS 평가 이력을 찾을 수 없습니다."
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