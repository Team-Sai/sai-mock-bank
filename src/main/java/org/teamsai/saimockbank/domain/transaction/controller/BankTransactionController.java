package org.teamsai.saimockbank.domain.transaction.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.teamsai.saimockbank.domain.transaction.dto.TransactionResponse;
import org.teamsai.saimockbank.domain.transaction.service.BankTransactionService;
import org.teamsai.saimockbank.global.security.CustomUserDetails;

import java.util.List;

@Tag(
        name = "거래내역 API",
        description = "Mock은행 계좌 거래내역 조회 API"
)
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/mock-bank/accounts")
public class BankTransactionController {

    private final BankTransactionService bankTransactionService;

    @Operation(summary = "계좌 거래내역 조회")
    @GetMapping("/{accountId}/transactions")
    public List<TransactionResponse> getTransactions(
            @PathVariable Long accountId,
            @RequestHeader("X-User-Key") String userKey,
            @RequestParam(required = false, defaultValue = "0") Long afterTransactionId
    ) {
        return bankTransactionService.getTransactions(accountId, userKey, afterTransactionId);
    }

    @Operation(summary = "내 계좌 거래내역 조회 (로그인 기반)")
    @GetMapping("/my/{accountId}/transactions")
    public List<TransactionResponse> getMyTransactions(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long accountId,
            @RequestParam(required = false, defaultValue = "0") Long afterTransactionId
    ) {
        return bankTransactionService.getMyTransactions(
                userDetails.getUserId(), accountId, afterTransactionId
        );
    }
}
