package org.teamsai.saimockbank.domain.transfer.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.teamsai.saimockbank.domain.transfer.dto.TransferRequest;
import org.teamsai.saimockbank.domain.transfer.dto.TransferResponse;
import org.teamsai.saimockbank.domain.transfer.service.BankTransferService;
@Tag(
        name = "이체 관련 API",
        description = "Mock은행 이체 및 이체 내역 조회 API"
)
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/mock-bank/transfers")
public class BankTransferController {

    private final BankTransferService bankTransferService;
    @Operation(summary = "계좌 이체")
    @PostMapping
    public TransferResponse transfer(
            @RequestBody TransferRequest request
            ){
        return bankTransferService.transfer(request);
    }
    @Operation(summary = "이체 내역 단건 조회")
    @GetMapping("/{transferId}")
    public TransferResponse getTransfer(
            @PathVariable Long transferId
    ){
        return bankTransferService.getTransfer(transferId);
    }
}
