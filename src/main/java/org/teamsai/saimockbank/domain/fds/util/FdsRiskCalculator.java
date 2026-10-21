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

        // HIGH: 서로 다른 위험 징후의 조합
        if (r02Matched
                && context.hasMultipleNewRecipients()
                && context.hasFrequencySpike()) {

            return HIGH_RISK_SCORE;
        }

        // P01: R01과 R03은 같은 정책이므로 최고 점수
        int p01Score = 0;

        if (r01Matched) {
            p01Score = FdsRuleType.R01.getRiskScore();
        }

        if (r03Matched) {
            p01Score = Math.max(
                    p01Score,
                    FdsRuleType.R03.getRiskScore()
            );
        }

        // P02: R02 단독은 MEDIUM
        int p02Score =
                r02Matched ? R02_MEDIUM_SCORE : 0;

        /*
         * 서로 다른 정책이 동시에 탐지되어도
         * 복합 HIGH 조건을 만족하지 않으면
         * MEDIUM 범위 안에서 관리한다.
         */
        return Math.min(59, p01Score + p02Score);
    }

    public int calculateScore(List<FdsRuleResult> results) {

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

    public FdsRiskLevel determineRiskLevel(int score) {
        return FdsRiskLevel.fromScore(score);
    }


    public FdsAction determineAction(
            FdsRiskLevel riskLevel
    ) {

        return switch (riskLevel) {
            case LOW -> FdsAction.ALLOW;
            case MEDIUM, HIGH -> FdsAction.MONITOR;
        };
    }
}