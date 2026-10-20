package org.teamsai.saimockbank.domain.fds.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.teamsai.saimockbank.domain.fds.dto.response.FdsEvaluationResponse;
import org.teamsai.saimockbank.domain.fds.service.FdsService;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/fds/evaluations")
public class FdsController {

    private final FdsService fdsService;

    @GetMapping
    public ResponseEntity<List<FdsEvaluationResponse>> getEvaluations() {

        return ResponseEntity.ok(
                fdsService.getEvaluations()
        );
    }


    @GetMapping("/{id}")
    public ResponseEntity<FdsEvaluationResponse> getEvaluation(
            @PathVariable Long id
    ) {

        return ResponseEntity.ok(
                fdsService.getEvaluation(id)
        );
    }

    @GetMapping("/transactions/{transactionId}")
    public ResponseEntity<List<FdsEvaluationResponse>> getByTransaction(
            @PathVariable Long transactionId
    ) {

        return ResponseEntity.ok(
                fdsService.getEvaluationsByTransaction(transactionId)
        );
    }
}