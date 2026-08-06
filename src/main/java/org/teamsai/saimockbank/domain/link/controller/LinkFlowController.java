package org.teamsai.saimockbank.domain.link.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;
import org.teamsai.saimockbank.domain.account.dto.AccountListResponse;
import org.teamsai.saimockbank.domain.account.service.BankAccountService;
import org.teamsai.saimockbank.domain.user.dto.MockBankLinkResponse;
import org.teamsai.saimockbank.domain.user.service.BankLinkService;
import org.teamsai.saimockbank.global.security.CustomUserDetails;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class LinkFlowController {

    private final BankLinkService bankLinkService;
    private final BankAccountService bankAccountService;

    @GetMapping("/link/start")
    public String linkStart(
            @RequestParam String returnUrl,
            @RequestParam String state,
            @RequestParam(required = false) String excludeAccountIds,
            HttpSession session
    ) {
        session.setAttribute("linkReturnUrl", returnUrl);
        session.setAttribute("linkState", state);
        session.setAttribute("excludeAccountIds", excludeAccountIds);
        return "redirect:/login?next=/link/select";
    }

    @GetMapping("/link/select")
    public String linkSelectPage() {
        return "link/link-select";
    }

    @GetMapping("/api/bank-user/accounts")
    @ResponseBody
    public List<AccountListResponse> myAccounts(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpSession session
    ) {
        List<AccountListResponse> accounts = bankAccountService.getMyAccounts(userDetails.getUserId());

        String excludeParam = (String) session.getAttribute("excludeAccountIds");
        if (excludeParam != null && !excludeParam.isBlank()) {
            Set<Long> excludeIds = Arrays.stream(excludeParam.split(","))
                    .map(Long::parseLong)
                    .collect(Collectors.toSet());

            accounts = accounts.stream()
                    .filter(account -> !excludeIds.contains(account.accountId()))
                    .toList();
        }

        return accounts;
    }

    @PostMapping("/api/link/confirm")
    @ResponseBody
    public LinkConfirmResponse confirmLink(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody LinkConfirmRequest request,
            HttpSession session
    ) {
        MockBankLinkResponse linkResponse = bankLinkService.issueUserKey(
                userDetails.getName(),
                userDetails.getUserToken()
        );
        String userKey = linkResponse.userKey();

        String returnUrl = (String) session.getAttribute("linkReturnUrl");
        String state = (String) session.getAttribute("linkState");

        String accountIdsParam = request.accountIds().stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));

        String redirectUrl = UriComponentsBuilder
                .fromUriString(returnUrl)
                .queryParam("state", state)
                .queryParam("userKey", userKey)
                .queryParam("accountIds", accountIdsParam)
                .toUriString();

        session.removeAttribute("linkReturnUrl");
        session.removeAttribute("linkState");
        session.removeAttribute("excludeAccountIds");

        return new LinkConfirmResponse(redirectUrl);
    }

    public record LinkConfirmRequest(List<Long> accountIds) {}
    public record LinkConfirmResponse(String redirectUrl) {}
}