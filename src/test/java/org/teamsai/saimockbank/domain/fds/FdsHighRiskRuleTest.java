
package org.teamsai.saimockbank.domain.fds;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.teamsai.saimockbank.domain.fds.dto.FdsContext;
import org.teamsai.saimockbank.domain.fds.dto.FdsRuleResult;
import org.teamsai.saimockbank.domain.fds.service.FdsRuleService;
import org.teamsai.saimockbank.domain.fds.type.FdsRiskLevel;
import org.teamsai.saimockbank.domain.fds.type.FdsRuleType;
import org.teamsai.saimockbank.domain.fds.util.FdsRiskCalculator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class FdsHighRiskRuleTest {

    private final FdsRuleService ruleService = new FdsRuleService();
    private final FdsRiskCalculator riskCalculator = new FdsRiskCalculator();

    @Test
    void 고액입금후_분산송금과_빈도급증시_HIGH로_판단한다() {
        FdsContext context = new FdsContext(
                21L,
                31L,
                BigDecimal.valueOf(500_000),
                LocalDateTime.of(2026, 10, 22, 10, 0),
                6,
                4,
                1,
                0,
                0,
                BigDecimal.valueOf(2_000_000),
                BigDecimal.valueOf(1_800_000),
                4,
                3,
                6,
                4
        );

        List<FdsRuleResult> results = ruleService.evaluate(context);

        int score = riskCalculator.calculateScore(results, context);
        FdsRiskLevel riskLevel = riskCalculator.determineRiskLevel(score);

        assertEquals(0,
                BigDecimal.valueOf(2_000_000)
                        .compareTo(context.recentLargeDepositAmount()));

        assertEquals(0,
                BigDecimal.valueOf(1_800_000)
                        .compareTo(context.outgoingAmountSinceDeposit()));

        assertEquals(4, context.distinctRecipientsSinceDeposit());
        assertEquals(3, context.newRecipientsSinceDeposit());
        assertEquals(6, context.transferCount10Min());
        assertEquals(4, context.previousTransferCount60Min());

        assertTrue(results.stream().anyMatch(
                result -> result.ruleType() == FdsRuleType.R02
                        && result.matched()
        ));

        assertEquals(90, score);
        assertEquals(FdsRiskLevel.HIGH, riskLevel);
    }
}
