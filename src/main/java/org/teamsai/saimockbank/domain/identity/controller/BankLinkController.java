package org.teamsai.saimockbank.domain.identity.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.teamsai.saimockbank.domain.account.service.BankAccountService;
import org.teamsai.saimockbank.domain.identity.dto.MockBankLinkRequest;
import org.teamsai.saimockbank.domain.identity.dto.MockBankLinkResponse;
import org.teamsai.saimockbank.domain.identity.service.BankLinkService;

@Tag(
        name = "사이원장 연동 API",
        description = "사용자 연동키 생성 API"
)
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
