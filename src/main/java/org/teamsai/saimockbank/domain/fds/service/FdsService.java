package org.teamsai.saimockbank.domain.fds.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saimockbank.domain.fds.dto.FdsContext;
import org.teamsai.saimockbank.domain.fds.dto.FdsRuleResult;
import org.teamsai.saimockbank.domain.fds.dto.response.FdsEvaluationResponse;
import org.teamsai.saimockbank.domain.fds.entity.FdsEvaluation;
import org.teamsai.saimockbank.domain.fds.repository.FdsEvaluationRepository;
import org.teamsai.saimockbank.domain.fds.type.FdsAction;
import org.teamsai.saimockbank.domain.fds.type.FdsRiskLevel;
import org.teamsai.saimockbank.domain.fds.util.FdsRiskCalculator;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FdsService {

    private final FdsRuleService fdsRuleService;
    private final FdsRiskCalculator fdsRiskCalculator;
    private final FdsEvaluationRepository evaluationRepository;

    @Transactional
    public FdsEvaluationResponse evaluate(
            Long transactionId,
            FdsContext context
    ) {

        // 1. 개별 규칙 평가
        List<FdsRuleResult> results =
                fdsRuleService.evaluate(context);

        // 2. 거래 맥락을 포함한 최종 위험 점수 계산
        int riskScore =
                fdsRiskCalculator.calculateScore(
                        results,
                        context
                );

        // 3. 위험 등급 산정
        FdsRiskLevel riskLevel =
                fdsRiskCalculator.determineRiskLevel(
                        riskScore
                );

        // 4. 후속 조치 결정
        FdsAction action =
                fdsRiskCalculator.determineAction(
                        riskLevel
                );

        // 5. 탐지된 규칙 추출
        List<FdsRuleResult> matchedResults =
                results.stream()
                        .filter(FdsRuleResult::matched)
                        .toList();

        String matchedRules =
                matchedResults.stream()
                        .map(result -> result.ruleType().name())
                        .collect(Collectors.joining(","));

        String reasons =
                matchedResults.stream()
                        .map(FdsRuleResult::reason)
                        .collect(Collectors.joining("\n"));

        // HIGH 복합 판단 근거 추가
        if (riskLevel == FdsRiskLevel.HIGH) {

            String highReason =
                    "고액 입금 후 자금 분산, "
                            + "30일 미거래 수취계좌 3개 이상, "
                            + "비정상적인 송금 빈도 급증이 "
                            + "동시에 발생했습니다.";

            reasons = reasons.isBlank()
                    ? highReason
                    : reasons + "\n" + highReason;
        }

        // 6. 평가 Entity 생성
        FdsEvaluation evaluation = new FdsEvaluation(
                transactionId,
                context.senderAccountId(),
                context.receiverAccountId(),
                context.amount(),
                riskScore,
                riskLevel,
                action,
                matchedRules,
                reasons,
                context.requestedAt()
        );

        // 7. 평가 결과 저장
        FdsEvaluation saved =
                evaluationRepository.save(evaluation);

        return FdsEvaluationResponse.from(saved);
    }


    @Transactional
    public void linkTransaction(
            Long evaluationId,
            Long transferId
    ) {

        FdsEvaluation evaluation =
                evaluationRepository.findById(evaluationId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "FDS 평가 이력을 찾을 수 없습니다. id="
                                                + evaluationId
                                )
                        );

        evaluation.linkTransaction(transferId);

        evaluationRepository.save(evaluation);
    }


    @Transactional(readOnly = true)
    public List<FdsEvaluationResponse> getEvaluations() {

        return evaluationRepository
                .findAllByOrderByEvaluatedAtDesc()
                .stream()
                .map(FdsEvaluationResponse::from)
                .toList();
    }


    @Transactional(readOnly = true)
    public FdsEvaluationResponse getEvaluation(Long id) {

        FdsEvaluation evaluation =
                evaluationRepository.findById(id)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "FDS 평가 이력을 찾을 수 없습니다. id="
                                                + id
                                )
                        );

        return FdsEvaluationResponse.from(evaluation);
    }


    @Transactional(readOnly = true)
    public List<FdsEvaluationResponse> getEvaluationsByTransaction(
            Long transactionId
    ) {

        return evaluationRepository
                .findByTransactionIdOrderByEvaluatedAtDesc(
                        transactionId
                )
                .stream()
                .map(FdsEvaluationResponse::from)
                .toList();
    }
}