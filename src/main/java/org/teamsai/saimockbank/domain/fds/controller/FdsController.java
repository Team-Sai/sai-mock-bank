package org.teamsai.saimockbank.domain.fds.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.teamsai.saimockbank.domain.fds.dto.response.FdsEvaluationResponse;
import org.teamsai.saimockbank.domain.fds.service.FdsService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/fds/evaluations")
public class FdsController {

    private final FdsService fdsService;

    /**
     * 전체 FDS 평가 이력 페이징 조회
     */
    @GetMapping
    public ResponseEntity<Page<FdsEvaluationResponse>> getEvaluations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        validatePagination(page, size);

        return ResponseEntity.ok(
                fdsService.getEvaluations(page, size)
        );
    }

    /**
     * 개별 평가 상세 조회
     */
    @GetMapping("/{id}")
    public ResponseEntity<FdsEvaluationResponse> getEvaluation(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(
                fdsService.getEvaluation(id)
        );
    }

    /**
     * 거래 ID별 평가 이력 페이징 조회
     */
    @GetMapping("/transactions/{transactionId}")
    public ResponseEntity<Page<FdsEvaluationResponse>> getByTransaction(
            @PathVariable Long transactionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        validatePagination(page, size);

        return ResponseEntity.ok(
                fdsService.getEvaluationsByTransaction(
                        transactionId,
                        page,
                        size
                )
        );
    }

    private void validatePagination(int page, int size) {

        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException(
                    "페이지는 0 이상, 크기는 1~100이어야 합니다."
            );
        }
    }
}