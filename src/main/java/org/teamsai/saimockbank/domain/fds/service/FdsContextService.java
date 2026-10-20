package org.teamsai.saimockbank.domain.fds.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saimockbank.domain.fds.dto.FdsContext;
import org.teamsai.saimockbank.domain.fds.entity.FdsTransferRead;
import org.teamsai.saimockbank.domain.fds.repository.FdsTransferRepository;
import org.teamsai.saimockbank.domain.transfer.dto.TransferStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FdsContextService {

    private static final BigDecimal SMALL_TRANSFER_LIMIT =
            BigDecimal.valueOf(1_000);

    private static final BigDecimal LARGE_DEPOSIT_LIMIT =
            BigDecimal.valueOf(1_000_000);

    private final FdsTransferRepository transferRepository;

    @Transactional(readOnly = true)
    public FdsContext create(
            Long senderAccountId,
            Long receiverAccountId,
            BigDecimal amount,
            LocalDateTime requestedAt
    ) {

        LocalDateTime fiveMinutesAgo =
                requestedAt.minusMinutes(5);

        LocalDateTime tenMinutesAgo =
                requestedAt.minusMinutes(10);

        LocalDateTime seventyMinutesAgo =
                requestedAt.minusMinutes(70);

        // 1. 최근 5분 전체 송금 조회
        List<FdsTransferRead> recentTransfers =
                transferRepository
                        .findByFromAccountIdAndStatusAndCompletedAtBetween(
                                senderAccountId,
                                TransferStatus.SUCCESS,
                                fiveMinutesAgo,
                                requestedAt
                        );

        // 현재 송금 요청 포함
        int transferCount5Min =
                recentTransfers.size() + 1;

        Set<Long> recipients = new HashSet<>();

        for (FdsTransferRead transfer : recentTransfers) {
            recipients.add(transfer.getToAccountId());
        }

        recipients.add(receiverAccountId);

        int distinctRecipients5Min = recipients.size();

        int sameRecipientTransferCount5Min =
                (int) recentTransfers.stream()
                        .filter(transfer ->
                                receiverAccountId.equals(
                                        transfer.getToAccountId()
                                )
                        )
                        .count() + 1;

        // 2. 최근 5분 소액 송금 집계
        List<FdsTransferRead> smallTransfers =
                recentTransfers.stream()
                        .filter(transfer ->
                                transfer.getAmount()
                                        .compareTo(SMALL_TRANSFER_LIMIT) <= 0
                        )
                        .toList();

        int smallTransferCount5Min =
                smallTransfers.size();

        Set<Long> smallRecipients = new HashSet<>();

        for (FdsTransferRead transfer : smallTransfers) {
            smallRecipients.add(transfer.getToAccountId());
        }

        if (amount.compareTo(SMALL_TRANSFER_LIMIT) <= 0) {
            smallTransferCount5Min++;
            smallRecipients.add(receiverAccountId);
        }

        int smallTransferRecipients5Min =
                smallRecipients.size();

        // 3. HIGH용 최근 10분 거래량
        long previous10MinCount =
                transferRepository
                        .countByFromAccountIdAndStatusAndCompletedAtGreaterThanEqualAndCompletedAtLessThan(
                                senderAccountId,
                                TransferStatus.SUCCESS,
                                tenMinutesAgo,
                                requestedAt
                        );

        int transferCount10Min =
                Math.toIntExact(previous10MinCount + 1);

        // 4. HIGH용 비교 구간 60분 거래량
        long previous60MinCount =
                transferRepository
                        .countByFromAccountIdAndStatusAndCompletedAtGreaterThanEqualAndCompletedAtLessThan(
                                senderAccountId,
                                TransferStatus.SUCCESS,
                                seventyMinutesAgo,
                                tenMinutesAgo
                        );

        int previousTransferCount60Min =
                Math.toIntExact(previous60MinCount);

        // 5. R02 고액 입금 이후 거래 집계
        BigDecimal recentLargeDepositAmount =
                BigDecimal.ZERO;

        BigDecimal outgoingAmountSinceDeposit =
                BigDecimal.ZERO;

        int distinctRecipientsSinceDeposit = 0;
        int newRecipientsSinceDeposit = 0;

        Optional<FdsTransferRead> recentDeposit =
                transferRepository
                        .findFirstByToAccountIdAndStatusAndAmountGreaterThanEqualAndCompletedAtBetweenOrderByCompletedAtDesc(
                                senderAccountId,
                                TransferStatus.SUCCESS,
                                LARGE_DEPOSIT_LIMIT,
                                tenMinutesAgo,
                                requestedAt
                        );

        if (recentDeposit.isPresent()) {

            FdsTransferRead deposit = recentDeposit.get();

            LocalDateTime depositAt =
                    deposit.getCompletedAt();

            recentLargeDepositAmount =
                    deposit.getAmount();

            List<FdsTransferRead> outgoingTransfers =
                    transferRepository
                            .findByFromAccountIdAndStatusAndCompletedAtBetweenOrderByCompletedAtAsc(
                                    senderAccountId,
                                    TransferStatus.SUCCESS,
                                    depositAt,
                                    requestedAt
                            );

            // 입금 이후 출금액 + 현재 요청 금액
            outgoingAmountSinceDeposit =
                    outgoingTransfers.stream()
                            .map(FdsTransferRead::getAmount)
                            .reduce(
                                    amount,
                                    BigDecimal::add
                            );

            // 입금 이후 수취계좌 집계
            Set<Long> outgoingRecipients = new HashSet<>();

            for (FdsTransferRead transfer : outgoingTransfers) {
                outgoingRecipients.add(
                        transfer.getToAccountId()
                );
            }

            outgoingRecipients.add(receiverAccountId);

            distinctRecipientsSinceDeposit =
                    outgoingRecipients.size();

            // 기준 입금 이전 30일간 거래했던 수취계좌
            List<Long> previousRecipients =
                    transferRepository.findPreviousRecipientIds(
                            senderAccountId,
                            TransferStatus.SUCCESS,
                            depositAt.minusDays(30),
                            depositAt
                    );

            Set<Long> previousRecipientSet =
                    new HashSet<>(previousRecipients);

            // 신규 거래 관계에 해당하는 수취계좌 수
            newRecipientsSinceDeposit =
                    (int) outgoingRecipients.stream()
                            .filter(recipientId ->
                                    !previousRecipientSet.contains(
                                            recipientId
                                    )
                            )
                            .count();
        }

        // 6. FDS 평가 Context 생성
        return new FdsContext(
                senderAccountId,
                receiverAccountId,
                amount,
                requestedAt,

                transferCount5Min,
                distinctRecipients5Min,
                sameRecipientTransferCount5Min,

                smallTransferCount5Min,
                smallTransferRecipients5Min,

                recentLargeDepositAmount,
                outgoingAmountSinceDeposit,
                distinctRecipientsSinceDeposit,

                newRecipientsSinceDeposit,
                transferCount10Min,
                previousTransferCount60Min
        );
    }
}