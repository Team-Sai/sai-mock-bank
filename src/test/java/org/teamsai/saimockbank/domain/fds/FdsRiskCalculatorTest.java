package org.teamsai.saimockbank.domain.fds;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.teamsai.saimockbank.domain.fds.dto.FdsContext;
import org.teamsai.saimockbank.domain.fds.dto.FdsRuleResult;
import org.teamsai.saimockbank.domain.fds.type.FdsAction;
import org.teamsai.saimockbank.domain.fds.type.FdsRiskLevel;
import org.teamsai.saimockbank.domain.fds.type.FdsRuleType;
import org.teamsai.saimockbank.domain.fds.util.FdsRiskCalculator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FdsRiskCalculatorTest {

    private FdsRiskCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new FdsRiskCalculator();
    }

    private FdsContext context(
            int newRecipients,
            int recentCount,
            int previousCount
    ) {
        return new FdsContext(
                1L,
                2L,
                BigDecimal.valueOf(600_000),
                LocalDateTime.of(2026, 10, 20, 14, 0),

                3,
                3,
                1,

                0,
                0,

                BigDecimal.valueOf(2_000_000),
                BigDecimal.valueOf(1_800_000),
                3,

                newRecipients,
                recentCount,
                previousCount
        );
    }

    private List<FdsRuleResult> r02Results() {
        return List.of(
                FdsRuleResult.notMatched(FdsRuleType.R01),
                FdsRuleResult.matched(
                        FdsRuleType.R02,
                        "고액 입금 후 자금 분산"
                ),
                FdsRuleResult.notMatched(FdsRuleType.R03)
        );
    }

    @Test
    @DisplayName("탐지된 규칙이 없으면 LOW")
    void noMatchedRules() {

        List<FdsRuleResult> results = List.of(
                FdsRuleResult.notMatched(FdsRuleType.R01),
                FdsRuleResult.notMatched(FdsRuleType.R02),
                FdsRuleResult.notMatched(FdsRuleType.R03)
        );

        int score = calculator.calculateScore(
                results,
                context(0, 1, 0)
        );

        assertEquals(0, score);
        assertEquals(
                FdsRiskLevel.LOW,
                calculator.determineRiskLevel(score)
        );
    }

    @Test
    @DisplayName("R01 단독 탐지는 MEDIUM 40점")
    void r01Medium() {

        int score = calculator.calculateScore(
                List.of(FdsRuleResult.matched(
                        FdsRuleType.R01,
                        "소액 반복"
                )),
                context(0, 1, 0)
        );

        assertEquals(40, score);
        assertEquals(
                FdsRiskLevel.MEDIUM,
                calculator.determineRiskLevel(score)
        );
    }

    @Test
    @DisplayName("R02 단독 탐지는 MEDIUM 50점")
    void r02Medium() {

        int score = calculator.calculateScore(
                r02Results(),
                context(0, 3, 0)
        );

        assertEquals(50, score);
        assertEquals(
                FdsRiskLevel.MEDIUM,
                calculator.determineRiskLevel(score)
        );
    }

    @Test
    @DisplayName("R03 단독 탐지는 MEDIUM 30점")
    void r03Medium() {

        int score = calculator.calculateScore(
                List.of(FdsRuleResult.matched(
                        FdsRuleType.R03,
                        "동일 계좌 반복"
                )),
                context(0, 1, 0)
        );

        assertEquals(30, score);
        assertEquals(
                FdsRiskLevel.MEDIUM,
                calculator.determineRiskLevel(score)
        );
    }

    @Test
    @DisplayName("R01 R03 동시 탐지는 중복 점수 제외")
    void samePolicy() {

        int score = calculator.calculateScore(
                List.of(
                        FdsRuleResult.matched(
                                FdsRuleType.R01, "R01"
                        ),
                        FdsRuleResult.matched(
                                FdsRuleType.R03, "R03"
                        )
                ),
                context(0, 1, 0)
        );

        assertEquals(40, score);
    }

    @Test
    @DisplayName("R02와 신규 수취계좌 및 빈도 급증이 결합되면 HIGH")
    void highCombinedRisk() {

        int score = calculator.calculateScore(
                r02Results(),
                context(3, 6, 4)
        );

        assertEquals(90, score);
        assertEquals(
                FdsRiskLevel.HIGH,
                calculator.determineRiskLevel(score)
        );

        assertEquals(
                FdsAction.MONITOR,
                calculator.determineAction(FdsRiskLevel.HIGH)
        );
    }

    @Test
    @DisplayName("신규 수취계좌가 2개면 HIGH가 아님")
    void insufficientNewRecipients() {

        int score = calculator.calculateScore(
                r02Results(),
                context(2, 6, 4)
        );

        assertEquals(50, score);
    }

    @Test
    @DisplayName("최근 거래 빈도가 충분하지 않으면 HIGH가 아님")
    void insufficientRecentFrequency() {

        int score = calculator.calculateScore(
                r02Results(),
                context(3, 5, 4)
        );

        assertEquals(50, score);
    }

    @Test
    @DisplayName("직전 거래 이력이 없으면 HIGH가 아님")
    void insufficientHistory() {

        int score = calculator.calculateScore(
                r02Results(),
                context(3, 6, 0)
        );

        assertEquals(50, score);
    }

    @Test
    @DisplayName("과거 거래량이 최근 거래량보다 많으면 HIGH가 아님")
    void noFrequencySpike() {

        int score = calculator.calculateScore(
                r02Results(),
                context(3, 6, 12)
        );

        assertEquals(50, score);
    }

    @Test
    @DisplayName("복합 징후가 있어도 R02 미탐지면 HIGH가 아님")
    void noFundDistribution() {

        int score = calculator.calculateScore(
                List.of(
                        FdsRuleResult.notMatched(FdsRuleType.R02)
                ),
                context(3, 6, 4)
        );

        assertEquals(0, score);
    }
}