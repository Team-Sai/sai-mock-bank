package org.teamsai.saimockbank.domain.fds.util;

import org.springframework.stereotype.Component;
import org.teamsai.saimockbank.domain.fds.dto.FdsContext;
import org.teamsai.saimockbank.domain.fds.dto.FdsRuleResult;
import org.teamsai.saimockbank.domain.fds.type.FdsAction;
import org.teamsai.saimockbank.domain.fds.type.FdsRiskLevel;
import org.teamsai.saimockbank.domain.fds.type.FdsRuleType;

import java.util.List;

@Component
public class FdsRiskCalculator {

    private static final int R02_MEDIUM_SCORE = 50;
    private static final int HIGH_RISK_SCORE = 90;

    public int calculateScore(
            List<FdsRuleResult> results,
            FdsContext context
    ) {
        boolean r01Matched =
                isMatched(results, FdsRuleType.R01);

        boolean r02Matched =
                isMatched(results, FdsRuleType.R02);

        boolean r03Matched =
                isMatched(results, FdsRuleType.R03);

        if (r02Matched
                && context.hasMultipleNewRecipients()
                && context.hasFrequencySpike()) {
            return HIGH_RISK_SCORE;
        }

        int p01Score = Math.max(
                r01Matched ? FdsRuleType.R01.getRiskScore() : 0,
                r03Matched ? FdsRuleType.R03.getRiskScore() : 0
        );

        int p02Score =
                r02Matched ? R02_MEDIUM_SCORE : 0;

        return Math.min(59, p01Score + p02Score);
    }

    public int calculateScore(
            List<FdsRuleResult> results
    ) {
        int p01Score = results.stream()
                .filter(FdsRuleResult::matched)
                .filter(result ->
                        result.ruleType() == FdsRuleType.R01
                                || result.ruleType() == FdsRuleType.R03
                )
                .mapToInt(FdsRuleResult::riskScore)
                .max()
                .orElse(0);

        boolean r02Matched =
                isMatched(results, FdsRuleType.R02);

        return Math.min(
                59,
                p01Score + (r02Matched ? R02_MEDIUM_SCORE : 0)
        );
    }

    private boolean isMatched(
            List<FdsRuleResult> results,
            FdsRuleType ruleType
    ) {
        return results.stream()
                .anyMatch(result ->
                        result.matched()
                                && result.ruleType() == ruleType
                );
    }

    public FdsRiskLevel determineRiskLevel(
            int score
    ) {
        return FdsRiskLevel.fromScore(score);
    }

    public FdsAction determineAction(
            FdsRiskLevel riskLevel
    ) {
        return switch (riskLevel) {
            case LOW -> FdsAction.ALLOW;
            case MEDIUM -> FdsAction.MONITOR;
            case HIGH -> FdsAction.HOLD;
        };
    }
}
