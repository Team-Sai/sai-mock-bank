package org.teamsai.saimockbank.domain.fds;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.teamsai.saimockbank.domain.fds.dto.FdsContext;
import org.teamsai.saimockbank.domain.fds.dto.FdsRuleResult;
import org.teamsai.saimockbank.domain.fds.service.FdsRuleService;
import org.teamsai.saimockbank.domain.fds.type.FdsRuleType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FdsRuleServiceTest {

    private FdsRuleService ruleService;

    @BeforeEach
    void setUp() {
        ruleService = new FdsRuleService();
    }

    private FdsContext context(
            long amount,
            int transferCount,
            int distinctRecipients,
            int sameRecipientCount,
            int smallTransferCount,
            int smallTransferRecipients,
            long depositAmount,
            long outgoingAmount,
            int recipientsSinceDeposit
    ) {
        return new FdsContext(
                18L,
                19L,
                BigDecimal.valueOf(amount),
                LocalDateTime.of(2026, 10, 19, 16, 30),
                transferCount,
                distinctRecipients,
                sameRecipientCount,
                smallTransferCount,
                smallTransferRecipients,
                BigDecimal.valueOf(depositAmount),
                BigDecimal.valueOf(outgoingAmount),
                recipientsSinceDeposit
        );
    }

    private FdsRuleResult findRule(
            FdsContext context,
            FdsRuleType ruleType
    ) {
        return ruleService.evaluate(context).stream()
                .filter(result -> result.ruleType() == ruleType)
                .findFirst()
                .orElseThrow();
    }

    // ==========================
    // R01: 자동화 소액 송금
    // ==========================

    @Test
    @DisplayName("R01 - 소액 송금 20회, 수취계좌 5개이면 탐지")
    void r01_detect() {

        FdsContext context = context(
                1,
                20, 5, 4,
                20, 5,
                0, 0, 0
        );

        FdsRuleResult result =
                findRule(context, FdsRuleType.R01);

        assertTrue(result.matched());
        assertEquals(40, result.riskScore());
    }

    @Test
    @DisplayName("R01 - 소액 송금 19회이면 미탐지")
    void r01_belowTransferCount() {

        FdsContext context = context(
                1,
                19, 5, 4,
                19, 5,
                0, 0, 0
        );

        FdsRuleResult result =
                findRule(context, FdsRuleType.R01);

        assertFalse(result.matched());
        assertEquals(0, result.riskScore());
    }

    @Test
    @DisplayName("R01 - 소액 송금 20회여도 수취계좌 4개이면 미탐지")
    void r01_belowRecipientCount() {

        FdsContext context = context(
                1,
                20, 4, 5,
                20, 4,
                0, 0, 0
        );

        assertFalse(
                findRule(context, FdsRuleType.R01).matched()
        );
    }

    @Test
    @DisplayName("R01 - 전체 송금이 20회여도 소액 송금이 2회면 미탐지")
    void r01_onlySmallTransfersCounted() {

        FdsContext context = context(
                1,
                20, 5, 4,
                2, 2,
                0, 0, 0
        );

        assertFalse(
                findRule(context, FdsRuleType.R01).matched()
        );
    }

    // ==========================
    // R02: 입금 직후 자금 분산
    // ==========================

    @Test
    @DisplayName("R02 - 200만원 입금 후 180만원 분산 송금 탐지")
    void r02_detect() {

        FdsContext context = context(
                600_000,
                3, 3, 1,
                0, 0,
                2_000_000, 1_800_000, 3
        );

        FdsRuleResult result =
                findRule(context, FdsRuleType.R02);

        assertTrue(result.matched());
        assertEquals(50, result.riskScore());
    }

    @Test
    @DisplayName("R02 - 출금액이 입금액의 80% 미만이면 미탐지")
    void r02_belowOutgoingRatio() {

        FdsContext context = context(
                500_000,
                3, 3, 1,
                0, 0,
                2_000_000, 1_500_000, 3
        );

        assertFalse(
                findRule(context, FdsRuleType.R02).matched()
        );
    }

    @Test
    @DisplayName("R02 - 수취계좌가 2개이면 미탐지")
    void r02_belowRecipientCount() {

        FdsContext context = context(
                900_000,
                3, 2, 2,
                0, 0,
                2_000_000, 1_800_000, 2
        );

        assertFalse(
                findRule(context, FdsRuleType.R02).matched()
        );
    }

    @Test
    @DisplayName("R02 - 출금 비율이 정확히 80%이면 탐지")
    void r02_boundary() {

        FdsContext context = context(
                600_000,
                3, 3, 1,
                0, 0,
                2_000_000, 1_600_000, 3
        );

        assertTrue(
                findRule(context, FdsRuleType.R02).matched()
        );
    }

    @Test
    @DisplayName("R02 - 입금액이 100만원 미만이면 미탐지")
    void r02_belowDepositAmount() {

        FdsContext context = context(
                300_000,
                3, 3, 1,
                0, 0,
                900_000, 850_000, 3
        );

        assertFalse(
                findRule(context, FdsRuleType.R02).matched()
        );
    }

    // ==========================
    // R03: 동일 계좌 반복 송금
    // ==========================

    @Test
    @DisplayName("R03 - 동일 계좌 15회 송금 탐지")
    void r03_detect() {

        FdsContext context = context(
                1,
                15, 1, 15,
                15, 1,
                0, 0, 0
        );

        assertTrue(
                findRule(context, FdsRuleType.R03).matched()
        );
    }

    @Test
    @DisplayName("R03 - 동일 계좌 14회 송금 미탐지")
    void r03_boundary() {

        FdsContext context = context(
                1,
                14, 1, 14,
                14, 1,
                0, 0, 0
        );

        assertFalse(
                findRule(context, FdsRuleType.R03).matched()
        );
    }

    // ==========================
    // 정상 거래
    // ==========================

    @Test
    @DisplayName("정상적인 25000원 송금은 탐지되지 않음")
    void normalTransfer() {

        FdsContext context = context(
                25_000,
                1, 1, 1,
                0, 0,
                0, 0, 0
        );

        List<FdsRuleResult> results =
                ruleService.evaluate(context);

        assertEquals(3, results.size());

        assertTrue(
                results.stream()
                        .noneMatch(FdsRuleResult::matched)
        );
    }
}