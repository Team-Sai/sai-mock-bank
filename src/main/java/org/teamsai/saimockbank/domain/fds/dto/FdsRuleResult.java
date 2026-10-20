package org.teamsai.saimockbank.domain.fds.dto;

import org.teamsai.saimockbank.domain.fds.type.FdsRuleType;

public record FdsRuleResult(
        FdsRuleType ruleType,
        boolean matched,
        int riskScore,
        String reason
) {

    public static FdsRuleResult matched(
            FdsRuleType ruleType,
            String reason
    ) {
        return new FdsRuleResult(
                ruleType,
                true,
                ruleType.getRiskScore(),
                reason
        );
    }

    public static FdsRuleResult notMatched(
            FdsRuleType ruleType
    ) {
        return new FdsRuleResult(
                ruleType,
                false,
                0,
                null
        );
    }
}
