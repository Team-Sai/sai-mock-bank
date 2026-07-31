package org.teamsai.saimockbank.domain.test_identity.controller;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.teamsai.saimockbank.domain.account.dto.AccountListResponse;
import org.teamsai.saimockbank.domain.account.service.BankAccountService;
import org.teamsai.saimockbank.domain.test_identity.dto.MockBankLinkRequest;
import org.teamsai.saimockbank.domain.test_identity.dto.MockBankLinkResponse;
import org.teamsai.saimockbank.domain.test_identity.service.BankLinkService;

import java.util.List;

@RestController
@RequestMapping("/api/mock-bank")
@RequiredArgsConstructor
public class BankLinkController {

    private final BankLinkService bankLinkService;
    private final BankAccountService bankAccountService;

    @Operation(summary = "연동키 생성 응답")
    @PostMapping("/link")
    public ResponseEntity<MockBankLinkResponse> link(@RequestBody MockBankLinkRequest request) {
        MockBankLinkResponse response = bankLinkService.issueUserKey(
                request.name(), request.email()
        );
        return ResponseEntity.ok(response);
    }
}
