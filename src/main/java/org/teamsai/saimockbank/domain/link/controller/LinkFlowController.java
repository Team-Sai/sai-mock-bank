package org.teamsai.saimockbank.domain.link.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;
import org.teamsai.saimockbank.domain.account.dto.AccountListResponse;
import org.teamsai.saimockbank.domain.account.exception.AccountErrorCode;
import org.teamsai.saimockbank.domain.account.service.BankAccountService;
import org.teamsai.saimockbank.domain.user.dto.MockBankLinkResponse;
import org.teamsai.saimockbank.domain.user.dto.response.UserResponse;
import org.teamsai.saimockbank.domain.user.exception.UserErrorCode;
import org.teamsai.saimockbank.domain.user.service.BankLinkService;
import org.teamsai.saimockbank.domain.user.service.UserService;
import org.teamsai.saimockbank.global.jwt.JwtTokenProvider;
import org.teamsai.saimockbank.global.security.CustomUserDetails;
import org.teamsai.saimockbank.global.util.LinkIdentityHasher;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequiredArgsConstructor
public class LinkFlowController {

    private final BankLinkService bankLinkService;
    private final BankAccountService bankAccountService;
    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${link-identity.hash-secret}")
    private String linkIdentityHashSecret;

    @Value("#{'${link-state.allowed-return-urls}'.split(',')}")
    private List<String> allowedReturnUrls;

    private static final String SESSION_RETURN_URL = "linkReturnUrl";
    private static final String SESSION_STATE = "linkState";
    private static final String SESSION_EXCLUDE_ACCOUNT_IDS = "excludeAccountIds";
    private static final String SESSION_EXPECTED_IDENTITY_HASH = "linkExpectedIdentityHash";

    @GetMapping("/link/start")
    public String linkStart(
            @RequestParam String returnUrl,
            @RequestParam String state,
            @RequestParam(required = false) String excludeAccountIds,
            HttpSession session
    ) {
        log.info("[DEBUG] linkStart 호출 - returnUrl: {}, state: {}", returnUrl, state); // 임시 로그

        validateReturnUrl(returnUrl);

        return jwtTokenProvider.getIdentityHashFromLinkState(state)
                .map(expectedIdentityHash -> {
                    session.setAttribute(SESSION_RETURN_URL, returnUrl);
                    session.setAttribute(SESSION_STATE, state);
                    session.setAttribute(SESSION_EXCLUDE_ACCOUNT_IDS, excludeAccountIds);
                    session.setAttribute(SESSION_EXPECTED_IDENTITY_HASH, expectedIdentityHash);
                    return "redirect:/login?next=/link/select";
                })
                .orElseGet(() -> {
                    log.warn("[LinkFlowController] 유효하지 않은 state로 연동 시작 시도");
                    return "redirect:/link/invalid";
                });
    }

    @GetMapping("/link/select")
    public String linkSelectPage() {
        return "link/link-select";
    }

    @GetMapping("/link/identity-mismatch")
    public String linkIdentityMismatchPage() {
        return "link/link-identity-mismatch";
    }

    @GetMapping("/link/invalid")
    public String linkInvalidPage() {
        return "link/link-invalid";
    }

    @GetMapping("/api/bank-user/accounts")
    @ResponseBody
    public List<AccountListResponse> myAccounts(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpSession session
    ) {
        validateExpectedIdentity(session, userDetails);

        List<AccountListResponse> accounts = bankAccountService.getMyAccounts(userDetails.getUserId());

        String excludeParam = (String) session.getAttribute(SESSION_EXCLUDE_ACCOUNT_IDS);
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
        validateExpectedIdentity(session, userDetails);
        validateAccountIds(userDetails, session, request.accountIds());

        MockBankLinkResponse linkResponse = bankLinkService.issueUserKey(
                userDetails.getName(),
                userDetails.getUserToken()
        );
        String userKey = linkResponse.userKey();

        String returnUrl = (String) session.getAttribute(SESSION_RETURN_URL);
        String state = (String) session.getAttribute(SESSION_STATE);

        String accountIdsParam = request.accountIds().stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));

        String redirectUrl = UriComponentsBuilder
                .fromUriString(returnUrl)
                .queryParam("state", state)
                .queryParam("userKey", userKey)
                .queryParam("accountIds", accountIdsParam)
                .toUriString();

        session.removeAttribute(SESSION_RETURN_URL);
        session.removeAttribute(SESSION_STATE);
        session.removeAttribute(SESSION_EXCLUDE_ACCOUNT_IDS);
        session.removeAttribute(SESSION_EXPECTED_IDENTITY_HASH);

        return new LinkConfirmResponse(redirectUrl);
    }

    private void validateAccountIds(CustomUserDetails userDetails, HttpSession session, List<Long> requestedAccountIds){
        if(requestedAccountIds == null || requestedAccountIds.isEmpty()){
            throw AccountErrorCode.INVALID_ACCOUNT_REQUEST.toException();
        }

        List<AccountListResponse> myAccounts = bankAccountService.getMyAccounts(userDetails.getUserId());
        String excludeParam = (String) session.getAttribute(SESSION_EXCLUDE_ACCOUNT_IDS);
        Set<Long> excludeIds = (excludeParam != null && !excludeParam.isBlank())
                ? Arrays.stream(excludeParam.split(","))
                  .map(Long::parseLong)
                  .collect(Collectors.toSet()) :
                Set.of();

        Set<Long> eligibleAccountIds = myAccounts.stream()
                .map(AccountListResponse::accountId)
                .filter(id -> !excludeIds.contains(id))
                .collect(Collectors.toSet());
        boolean allValid = eligibleAccountIds.containsAll(requestedAccountIds);
        if(!allValid){
            log.warn("[LinkFlowController] 연동 불가능한 계좌 ID 요청 - userId: {}, requestedIds: {}",
                    userDetails.getUserId(), requestedAccountIds);
            throw AccountErrorCode.INVALID_ACCOUNT_SELECTION.toException();
        }
    }

    private void validateReturnUrl(String returnUrl) {
        if (!allowedReturnUrls.contains(returnUrl)) {
            throw AccountErrorCode.INVALID_RETURN_URL.toException();
        }
    }

    private void validateExpectedIdentity(HttpSession session, CustomUserDetails userDetails) {
        String expectedIdentityHash = (String) session.getAttribute(SESSION_EXPECTED_IDENTITY_HASH);
        if (expectedIdentityHash == null) {
            throw UserErrorCode.INVALID_LINK_STATE.toException();
        }

        UserResponse loggedInUser = userService.getMyInfo(userDetails.getUserId());
        String loggedInIdentityHash = LinkIdentityHasher.hash(
                loggedInUser.getName(),
                loggedInUser.getBirthDate(),
                linkIdentityHashSecret
        );

        if (!expectedIdentityHash.equals(loggedInIdentityHash)) {
            log.warn("[LinkFlowController] 계좌 연동 명의 불일치 - loggedInUserId: {}", userDetails.getUserId());
            throw UserErrorCode.LINK_IDENTITY_MISMATCH.toException();
        }
    }

    public record LinkConfirmRequest(List<Long> accountIds) {}

    public record LinkConfirmResponse(String redirectUrl) {}
}