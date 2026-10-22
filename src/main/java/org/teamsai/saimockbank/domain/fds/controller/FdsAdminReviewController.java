package org.teamsai.saimockbank.domain.fds.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.teamsai.saimockbank.domain.fds.dto.request.FdsReviewDecisionRequest;
import org.teamsai.saimockbank.domain.fds.dto.response.FdsReviewResponse;
import org.teamsai.saimockbank.domain.fds.service.FdsAdminReviewService;
import org.teamsai.saimockbank.global.security.CustomUserDetails;

@RestController
@RequestMapping("/api/fds/admin/reviews")
@RequiredArgsConstructor
public class FdsAdminReviewController {

    private final FdsAdminReviewService service;

    @GetMapping
    public Page<FdsReviewResponse> pending(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return service.pending(page, size);
    }

    @GetMapping("/{transferId}")
    public FdsReviewResponse detail(
            @PathVariable Long transferId
    ) {
        return service.detail(transferId);
    }

    @PostMapping("/{transferId}/approve")
    public FdsReviewResponse approve(
            @PathVariable Long transferId,
            @AuthenticationPrincipal CustomUserDetails admin,
            @Valid @RequestBody(required = false)
            FdsReviewDecisionRequest body
    ) {
        return service.approve(
                transferId,
                admin.getUserId(),
                body == null ? null : body.reason()
        );
    }

    @PostMapping("/{transferId}/reject")
    public FdsReviewResponse reject(
            @PathVariable Long transferId,
            @AuthenticationPrincipal CustomUserDetails admin,
            @Valid @RequestBody FdsReviewDecisionRequest body
    ) {
        return service.reject(
                transferId,
                admin.getUserId(),
                body.reason()
        );
    }
}
