package org.teamsai.saimockbank.domain.link.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.teamsai.saimockbank.domain.user.service.BankLinkService;

/**
 * 사이원장이 계좌 연동 콜백 처리를 마친 뒤 서버 간 호출로
 * PENDING 상태의 userKey를 ACTIVE로 확정시키는 엔드포인트
 * 이 컨트롤러를 호출하는 코드는 mock-bank 안에 없음.
 */
@Controller
@RequiredArgsConstructor
public class BankLinkCallbackController {
    private final BankLinkService bankLinkService;

    @PostMapping("/api/link/confirm-key")
    @ResponseBody
    public void confirmUserKey(@Valid @RequestBody ConfirmKeyRequest request) {
        bankLinkService.confirmUserKey(request.userKey(), request.operationId());
    }

    @PostMapping("/api/link/revoke-key")
    @ResponseBody
    public void revokeUserKey(@Valid @RequestBody RevokeKeyRequest request) {
        bankLinkService.revokeUserKey(request.userKey());
    }

    @PostMapping("/api/link/recover-key")
    @ResponseBody
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void recoverUserKey(@Valid @RequestBody RecoverKeyRequest request) {
        bankLinkService.recoverUserKey(request.userToken(), request.currentUserKey(), request.previousUserKey(), request.operationId());
    }

    public record RecoverKeyRequest(
            @NotBlank(message = "userToken은 필수입니다.") String userToken,
            @NotBlank(message = "currentUserKey는 필수입니다.") String currentUserKey,
            @Pattern(regexp = "(?s).*\\S.*", message = "previousUserKey는 null 또는 공백이 아닌 키여야 합니다.")
            String previousUserKey,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String operationId
    ) {}

    public record ConfirmKeyRequest(
            @NotBlank(message = "userKey는 필수입니다.") String userKey,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String operationId
    ) {}

    public record RevokeKeyRequest(
            @NotBlank(message = "userKey는 필수입니다.") String userKey
    ) {}
}
