
package org.teamsai.saimockbank.domain.fds.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saimockbank.domain.fds.dto.FdsContext;
import org.teamsai.saimockbank.domain.fds.dto.FdsRuleResult;
import org.teamsai.saimockbank.domain.fds.dto.response.FdsEvaluationResponse;
import org.teamsai.saimockbank.domain.fds.entity.FdsEvaluation;
import org.teamsai.saimockbank.domain.fds.exception.FdsErrorCode;
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
        List<FdsRuleResult> results =
                fdsRuleService.evaluate(context);

        int riskScore =
                fdsRiskCalculator.calculateScore(
                        results,
                        context
                );

        FdsRiskLevel riskLevel =
                fdsRiskCalculator.determineRiskLevel(riskScore);

        return persistEvaluation(
                transactionId,
                context,
                results,
                riskScore,
                riskLevel
        );
    }

    @Transactional
    public FdsEvaluationResponse evaluate(
            Long transactionId,
            FdsContext context,
            List<FdsRuleResult> results,
            int riskScore,
            FdsRiskLevel riskLevel
    ) {
        return persistEvaluation(
                transactionId,
                context,
                results,
                riskScore,
                riskLevel
        );
    }

    private FdsEvaluationResponse persistEvaluation(
            Long transactionId,
            FdsContext context,
            List<FdsRuleResult> results,
            int riskScore,
            FdsRiskLevel riskLevel
    ) {
        FdsAction action =
                fdsRiskCalculator.determineAction(riskLevel);

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

        return FdsEvaluationResponse.from(
                evaluationRepository.saveAndFlush(evaluation)
        );
    }

    @Transactional(readOnly = true)
    public Page<FdsEvaluationResponse> getEvaluations(
            int page,
            int size
    ) {
        return evaluationRepository
                .findAllByOrderByEvaluatedAtDesc(
                        PageRequest.of(page, size)
                )
                .map(FdsEvaluationResponse::from);
    }

    @Transactional(readOnly = true)
    public FdsEvaluationResponse getEvaluation(Long id) {
        FdsEvaluation evaluation =
                evaluationRepository.findById(id)
                        .orElseThrow(
                                FdsErrorCode.EVALUATION_NOT_FOUND::toException
                        );

        return FdsEvaluationResponse.from(evaluation);
    }

    @Transactional(readOnly = true)
    public Page<FdsEvaluationResponse> getEvaluationsByTransaction(
            Long transactionId,
            int page,
            int size
    ) {
        return evaluationRepository
                .findByTransactionIdOrderByEvaluatedAtDesc(
                        transactionId,
                        PageRequest.of(page, size)
                )
                .map(FdsEvaluationResponse::from);
    }
}
