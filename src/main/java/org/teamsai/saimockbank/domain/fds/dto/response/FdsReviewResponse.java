
package org.teamsai.saimockbank.domain.fds.dto.response;

import org.teamsai.saimockbank.domain.transfer.dto.BankTransferDTO;
import org.teamsai.saimockbank.domain.transfer.dto.TransferStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FdsReviewResponse(
        Long transferId,
        String requestKey,
        Long fromAccountId,
        Long toAccountId,
        BigDecimal amount,
        TransferStatus status,
        Long reviewedBy,
        LocalDateTime reviewedAt,
        String reviewReason,
        LocalDateTime createdAt,
        LocalDateTime completedAt
) {

    public static FdsReviewResponse from(
            BankTransferDTO transfer
    ) {
        return new FdsReviewResponse(
                transfer.getTransferId(),
                transfer.getRequestKey(),
                transfer.getFromAccountId(),
                transfer.getToAccountId(),
                transfer.getAmount(),
                transfer.getStatus(),
                transfer.getReviewedBy(),
                transfer.getReviewedAt(),
                transfer.getReviewReason(),
                transfer.getCreatedAt(),
                transfer.getCompletedAt()
        );
    }
}
