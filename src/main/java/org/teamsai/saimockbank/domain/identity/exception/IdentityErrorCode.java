package org.teamsai.saimockbank.domain.identity.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.teamsai.saimockbank.global.exception.BaseErrorCode;
import org.teamsai.saimockbank.global.exception.DomainException;

@Getter
@AllArgsConstructor
public enum IdentityErrorCode implements BaseErrorCode<DomainException> {
    CORRECT_USER_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "일치하는 회원 정보가 없습니다."
    ),
    CONFLICT(
            HttpStatus.ALREADY_REPORTED,
            "이미 연동된 회원입니다."
    ),
    INVALID_USER_KEY(
            HttpStatus.UNAUTHORIZED,
            "타당하지 않은 사용자 키입니다."
    );
    private final HttpStatus httpStatus;
    private final String message;

    @Override
    public DomainException toException() {
        return new DomainException(httpStatus, this);
    }
}
