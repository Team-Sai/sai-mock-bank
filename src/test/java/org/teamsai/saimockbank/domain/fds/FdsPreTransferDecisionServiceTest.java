
package org.teamsai.saimockbank.domain.fds;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.teamsai.saimockbank.domain.fds.dto.FdsContext;
import org.teamsai.saimockbank.domain.fds.dto.FdsRuleResult;
import org.teamsai.saimockbank.domain.fds.dto.GeoIpResolution;
import org.teamsai.saimockbank.domain.fds.service.FdsContextService;
import org.teamsai.saimockbank.domain.fds.service.FdsPreTransferDecisionService;
import org.teamsai.saimockbank.domain.fds.service.FdsRuleService;
import org.teamsai.saimockbank.domain.fds.service.GeoIpService;
import org.teamsai.saimockbank.domain.fds.type.FdsRiskLevel;
import org.teamsai.saimockbank.domain.fds.type.FdsRuleType;
import org.teamsai.saimockbank.domain.fds.util.FdsRiskCalculator;
import org.teamsai.saimockbank.global.exception.DomainException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FdsPreTransferDecisionServiceTest {

    @Mock
    private FdsContextService contextService;

    @Mock
    private FdsRuleService ruleService;

    @Mock
    private FdsRiskCalculator calculator;

    @Mock
    private GeoIpService geoIpService;

    @InjectMocks
    private FdsPreTransferDecisionService service;

    private static final Long SENDER = 20L;
    private static final Long RECEIVER = 21L;

    private static final BigDecimal AMOUNT =
            BigDecimal.valueOf(500_000);

    private FdsContext createContext() {
        return new FdsContext(
                SENDER,
                RECEIVER,
                AMOUNT,
                LocalDateTime.of(2026, 10, 21, 14, 0),
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
    }

    @Test
    void 정상_위험도는_관리자_심사가_필요하지_않다() {
        FdsContext context = createContext();

        GeoIpResolution location =
                new GeoIpResolution(
                        "127.0.0.1",
                        "KR",
                        false
                );

        List<FdsRuleResult> results = List.of(
                FdsRuleResult.notMatched(FdsRuleType.R01),
                FdsRuleResult.notMatched(FdsRuleType.R02),
                FdsRuleResult.notMatched(FdsRuleType.R03),
                FdsRuleResult.notMatched(FdsRuleType.R04)
        );

        when(contextService.create(
                eq(SENDER),
                eq(RECEIVER),
                eq(AMOUNT),
                any(LocalDateTime.class)
        )).thenReturn(context);

        when(geoIpService.resolve(null))
                .thenReturn(location);

        when(ruleService.evaluate(context, location))
                .thenReturn(results);

        when(calculator.calculateScore(results, context))
                .thenReturn(0);

        when(calculator.determineRiskLevel(0))
                .thenReturn(FdsRiskLevel.LOW);

        var decision = service.assess(
                SENDER,
                RECEIVER,
                AMOUNT
        );

        assertThat(decision.riskLevel())
                .isEqualTo(FdsRiskLevel.LOW);

        assertThat(decision.score())
                .isZero();

        assertThat(decision.requiresReview())
                .isFalse();

        verify(geoIpService).resolve(null);

        verify(ruleService).evaluate(context, location);
    }

    @Test
    void HIGH_위험도는_관리자_심사가_필요하다() {
        FdsContext context = createContext();

        GeoIpResolution location =
                new GeoIpResolution(
                        "127.0.0.1",
                        "KR",
                        false
                );

        List<FdsRuleResult> results = List.of(
                FdsRuleResult.matched(
                        FdsRuleType.R02,
                        "고액 입금 후 분산 송금"
                ),
                FdsRuleResult.notMatched(FdsRuleType.R04)
        );

        when(contextService.create(
                eq(SENDER),
                eq(RECEIVER),
                eq(AMOUNT),
                any(LocalDateTime.class)
        )).thenReturn(context);

        when(geoIpService.resolve(null))
                .thenReturn(location);

        when(ruleService.evaluate(context, location))
                .thenReturn(results);

        when(calculator.calculateScore(results, context))
                .thenReturn(90);

        when(calculator.determineRiskLevel(90))
                .thenReturn(FdsRiskLevel.HIGH);

        var decision = service.assess(
                SENDER,
                RECEIVER,
                AMOUNT
        );

        assertThat(decision.riskLevel())
                .isEqualTo(FdsRiskLevel.HIGH);

        assertThat(decision.score())
                .isEqualTo(90);

        assertThat(decision.requiresReview())
                .isTrue();

        verify(ruleService).evaluate(context, location);
    }

    @Test
    void 해외_IP는_HIGH_위험도로_관리자_심사가_필요하다() {
        FdsContext context = createContext();

        String clientIp = "203.0.113.10";

        GeoIpResolution location =
                new GeoIpResolution(
                        clientIp,
                        "US",
                        true
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

        when(contextService.create(
                eq(SENDER),
                eq(RECEIVER),
                eq(AMOUNT),
                any(LocalDateTime.class)
        )).thenReturn(context);

        when(geoIpService.resolve(clientIp))
                .thenReturn(location);

        when(ruleService.evaluate(context, location))
                .thenReturn(results);

        when(calculator.calculateScore(results, context))
                .thenReturn(90);

        when(calculator.determineRiskLevel(90))
                .thenReturn(FdsRiskLevel.HIGH);

        var decision = service.assess(
                SENDER,
                RECEIVER,
                AMOUNT,
                clientIp
        );

        assertThat(decision.riskLevel())
                .isEqualTo(FdsRiskLevel.HIGH);

        assertThat(decision.score())
                .isEqualTo(90);

        assertThat(decision.requiresReview())
                .isTrue();

        assertThat(decision.results())
                .anyMatch(result ->
                        result.ruleType() == FdsRuleType.R04
                                && result.matched()
                );

        verify(geoIpService).resolve(clientIp);

        verify(ruleService).evaluate(context, location);
    }

    @Test
    void FDS_장애가_발생하면_평가없이_이체하지_않는다() {
        when(contextService.create(
                eq(SENDER),
                eq(RECEIVER),
                eq(AMOUNT),
                any(LocalDateTime.class)
        )).thenThrow(
                new IllegalStateException("FDS 조회 오류")
        );

        assertThatThrownBy(() ->
                service.assess(
                        SENDER,
                        RECEIVER,
                        AMOUNT
                )
        ).isInstanceOf(DomainException.class);

        verifyNoInteractions(
                geoIpService,
                ruleService,
                calculator
        );
    }
}
