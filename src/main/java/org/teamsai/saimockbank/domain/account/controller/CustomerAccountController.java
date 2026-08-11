package org.teamsai.saimockbank.domain.account.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.teamsai.saimockbank.domain.account.dto.AccountLookupResponse;
import org.teamsai.saimockbank.domain.account.dto.BankAccountDTO;
import org.teamsai.saimockbank.domain.account.mapper.BankAccountMapper;
import org.teamsai.saimockbank.domain.transfer.exception.TransferErrorCode;
import org.teamsai.saimockbank.global.security.CustomUserDetails;

@Tag(
        name = "내 계좌 API",
        description = "로그인 고객이 mock-bank 웹에서 직접 호출하는 계좌 관련 API"
)
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/mock-bank/customer-accounts")
public class CustomerAccountController {

    private final BankAccountMapper bankAccountMapper;

    @Operation(summary = "계좌번호로 예금주명/은행명 조회 (이체 화면 받는 분 확인용)")
    @GetMapping("/lookup")
    public AccountLookupResponse lookup(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam String accountNumber
    ) {
        BankAccountDTO account = bankAccountMapper.findByAccountNumber(accountNumber)
                .orElseThrow(TransferErrorCode.ACCOUNT_NOT_FOUND::toException);

        return AccountLookupResponse.from(account);
    }
}