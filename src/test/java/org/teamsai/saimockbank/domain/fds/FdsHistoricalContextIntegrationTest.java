package org.teamsai.saimockbank.domain.fds;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.annotation.Rollback;
import org.teamsai.saimockbank.domain.fds.dto.FdsContext;
import org.teamsai.saimockbank.domain.fds.dto.FdsRuleResult;
import org.teamsai.saimockbank.domain.fds.service.FdsContextService;
import org.teamsai.saimockbank.domain.fds.service.FdsRuleService;
import org.teamsai.saimockbank.domain.fds.type.FdsRiskLevel;
import org.teamsai.saimockbank.domain.fds.type.FdsRuleType;
import org.teamsai.saimockbank.domain.fds.util.FdsRiskCalculator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("dev")
@Transactional
@Rollback
class FdsHistoricalContextIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FdsContextService contextService;

    @Autowired
    private FdsRuleService ruleService;

    @Autowired
    private FdsRiskCalculator riskCalculator;

    @Test
    void 기존_76번_거래의_HIGH_판단을_재현한다() {

        Long transferId = 76L;
        Long senderAccountId = 21L;
        Long receiverAccountId = 28L;

        LocalDateTime completedAt =
                jdbcTemplate.queryForObject(
                        """
                        SELECT completed_at
                        FROM bank_transfer
                        WHERE transfer_id = ?
                        """,
                        (rs, rowNum) ->
                                rs.getTimestamp("completed_at")
                                        .toLocalDateTime(),
                        transferId
                );

        Integer updated = jdbcTemplate.update(
                """
                UPDATE bank_transfer
                SET status = 'PENDING'
                WHERE transfer_id = ?
                  AND status = 'SUCCESS'
                """,
                transferId
        );

        assertEquals(1, updated);

        FdsContext context = contextService.create(
                senderAccountId,
                receiverAccountId,
                BigDecimal.valueOf(500_000),
                completedAt
        );

        List<FdsRuleResult> results =
                ruleService.evaluate(context);

        int score = riskCalculator.calculateScore(
                results,
                context
        );

        FdsRiskLevel riskLevel =
                riskCalculator.determineRiskLevel(score);

        System.out.println("========== FDS TEST ==========");

        System.out.println(
                "deposit=" + context.recentLargeDepositAmount()
        );

        System.out.println(
                "outgoing=" + context.outgoingAmountSinceDeposit()
        );

        System.out.println(
                "recipients=" + context.distinctRecipientsSinceDeposit()
        );

        System.out.println(
                "newRecipients=" + context.newRecipientsSinceDeposit()
        );

        System.out.println(
                "recent10Min=" + context.transferCount10Min()
        );

        System.out.println(
                "previous60Min=" + context.previousTransferCount60Min()
        );

        for (FdsRuleResult result : results) {
            System.out.println(
                    result.ruleType()
                            + " matched=" + result.matched()
                            + " score=" + result.riskScore()
            );
        }

        System.out.println("finalScore=" + score);
        System.out.println("riskLevel=" + riskLevel);

        System.out.println("==============================");

        assertEquals(
                0,
                BigDecimal.valueOf(2_000_000).compareTo(
                        context.recentLargeDepositAmount()
                )
        );

        assertEquals(
                0,
                BigDecimal.valueOf(1_850_000).compareTo(
                        context.outgoingAmountSinceDeposit()
                )
        );

        assertEquals(
                4,
                context.distinctRecipientsSinceDeposit()
        );

        assertEquals(
                3,
                context.newRecipientsSinceDeposit()
        );

        assertEquals(
                7,
                context.transferCount10Min()
        );

        assertEquals(
                4,
                context.previousTransferCount60Min()
        );

        assertTrue(
                results.stream().anyMatch(
                        result ->
                                result.ruleType() == FdsRuleType.R02
                                        && result.matched()
                )
        );

        assertEquals(90, score);
        assertEquals(FdsRiskLevel.HIGH, riskLevel);
    }
}
