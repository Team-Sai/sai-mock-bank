package org.teamsai.saimockbank.domain.fds;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.teamsai.saimockbank.domain.fds.dto.FdsContext;
import org.teamsai.saimockbank.domain.fds.dto.FdsRuleResult;
import org.teamsai.saimockbank.domain.fds.dto.response.FdsEvaluationResponse;
import org.teamsai.saimockbank.domain.fds.entity.FdsEvaluation;
import org.teamsai.saimockbank.domain.fds.repository.FdsEvaluationRepository;
import org.teamsai.saimockbank.domain.fds.service.FdsRuleService;
import org.teamsai.saimockbank.domain.fds.service.FdsService;
import org.teamsai.saimockbank.domain.fds.type.FdsAction;
import org.teamsai.saimockbank.domain.fds.type.FdsRiskLevel;
import org.teamsai.saimockbank.domain.fds.type.FdsRuleType;
import org.teamsai.saimockbank.domain.fds.util.FdsRiskCalculator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FdsServiceTest {

    @Mock
    private FdsEvaluationRepository evaluationRepository;

    private FdsService fdsService;

    @BeforeEach
    void setUp() {
        fdsService = new FdsService(
                new FdsRuleService(),
                new FdsRiskCalculator(),
                evaluationRepository
        );
    }

    private FdsContext createContext(int transferCount) {
        return new FdsContext(
                1L, 2L, BigDecimal.ONE,
                LocalDateTime.of(2026, 10, 19, 15, 0),
                transferCount, 5, 1,
                transferCount, 5,
                BigDecimal.ZERO, BigDecimal.ZERO, 0
        );
    }

    @Test
    @DisplayName("이상거래 탐지 결과를 DB에 저장")
    void evaluateAndSave() {
        FdsContext context = createContext(20);

        when(evaluationRepository.saveAndFlush(any(FdsEvaluation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        FdsEvaluationResponse response = fdsService.evaluate(100L, context);

        assertEquals(100L, response.transactionId());
        assertEquals(40, response.riskScore());
        assertEquals(FdsRiskLevel.MEDIUM, response.riskLevel());
        assertEquals(FdsAction.MONITOR, response.action());
        assertTrue(response.matchedRules().contains(FdsRuleType.R01));

        verify(evaluationRepository, times(1))
                .saveAndFlush(any(FdsEvaluation.class));
    }

    @Test
    @DisplayName("정상 거래도 평가 이력을 저장")
    void evaluateNormalTransfer() {
        FdsContext context = new FdsContext(
                1L, 2L, BigDecimal.valueOf(25_000),
                LocalDateTime.of(2026, 10, 19, 15, 0),
                1, 1, 1,
                0, 0,
                BigDecimal.ZERO, BigDecimal.ZERO, 0
        );

        when(evaluationRepository.saveAndFlush(any(FdsEvaluation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        FdsEvaluationResponse response = fdsService.evaluate(101L, context);

        assertEquals(0, response.riskScore());
        assertEquals(FdsRiskLevel.LOW, response.riskLevel());
        assertEquals(FdsAction.ALLOW, response.action());
        assertTrue(response.matchedRules().isEmpty());

        verify(evaluationRepository, times(1))
                .saveAndFlush(any(FdsEvaluation.class));
    }

    @Test
    void 해외_IP로_HIGH가_된_거래에는_분산송금_복합사유가_붙지_않는다() {
        FdsContext context = new FdsContext(
                1L,
                2L,
                BigDecimal.valueOf(10_000),
                LocalDateTime.of(2026, 10, 26, 14, 0),
                0, 0, 0,
                0, 0,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0,
                0, 0, 0
        );

        List<FdsRuleResult> results = List.of(
                FdsRuleResult.notMatched(FdsRuleType.R01),
                FdsRuleResult.notMatched(FdsRuleType.R02),
                FdsRuleResult.notMatched(FdsRuleType.R03),
                FdsRuleResult.matched(
                        FdsRuleType.R04,
                        "해외 IP 국가(US)에서 송금을 요청했습니다."
                )
        );

        when(evaluationRepository.saveAndFlush(
                any(FdsEvaluation.class)
        )).thenAnswer(invocation -> invocation.getArgument(0));

        FdsEvaluationResponse response = fdsService.evaluate(
                200L,
                context,
                results,
                90,
                FdsRiskLevel.HIGH
        );

        assertEquals(90, response.riskScore());
        assertEquals(FdsRiskLevel.HIGH, response.riskLevel());
        assertEquals(FdsAction.HOLD, response.action());

        assertTrue(response.matchedRules().contains(FdsRuleType.R04));

        assertTrue(response.reasons().stream()
                .anyMatch(reason -> reason.contains("해외 IP")));

        assertTrue(response.reasons().stream()
                .noneMatch(reason -> reason.contains("30일 미거래 수취계좌")));

        verify(evaluationRepository).saveAndFlush(
                any(FdsEvaluation.class)
        );
    }
}
