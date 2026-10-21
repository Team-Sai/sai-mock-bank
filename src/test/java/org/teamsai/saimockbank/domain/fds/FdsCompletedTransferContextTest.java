package org.teamsai.saimockbank.domain.fds;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.teamsai.saimockbank.domain.fds.dto.FdsContext;
import org.teamsai.saimockbank.domain.fds.entity.FdsTransferRead;
import org.teamsai.saimockbank.domain.fds.repository.FdsTransferRepository;
import org.teamsai.saimockbank.domain.fds.service.FdsContextService;
import org.teamsai.saimockbank.domain.fds.service.FdsRuleService;
import org.teamsai.saimockbank.domain.fds.type.FdsRiskLevel;
import org.teamsai.saimockbank.domain.fds.util.FdsRiskCalculator;
import org.teamsai.saimockbank.domain.transfer.dto.TransferStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FdsCompletedTransferContextTest {

    @Mock
    private FdsTransferRepository transferRepository;

    private FdsContextService contextService;
    private FdsRuleService ruleService;
    private FdsRiskCalculator riskCalculator;

    private static final Long TRANSFER_ID = 100L;
    private static final Long SENDER = 21L;
    private static final Long RECEIVER = 24L;

    private static final LocalDateTime COMPLETED_AT =
            LocalDateTime.of(2026, 10, 21, 14, 0);

    private static final BigDecimal SMALL_LIMIT =
            BigDecimal.valueOf(1_000);

    @BeforeEach
    void setUp() {
        contextService =
                new FdsContextService(transferRepository);

        ruleService = new FdsRuleService();
        riskCalculator = new FdsRiskCalculator();
    }

    private FdsTransferRepository.FiveMinuteStats stats(
            long transferCount,
            long distinctRecipients,
            long sameRecipientCount,
            long smallTransferCount,
            long smallRecipientCount
    ) {
        FdsTransferRepository.FiveMinuteStats stats =
                mock(FdsTransferRepository.FiveMinuteStats.class);

        when(stats.getTransferCount())
                .thenReturn(transferCount);

        when(stats.getDistinctRecipients())
                .thenReturn(distinctRecipients);

        when(stats.getSameRecipientCount())
                .thenReturn(sameRecipientCount);

        when(stats.getSmallTransferCount())
                .thenReturn(smallTransferCount);

        when(stats.getSmallRecipientCount())
                .thenReturn(smallRecipientCount);

        return stats;
    }

    private void mockFiveMinutes(
            FdsTransferRepository.FiveMinuteStats stats
    ) {
        when(transferRepository.aggregateFiveMinutes(
                eq(TRANSFER_ID),
                eq(SENDER),
                eq(RECEIVER),
                eq(TransferStatus.SUCCESS),
                eq(SMALL_LIMIT),
                eq(COMPLETED_AT.minusMinutes(5)),
                eq(COMPLETED_AT)
        )).thenReturn(stats);
    }

    private FdsContext createContext(long amount) {
        return contextService.createForCompletedTransfer(
                TRANSFER_ID,
                SENDER,
                RECEIVER,
                BigDecimal.valueOf(amount),
                COMPLETED_AT
        );
    }

    @Test
    void 현재_거래는_한_번만_집계한다() {
        mockFiveMinutes(
                stats(0, 0, 0, 0, 0)
        );

        when(transferRepository.findLargeDeposits(
                eq(SENDER),
                eq(TransferStatus.SUCCESS),
                eq(BigDecimal.valueOf(1_000_000)),
                eq(COMPLETED_AT.minusMinutes(10)),
                eq(COMPLETED_AT),
                eq(PageRequest.of(0, 1))
        )).thenReturn(List.of());

        FdsContext context = createContext(1);

        assertEquals(1, context.transferCount5Min());
        assertEquals(1, context.distinctRecipients5Min());
        assertEquals(1, context.sameRecipientTransferCount5Min());
        assertEquals(1, context.smallTransferCount5Min());
        assertEquals(1, context.smallTransferRecipients5Min());
        assertEquals(1, context.transferCount10Min());

        verify(transferRepository).aggregateFiveMinutes(
                TRANSFER_ID,
                SENDER,
                RECEIVER,
                TransferStatus.SUCCESS,
                SMALL_LIMIT,
                COMPLETED_AT.minusMinutes(5),
                COMPLETED_AT
        );

        verify(transferRepository).countTransfersExcludingCurrent(
                TRANSFER_ID,
                SENDER,
                TransferStatus.SUCCESS,
                COMPLETED_AT.minusMinutes(10),
                COMPLETED_AT
        );
    }

    @Test
    void 동일_완료_시각의_다른_거래는_집계에_포함한다() {
        mockFiveMinutes(
                stats(3, 2, 1, 2, 2)
        );

        when(transferRepository.countTransfersToRecipient(
                TRANSFER_ID,
                SENDER,
                RECEIVER,
                TransferStatus.SUCCESS,
                COMPLETED_AT.minusMinutes(5),
                COMPLETED_AT
        )).thenReturn(1L);

        when(transferRepository.countSmallTransfersToRecipient(
                TRANSFER_ID,
                SENDER,
                RECEIVER,
                TransferStatus.SUCCESS,
                SMALL_LIMIT,
                COMPLETED_AT.minusMinutes(5),
                COMPLETED_AT
        )).thenReturn(1L);

        when(transferRepository.countTransfersExcludingCurrent(
                TRANSFER_ID,
                SENDER,
                TransferStatus.SUCCESS,
                COMPLETED_AT.minusMinutes(10),
                COMPLETED_AT
        )).thenReturn(3L);

        when(transferRepository.findLargeDeposits(
                eq(SENDER),
                eq(TransferStatus.SUCCESS),
                eq(BigDecimal.valueOf(1_000_000)),
                eq(COMPLETED_AT.minusMinutes(10)),
                eq(COMPLETED_AT),
                eq(PageRequest.of(0, 1))
        )).thenReturn(List.of());

        FdsContext context = createContext(1);

        assertEquals(4, context.transferCount5Min());
        assertEquals(2, context.distinctRecipients5Min());
        assertEquals(2, context.sameRecipientTransferCount5Min());
        assertEquals(3, context.smallTransferCount5Min());
        assertEquals(2, context.smallTransferRecipients5Min());
        assertEquals(4, context.transferCount10Min());
    }

    @Test
    void 완료_거래에서도_복합_HIGH_조건을_유지한다() {
        mockFiveMinutes(
                stats(5, 5, 0, 0, 0)
        );

        when(transferRepository.countTransfersExcludingCurrent(
                TRANSFER_ID,
                SENDER,
                TransferStatus.SUCCESS,
                COMPLETED_AT.minusMinutes(10),
                COMPLETED_AT
        )).thenReturn(5L);

        when(transferRepository
                .countByFromAccountIdAndStatusAndCompletedAtGreaterThanEqualAndCompletedAtLessThan(
                        SENDER,
                        TransferStatus.SUCCESS,
                        COMPLETED_AT.minusMinutes(70),
                        COMPLETED_AT.minusMinutes(10)
                )).thenReturn(4L);

        FdsTransferRead deposit =
                mock(FdsTransferRead.class);

        LocalDateTime depositAt =
                COMPLETED_AT.minusMinutes(5);

        when(deposit.getCompletedAt())
                .thenReturn(depositAt);

        when(deposit.getAmount())
                .thenReturn(BigDecimal.valueOf(2_000_000));

        when(transferRepository.findLargeDeposits(
                eq(SENDER),
                eq(TransferStatus.SUCCESS),
                eq(BigDecimal.valueOf(1_000_000)),
                eq(COMPLETED_AT.minusMinutes(10)),
                eq(COMPLETED_AT),
                eq(PageRequest.of(0, 1))
        )).thenReturn(List.of(deposit));

        when(transferRepository.sumOutgoingAmount(
                TRANSFER_ID,
                SENDER,
                TransferStatus.SUCCESS,
                depositAt,
                COMPLETED_AT
        )).thenReturn(BigDecimal.valueOf(1_000_000));

        when(transferRepository.findDistinctOutgoingRecipientIds(
                TRANSFER_ID,
                SENDER,
                TransferStatus.SUCCESS,
                depositAt,
                COMPLETED_AT
        )).thenReturn(List.of(20L, 21L, 22L));

        when(transferRepository.findPreviousRecipientIdsAmong(
                SENDER,
                TransferStatus.SUCCESS,
                depositAt.minusDays(30),
                depositAt,
                List.of(20L, 21L, 22L, RECEIVER)
        )).thenReturn(List.of(20L));

        FdsContext context = createContext(800_000);

        assertEquals(6, context.transferCount10Min());
        assertEquals(4, context.previousTransferCount60Min());

        assertEquals(4, context.distinctRecipientsSinceDeposit());
        assertEquals(3, context.newRecipientsSinceDeposit());

        assertEquals(
                0,
                BigDecimal.valueOf(1_800_000).compareTo(
                        context.outgoingAmountSinceDeposit()
                )
        );

        assertTrue(context.hasMultipleNewRecipients());
        assertTrue(context.hasFrequencySpike());

        int score = riskCalculator.calculateScore(
                ruleService.evaluate(context),
                context
        );

        assertEquals(90, score);
        assertEquals(
                FdsRiskLevel.HIGH,
                riskCalculator.determineRiskLevel(score)
        );
    }
}
