package org.teamsai.saimockbank.domain.transfer.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.teamsai.saimockbank.domain.transfer.dto.TransferRequest;
import org.teamsai.saimockbank.domain.transfer.dto.TransferResponse;
import org.teamsai.saimockbank.domain.transfer.service.BankTransferService;
import org.teamsai.saimockbank.global.security.CustomUserDetails;

@Tag(
        name = "이체 관련 API",
        description = "Mock은행 이체 및 이체 내역 조회 API"
)
@RestController
@RequiredArgsConstructor
public class BankTransferController {

    private final BankTransferService bankTransferService;

    @Operation(summary = "계좌 이체")
    @PostMapping("/api/mock-bank/transfers")
    public TransferResponse transfer(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody TransferRequest request
            ){
        return bankTransferService.transfer(userDetails.getUserId(),request);
    }
    @Operation(summary = "이체 내역 단건 조회")
    @GetMapping("/api/mock-bank/transfers/{transferId}")
    public TransferResponse getTransfer(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long transferId
    ){
        return bankTransferService.getTransfer(userDetails.getUserId(),transferId);
    }
}
