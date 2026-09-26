package org.teamsai.saimockbank.domain.identity.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.teamsai.saimockbank.global.exception.BaseErrorCode;
import org.teamsai.saimockbank.global.exception.DomainException;

@Getter
@RequiredArgsConstructor
public enum IdentityErrorCode
        implements BaseErrorCode<DomainException> {

    INVALID_OPERATION_ID(
            HttpStatus.BAD_REQUEST,
            "연동 작업 식별값이 올바르지 않습니다."
    ),
    UNAUTHENTICATED_USER(
            HttpStatus.UNAUTHORIZED,
            "로그인이 필요하거나 토큰이 유효하지 않습니다."
    ),

    INVALID_IDENTITY_VERIFICATION_ID(
            HttpStatus.BAD_REQUEST,
            "본인인증 식별값이 올바르지 않습니다."
    ),

    INVALID_IDENTITY_PURPOSE(
            HttpStatus.BAD_REQUEST,
            "본인인증 목적이 올바르지 않습니다."
    ),

    IDENTITY_VERIFICATION_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "본인인증 요청을 찾을 수 없습니다."
    ),

    IDENTITY_VERIFICATION_FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "해당 본인인증 요청에 접근할 권한이 없습니다."
    ),

    INVALID_IDENTITY_VERIFICATION_STATUS(
            HttpStatus.CONFLICT,
            "현재 상태에서는 본인인증을 완료할 수 없습니다."
    ),

    IDENTITY_VERIFICATION_CREATE_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "본인인증 요청 생성에 실패했습니다."
    ),

    IDENTITY_VERIFICATION_UPDATE_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "본인인증 결과 저장에 실패했습니다."
    ),

    IDENTITY_VERIFICATION_CONSUME_FAILED(
            HttpStatus.CONFLICT,
            "사용할 수 없는 본인인증 정보입니다."
    ),

    PORTONE_VERIFICATION_NOT_VERIFIED(
            HttpStatus.CONFLICT,
            "포트원 본인인증이 정상적으로 완료되지 않았습니다."
    ),

    PORTONE_API_CALL_FAILED(
            HttpStatus.BAD_GATEWAY,
            "포트원 본인인증 서버 호출에 실패했습니다."
    ),

    PORTONE_API_INVALID_RESPONSE(
            HttpStatus.BAD_GATEWAY,
            "포트원 본인인증 서버에서 올바르지 않은 응답을 받았습니다."
    ),
    IDENTITY_VERIFICATION_NOT_COMPLETED(
            HttpStatus.CONFLICT,
            "본인인증이 아직 완료되지 않았습니다."
    ),

    IDENTITY_USER_INFORMATION_MISSING(
            HttpStatus.CONFLICT,
            "회원의 이름 또는 생년월일 정보가 없습니다."
    ),

    IDENTITY_INFORMATION_MISMATCH(
            HttpStatus.BAD_REQUEST,
            "회원정보와 본인인증 정보가 일치하지 않습니다."
    ),
    LINK_KEY_UPDATE_CONFLICT(
            HttpStatus.CONFLICT,
            "연동키 갱신에 실패했습니다. 잠시 후 다시 시도해주세요."
    ),
    PENDING_KEY_ALREADY_EXISTS(
            HttpStatus.CONFLICT,
            "이미 진행 중인 연동키 발급 요청이 있습니다. 잠시 후 다시 시도해주세요."
    ),
    PENDING_KEY_NOT_FOUND(
            HttpStatus.CONFLICT,
            "확정할 대기 중인 연동키를 찾을 수 없습니다."
    ),
    KEY_RECOVERY_EXPIRED(
            HttpStatus.CONFLICT,
            "연동키 복구 기한이 만료되어 상태 확인이 필요합니다."
    ),
    KEY_RECOVERY_CONFLICT(
            HttpStatus.CONFLICT,
            "은행 키 상태가 변경되어 연동을 복구할 수 없습니다."
    ),
    ACTIVE_KEY_NOT_FOUND(
            HttpStatus.CONFLICT,
            "해지할 활성화된 연동키를 찾을 수 없습니다."
    );
    private final HttpStatus httpStatus;
    private final String message;

    @Override
    public DomainException toException() {
        return new DomainException(httpStatus, this);
    }
}
