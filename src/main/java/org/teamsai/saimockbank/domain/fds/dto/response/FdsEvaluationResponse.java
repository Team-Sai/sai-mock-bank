package org.teamsai.saimockbank.domain.fds.dto.response;

import org.teamsai.saimockbank.domain.fds.entity.FdsEvaluation;
import org.teamsai.saimockbank.domain.fds.type.FdsAction;
import org.teamsai.saimockbank.domain.fds.type.FdsRiskLevel;
import org.teamsai.saimockbank.domain.fds.type.FdsRuleType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

public record FdsEvaluationResponse(
        Long id,
        Long transactionId,
        Long senderAccountId,
        Long receiverAccountId,
        BigDecimal amount,
        int riskScore,
        FdsRiskLevel riskLevel,
        FdsAction action,
        List<FdsRuleType> matchedRules,
        List<String> reasons,
        LocalDateTime evaluatedAt
) {

    public static FdsEvaluationResponse from(
            FdsEvaluation evaluation
    ) {

        List<FdsRuleType> rules =
                evaluation.getMatchedRules() == null
                        || evaluation.getMatchedRules().isBlank()
                        ? List.of()
                        : Arrays.stream(
                                evaluation.getMatchedRules().split(",")
                        )
                        .map(FdsRuleType::valueOf)
                        .toList();

        List<String> reasons =
                evaluation.getReasons() == null
                        || evaluation.getReasons().isBlank()
                        ? List.of()
                        : Arrays.asList(
                        evaluation.getReasons().split("\n")
                );

        return new FdsEvaluationResponse(
                evaluation.getId(),
                evaluation.getTransactionId(),
                evaluation.getSenderAccountId(),
                evaluation.getReceiverAccountId(),
                evaluation.getAmount(),
                evaluation.getRiskScore(),
                evaluation.getRiskLevel(),
                evaluation.getAction(),
                rules,
                reasons,
                evaluation.getEvaluatedAt()
        );
    }
}