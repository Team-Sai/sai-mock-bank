package org.teamsai.saimockbank.domain.link.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.teamsai.saimockbank.domain.user.service.BankLinkService;

@Controller
@RequiredArgsConstructor
public class BankLinkCallbackController {
    private final BankLinkService bankLinkService;

    @PostMapping("/api/link/confirm-key")
    @ResponseBody
    public void confirmUserKey(@Valid @RequestBody ConfirmKeyRequest request) {
        bankLinkService.confirmUserKey(request.userKey());
    }

    public record ConfirmKeyRequest(
            @NotBlank(message = "userKey는 필수입니다.") String userKey
    ) {}
}
