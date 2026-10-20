
package org.teamsai.saimockbank.domain.fds;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.teamsai.saimockbank.domain.fds.dto.FdsContext;
import org.teamsai.saimockbank.domain.fds.dto.FdsRuleResult;
import org.teamsai.saimockbank.domain.fds.entity.FdsTransferRead;
import org.teamsai.saimockbank.domain.fds.repository.FdsTransferRepository;
import org.teamsai.saimockbank.domain.fds.service.FdsContextService;
import org.teamsai.saimockbank.domain.fds.service.FdsRuleService;
import org.teamsai.saimockbank.domain.fds.type.FdsRuleType;
import org.teamsai.saimockbank.domain.fds.type.FdsRiskLevel;
import org.teamsai.saimockbank.domain.fds.util.FdsRiskCalculator;
import org.teamsai.saimockbank.domain.transfer.dto.TransferStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FdsContextServiceTest {

    @Mock
    private FdsTransferRepository transferRepository;

    private FdsContextService contextService;
    private FdsRuleService ruleService;
    private FdsRiskCalculator riskCalculator;

    private static final Long SENDER = 18L;
    private static final Long RECEIVER = 19L;

    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 10, 20, 14, 0);

    @BeforeEach
    void setUp() {
        contextService = new FdsContextService(transferRepository);
        ruleService = new FdsRuleService();
        riskCalculator = new FdsRiskCalculator();
    }

    // ==========================================
    // 테스트 데이터 생성
    // ==========================================

    private FdsTransferRead transfer(
            Long receiverId,
            long amount
    ) {
        FdsTransferRead transfer =
                mock(FdsTransferRead.class);

        lenient().when(transfer.getToAccountId())
                .thenReturn(receiverId);

        lenient().when(transfer.getAmount())
                .thenReturn(BigDecimal.valueOf(amount));

        return transfer;
    }

    private void mockRecentTransfers(
            List<FdsTransferRead> transfers
    ) {
        when(transferRepository
                .findByFromAccountIdAndStatusAndCompletedAtBetween(
                        eq(SENDER),
                        eq(TransferStatus.SUCCESS),
                        eq(NOW.minusMinutes(5)),
                        eq(NOW)
                ))
                .thenReturn(transfers);
    }

    private void mockLargeDeposit(
            long amount,
            LocalDateTime depositAt,
            List<FdsTransferRead> outgoingTransfers,
            List<Long> previousRecipients
    ) {
        FdsTransferRead deposit =
                mock(FdsTransferRead.class);

        when(deposit.getAmount())
                .thenReturn(BigDecimal.valueOf(amount));

        when(deposit.getCompletedAt())
                .thenReturn(depositAt);

        when(transferRepository
                .findFirstByToAccountIdAndStatusAndAmountGreaterThanEqualAndCompletedAtBetweenOrderByCompletedAtDesc(
                        eq(SENDER),
                        eq(TransferStatus.SUCCESS),
                        eq(BigDecimal.valueOf(1_000_000)),
                        eq(NOW.minusMinutes(10)),
                        eq(NOW)
                ))
                .thenReturn(Optional.of(deposit));

        when(transferRepository
                .findByFromAccountIdAndStatusAndCompletedAtBetweenOrderByCompletedAtAsc(
                        eq(SENDER),
                        eq(TransferStatus.SUCCESS),
                        eq(depositAt),
                        eq(NOW)
                ))
                .thenReturn(outgoingTransfers);

        when(transferRepository.findPreviousRecipientIds(
                eq(SENDER),
                eq(TransferStatus.SUCCESS),
                eq(depositAt.minusDays(30)),
                eq(depositAt)
        )).thenReturn(previousRecipients);
    }

    private void mockFrequency(
            long recent10Min,
            long previous60Min
    ) {
        when(transferRepository
                .countByFromAccountIdAndStatusAndCompletedAtGreaterThanEqualAndCompletedAtLessThan(
                        eq(SENDER),
                        eq(TransferStatus.SUCCESS),
                        eq(NOW.minusMinutes(10)),
                        eq(NOW)
                ))
                .thenReturn(recent10Min);

        when(transferRepository
                .countByFromAccountIdAndStatusAndCompletedAtGreaterThanEqualAndCompletedAtLessThan(
                        eq(SENDER),
                        eq(TransferStatus.SUCCESS),
                        eq(NOW.minusMinutes(70)),
                        eq(NOW.minusMinutes(10))
                ))
                .thenReturn(previous60Min);
    }

    private FdsContext createContext(long amount) {
        return contextService.create(
                SENDER,
                RECEIVER,
                BigDecimal.valueOf(amount),
                NOW
        );
    }

    private int calculateRiskScore(FdsContext context) {
        List<FdsRuleResult> results =
                ruleService.evaluate(context);

        return riskCalculator.calculateScore(results, context);
    }

    // ==========================================
    // R01: 소액 반복 송금
    // ==========================================

    @Test
    @DisplayName("R01 - 소액 송금 19건과 현재 요청을 합산")
    void smallTransferCountIncludesCurrentRequest() {

        List<FdsTransferRead> transfers =
                new ArrayList<>();

        for (int i = 0; i < 19; i++) {
            transfers.add(
                    transfer(20L + (i % 5), 1)
            );
        }

        mockRecentTransfers(transfers);

        FdsContext context = createContext(1);

        assertEquals(20, context.transferCount5Min());
        assertEquals(20, context.smallTransferCount5Min());
        assertEquals(6, context.smallTransferRecipients5Min());

        List<FdsRuleResult> results =
                ruleService.evaluate(context);

        assertTrue(results.stream().anyMatch(
                result -> result.ruleType() == FdsRuleType.R01
                        && result.matched()
        ));
    }

    @Test
    @DisplayName("R01 - 고액 거래는 소액 송금 횟수에서 제외")
    void largeTransfersExcludedFromSmallCount() {

        List<FdsTransferRead> transfers = List.of(
                transfer(20L, 100_000),
                transfer(21L, 200_000),
                transfer(22L, 1),
                transfer(23L, 500)
        );

        mockRecentTransfers(transfers);

        FdsContext context = createContext(1);

        assertEquals(5, context.transferCount5Min());
        assertEquals(3, context.smallTransferCount5Min());
        assertEquals(3, context.smallTransferRecipients5Min());

        assertEquals(0, calculateRiskScore(context));
    }

    // ==========================================
    // R02: 고액 입금 이후 자금 분산
    // ==========================================

    @Test
    @DisplayName("R02 - 고액 입금 후 3개 계좌로 90% 송금")
    void largeDepositDistribution() {

        FdsTransferRead first =
                transfer(20L, 600_000);

        FdsTransferRead second =
                transfer(21L, 600_000);

        mockRecentTransfers(List.of(first, second));

        mockLargeDeposit(
                2_000_000,
                NOW.minusMinutes(5),
                List.of(first, second),
                List.of()
        );

        FdsContext context = createContext(600_000);

        assertEquals(
                0,
                BigDecimal.valueOf(2_000_000).compareTo(
                        context.recentLargeDepositAmount()
                )
        );

        assertEquals(
                0,
                BigDecimal.valueOf(1_800_000).compareTo(
                        context.outgoingAmountSinceDeposit()
                )
        );

        assertEquals(3, context.distinctRecipientsSinceDeposit());

        // R02 단독 탐지: MEDIUM 50점
        assertEquals(50, calculateRiskScore(context));
    }

    @Test
    @DisplayName("R02 - 고액 입금이 없으면 분산 송금 집계 제외")
    void noLargeDeposit() {

        mockRecentTransfers(List.of());

        FdsContext context = createContext(25_000);

        assertEquals(
                0,
                context.recentLargeDepositAmount()
                        .compareTo(BigDecimal.ZERO)
        );

        assertEquals(
                0,
                context.outgoingAmountSinceDeposit()
                        .compareTo(BigDecimal.ZERO)
        );

        assertEquals(0, context.distinctRecipientsSinceDeposit());
        assertEquals(0, context.newRecipientsSinceDeposit());

        assertEquals(0, calculateRiskScore(context));
    }

    @Test
    @DisplayName("R02 - 동일 수취계좌 중복 송금은 한 계좌로 집계")
    void distributionRecipientDeduplication() {

        FdsTransferRead first =
                transfer(RECEIVER, 500_000);

        FdsTransferRead second =
                transfer(RECEIVER, 500_000);

        FdsTransferRead third =
                transfer(20L, 500_000);

        mockRecentTransfers(List.of());

        mockLargeDeposit(
                2_000_000,
                NOW.minusMinutes(3),
                List.of(first, second, third),
                List.of()
        );

        FdsContext context = createContext(100_000);

        assertEquals(2, context.distinctRecipientsSinceDeposit());
        assertEquals(2, context.newRecipientsSinceDeposit());

        assertEquals(
                0,
                BigDecimal.valueOf(1_600_000).compareTo(
                        context.outgoingAmountSinceDeposit()
                )
        );
    }

    // ==========================================
    // R03: 동일 계좌 반복 송금
    // ==========================================

    @Test
    @DisplayName("R03 - 동일 계좌 14건과 현재 요청을 합산")
    void sameRecipientCountIncludesCurrentRequest() {

        List<FdsTransferRead> transfers =
                new ArrayList<>();

        for (int i = 0; i < 14; i++) {
            transfers.add(transfer(RECEIVER, 1));
        }

        mockRecentTransfers(transfers);

        FdsContext context = createContext(1);

        assertEquals(
                15,
                context.sameRecipientTransferCount5Min()
        );

        assertEquals(1, context.distinctRecipients5Min());

        assertEquals(30, calculateRiskScore(context));
    }

    // ==========================================
    // HIGH: 신규 수취계좌 분석
    // ==========================================

    @Test
    @DisplayName("HIGH - 과거 거래 이력이 없는 수취계좌 3개 탐지")
    void detectsThreeNewRecipients() {

        FdsTransferRead first =
                transfer(20L, 600_000);

        FdsTransferRead second =
                transfer(21L, 600_000);

        mockRecentTransfers(List.of(first, second));

        mockLargeDeposit(
                2_000_000,
                NOW.minusMinutes(5),
                List.of(first, second),
                List.of(22L, 23L)
        );

        FdsContext context = createContext(600_000);

        // 20, 21, 19는 과거 미거래 계좌
        assertEquals(3, context.newRecipientsSinceDeposit());
        assertTrue(context.hasMultipleNewRecipients());

        // 빈도 급증 조건은 미충족
        assertEquals(50, calculateRiskScore(context));
    }

    @Test
    @DisplayName("HIGH - 기존 거래 계좌는 신규 수취계좌에서 제외")
    void excludesPreviousRecipients() {

        FdsTransferRead first =
                transfer(20L, 600_000);

        FdsTransferRead second =
                transfer(21L, 600_000);

        mockRecentTransfers(List.of(first, second));

        mockLargeDeposit(
                2_000_000,
                NOW.minusMinutes(5),
                List.of(first, second),
                List.of(20L, 21L)
        );

        FdsContext context = createContext(600_000);

        // 현재 수취인 19만 신규
        assertEquals(1, context.newRecipientsSinceDeposit());
        assertFalse(context.hasMultipleNewRecipients());

        assertEquals(50, calculateRiskScore(context));
    }

    // ==========================================
    // HIGH: 거래 빈도 변화
    // ==========================================

    @Test
    @DisplayName("HIGH - 최근 10분과 직전 60분 송금 횟수 비교")
    void calculatesTransactionFrequency() {

        mockRecentTransfers(List.of());

        // 이전 10분 성공 거래 5회 + 현재 요청 1회
        mockFrequency(5, 4);

        FdsContext context = createContext(25_000);

        assertEquals(6, context.transferCount10Min());
        assertEquals(4, context.previousTransferCount60Min());

        assertTrue(context.hasFrequencySpike());
    }

    @Test
    @DisplayName("HIGH - 거래 빈도가 증가하지 않으면 급증으로 판단하지 않음")
    void noFrequencySpike() {

        mockRecentTransfers(List.of());

        mockFrequency(5, 12);

        FdsContext context = createContext(25_000);

        assertEquals(6, context.transferCount10Min());
        assertEquals(12, context.previousTransferCount60Min());

        assertFalse(context.hasFrequencySpike());
    }

    @Test
    @DisplayName("HIGH - 과거 거래 이력이 부족하면 급증으로 판단하지 않음")
    void insufficientHistory() {

        mockRecentTransfers(List.of());

        mockFrequency(5, 0);

        FdsContext context = createContext(25_000);

        assertEquals(6, context.transferCount10Min());
        assertEquals(0, context.previousTransferCount60Min());

        assertFalse(context.hasFrequencySpike());
    }

    // ==========================================
    // HIGH: 복합 위험 조건 통합 검증
    // ==========================================

    @Test
    @DisplayName("HIGH - 자금 분산, 신규 수취계좌, 빈도 급증 시 90점")
    void combinedHighRiskDetection() {

        FdsTransferRead first =
                transfer(20L, 200_000);

        FdsTransferRead second =
                transfer(21L, 200_000);

        FdsTransferRead third =
                transfer(22L, 200_000);

        FdsTransferRead fourth =
                transfer(23L, 200_000);

        FdsTransferRead fifth =
                transfer(24L, 200_000);

        mockRecentTransfers(List.of(
                first, second, third, fourth, fifth
        ));

        mockLargeDeposit(
                2_000_000,
                NOW.minusMinutes(5),
                List.of(first, second, third, fourth, fifth),
                List.of(20L, 21L, 22L)
        );

        // 이전 10분 5회 + 현재 1회 = 6회
        // 직전 60분 4회
        mockFrequency(5, 4);

        // 기존 100만원 + 현재 80만원 = 180만원
        FdsContext context = createContext(800_000);

        assertEquals(6, context.transferCount10Min());
        assertEquals(4, context.previousTransferCount60Min());

        assertEquals(3, context.newRecipientsSinceDeposit());

        assertEquals(
                0,
                BigDecimal.valueOf(1_800_000).compareTo(
                        context.outgoingAmountSinceDeposit()
                )
        );

        assertTrue(context.hasMultipleNewRecipients());
        assertTrue(context.hasFrequencySpike());

        int score = calculateRiskScore(context);

        assertEquals(90, score);
        assertEquals(
                FdsRiskLevel.HIGH,
                riskCalculator.determineRiskLevel(score)
        );
    }

    // ==========================================
    // 정상 거래
    // ==========================================

    @Test
    @DisplayName("정상 거래 - 과거 이력 없이 현재 요청만 존재")
    void normalTransfer() {

        mockRecentTransfers(List.of());

        FdsContext context = createContext(25_000);

        assertEquals(1, context.transferCount5Min());
        assertEquals(1, context.distinctRecipients5Min());
        assertEquals(1, context.sameRecipientTransferCount5Min());

        assertEquals(0, context.smallTransferCount5Min());
        assertEquals(0, context.smallTransferRecipients5Min());

        assertEquals(0, context.newRecipientsSinceDeposit());
        assertEquals(1, context.transferCount10Min());

        assertFalse(context.hasFrequencySpike());

        assertEquals(0, calculateRiskScore(context));
    }
}
