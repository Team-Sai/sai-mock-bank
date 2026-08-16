package org.teamsai.saimockbank.domain.link.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.teamsai.saimockbank.domain.user.service.BankLinkService;

@Controller
@RequiredArgsConstructor
public class BankLinkCalbackController {
    private final BankLinkService bankLinkService;
    
    @PostMapping("/api/link/confirm-key")
    @ResponseBody
    public void confirmUserKey(@RequestBody ConfirmKeyRequest request) {
        bankLinkService.confirmUserKey(request.userKey());
    }

    public record ConfirmKeyRequest(String userKey) {}
}
