package org.teamsai.saimockbank.domain.transaction.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.teamsai.saimockbank.domain.transaction.dto.TransactionResponse;
import org.teamsai.saimockbank.domain.transaction.service.BankTransactionService;

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
}
