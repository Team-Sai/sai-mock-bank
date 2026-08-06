package org.teamsai.saimockbank.domain.account.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.teamsai.saimockbank.domain.account.dto.AccountDetailResponse;
import org.teamsai.saimockbank.domain.account.dto.AccountListResponse;
import org.teamsai.saimockbank.domain.account.service.BankAccountService;

import java.util.List;

@Tag(
        name = "계좌 API",
        description = "Mock은행 계좌 조회 API"
)
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/mock-bank/accounts")
public class BankAccountController {

    private final BankAccountService bankAccountService;

    @Operation(summary = "사용자 보유 계좌 목록 조회")
    @GetMapping
    public ResponseEntity<List<AccountListResponse>> getAccounts(
            @RequestHeader("X-User-Key") String userKey
    ) {

        List<AccountListResponse> accounts = bankAccountService.getAccounts(userKey);


        return ResponseEntity.ok(accounts);
    }

    @Operation(summary = "계좌 상세 조회")
    @GetMapping("/{accountId}")
    public ResponseEntity<AccountDetailResponse> getAccount(
            @PathVariable Long accountId,
            @RequestHeader("X-User-Key") String userKey
    ) {

        return ResponseEntity.ok(
                bankAccountService.getAccount(accountId, userKey)
        );
    }

    
}