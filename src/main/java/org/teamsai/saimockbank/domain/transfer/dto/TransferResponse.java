package org.teamsai.saimockbank.domain.transfer.dto;

import org.teamsai.saimockbank.domain.transfer.entity.BankTransfer;
import org.teamsai.saimockbank.domain.transfer.entity.TransferStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransferResponse(
        Long transferId,
        String requestKey,
        Long fromAccountId,
        Long toAccountId,
        BigDecimal amount,
        TransferStatus status,
        String failureReason,
        LocalDateTime completedAt
) {

    public static TransferResponse from(BankTransfer transfer) {
        return new TransferResponse(
                transfer.getTransferId(),
                transfer.getRequestKey(),
                transfer.getFromAccountId(),
                transfer.getToAccountId(),
                transfer.getAmount(),
                transfer.getStatus(),
                transfer.getFailureReason(),
                transfer.getCompletedAt()
        );
    }
}