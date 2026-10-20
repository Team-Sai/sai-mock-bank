
package org.teamsai.saimockbank.domain.fds.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
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

        List<FdsTransferRead> recentTransfers =
                transferRepository
                        .findByFromAccountIdAndStatusAndCompletedAtBetween(
                                senderAccountId,
                                TransferStatus.SUCCESS,
                                fiveMinutesAgo,
                                requestedAt
                        );

        int transferCount5Min = recentTransfers.size() + 1;

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

        List<FdsTransferRead> smallTransfers =
                recentTransfers.stream()
                        .filter(transfer ->
                                transfer.getAmount()
                                        .compareTo(SMALL_TRANSFER_LIMIT) <= 0
                        )
                        .toList();

        int smallTransferCount5Min = smallTransfers.size();

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

            outgoingAmountSinceDeposit =
                    outgoingTransfers.stream()
                            .map(FdsTransferRead::getAmount)
                            .reduce(
                                    amount,
                                    BigDecimal::add
                            );

            Set<Long> outgoingRecipients = new HashSet<>();

            for (FdsTransferRead transfer : outgoingTransfers) {
                outgoingRecipients.add(
                        transfer.getToAccountId()
                );
            }

            outgoingRecipients.add(receiverAccountId);

            distinctRecipientsSinceDeposit =
                    outgoingRecipients.size();

            List<Long> previousRecipients =
                    transferRepository.findPreviousRecipientIds(
                            senderAccountId,
                            TransferStatus.SUCCESS,
                            depositAt.minusDays(30),
                            depositAt
                    );

            Set<Long> previousRecipientSet =
                    new HashSet<>(previousRecipients);

            newRecipientsSinceDeposit =
                    (int) outgoingRecipients.stream()
                            .filter(recipientId ->
                                    !previousRecipientSet.contains(
                                            recipientId
                                    )
                            )
                            .count();
        }

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

    @Transactional(readOnly = true)
    public FdsContext createForCompletedTransfer(
            Long senderAccountId,
            Long receiverAccountId,
            BigDecimal amount,
            LocalDateTime completedAt
    ) {
        LocalDateTime fiveMinutesAgo =
                completedAt.minusMinutes(5);

        LocalDateTime tenMinutesAgo =
                completedAt.minusMinutes(10);

        LocalDateTime seventyMinutesAgo =
                completedAt.minusMinutes(70);

        FdsTransferRepository.FiveMinuteStats stats =
                transferRepository.aggregateFiveMinutes(
                        senderAccountId,
                        receiverAccountId,
                        TransferStatus.SUCCESS,
                        SMALL_TRANSFER_LIMIT,
                        fiveMinutesAgo,
                        completedAt
                );

        int transferCount5Min =
                Math.toIntExact(stats.getTransferCount() + 1);

        long existingReceiverCount =
                transferRepository.countTransfersToRecipient(
                        senderAccountId,
                        receiverAccountId,
                        TransferStatus.SUCCESS,
                        fiveMinutesAgo,
                        completedAt
                );

        int distinctRecipients5Min =
                Math.toIntExact(
                        stats.getDistinctRecipients()
                                + (existingReceiverCount == 0 ? 1 : 0)
                );

        int sameRecipientTransferCount5Min =
                Math.toIntExact(stats.getSameRecipientCount() + 1);

        boolean currentSmallTransfer =
                amount.compareTo(SMALL_TRANSFER_LIMIT) <= 0;

        int smallTransferCount5Min =
                Math.toIntExact(
                        stats.getSmallTransferCount()
                                + (currentSmallTransfer ? 1 : 0)
                );

        int smallTransferRecipients5Min =
                Math.toIntExact(stats.getSmallRecipientCount());

        if (currentSmallTransfer) {
            long previousSmallReceiverCount =
                    transferRepository.countSmallTransfersToRecipient(
                            senderAccountId,
                            receiverAccountId,
                            TransferStatus.SUCCESS,
                            SMALL_TRANSFER_LIMIT,
                            fiveMinutesAgo,
                            completedAt
                    );

            if (previousSmallReceiverCount == 0) {
                smallTransferRecipients5Min++;
            }
        }

        long previous10MinCount =
                transferRepository
                        .countByFromAccountIdAndStatusAndCompletedAtGreaterThanEqualAndCompletedAtLessThan(
                                senderAccountId,
                                TransferStatus.SUCCESS,
                                tenMinutesAgo,
                                completedAt
                        );

        int transferCount10Min =
                Math.toIntExact(previous10MinCount + 1);

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

        BigDecimal recentLargeDepositAmount =
                BigDecimal.ZERO;

        BigDecimal outgoingAmountSinceDeposit =
                BigDecimal.ZERO;

        int distinctRecipientsSinceDeposit = 0;
        int newRecipientsSinceDeposit = 0;

        List<FdsTransferRead> deposits =
                transferRepository.findLargeDeposits(
                        senderAccountId,
                        TransferStatus.SUCCESS,
                        LARGE_DEPOSIT_LIMIT,
                        tenMinutesAgo,
                        completedAt,
                        PageRequest.of(0, 1)
                );

        if (!deposits.isEmpty()) {

            FdsTransferRead deposit = deposits.get(0);

            LocalDateTime depositAt =
                    deposit.getCompletedAt();

            recentLargeDepositAmount =
                    deposit.getAmount();

            BigDecimal previousOutgoingAmount =
                    transferRepository.sumOutgoingAmount(
                            senderAccountId,
                            TransferStatus.SUCCESS,
                            depositAt,
                            completedAt
                    );

            outgoingAmountSinceDeposit =
                    amount.add(
                            previousOutgoingAmount == null
                                    ? BigDecimal.ZERO
                                    : previousOutgoingAmount
                    );

            Set<Long> outgoingRecipients =
                    new HashSet<>(
                            transferRepository.findDistinctOutgoingRecipientIds(
                                    senderAccountId,
                                    TransferStatus.SUCCESS,
                                    depositAt,
                                    completedAt
                            )
                    );

            outgoingRecipients.add(receiverAccountId);

            distinctRecipientsSinceDeposit =
                    outgoingRecipients.size();

            List<Long> previousRecipients =
                    transferRepository.findPreviousRecipientIdsAmong(
                            senderAccountId,
                            TransferStatus.SUCCESS,
                            depositAt.minusDays(30),
                            depositAt,
                            List.copyOf(outgoingRecipients)
                    );

            Set<Long> previousRecipientSet =
                    new HashSet<>(previousRecipients);

            newRecipientsSinceDeposit =
                    (int) outgoingRecipients.stream()
                            .filter(recipientId ->
                                    !previousRecipientSet.contains(
                                            recipientId
                                    )
                            )
                            .count();
        }

        return new FdsContext(
                senderAccountId,
                receiverAccountId,
                amount,
                completedAt,
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
