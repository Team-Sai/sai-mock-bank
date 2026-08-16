package org.teamsai.saimockbank.domain.link.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
        bankLinkService.confirmUserKey(request.userKey());
    }

    @PostMapping("/api/link/revoke-key")
    @ResponseBody
    public void revokeUserKey(@Valid @RequestBody ConfirmKeyRequest request) {
        bankLinkService.revokeUserKey(request.userKey());
    }

    public record ConfirmKeyRequest(
            @NotBlank(message = "userKey는 필수입니다.") String userKey
    ) {}
}
