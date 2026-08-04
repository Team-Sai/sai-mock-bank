package org.teamsai.saimockbank.domain.transfer.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class BankTransferDTO{
    private Long transferId;
    private String requestKey;
    private Long fromAccountId;
    private Long toAccountId;
    private BigDecimal amount;
    private String senderMemo;
    private String receiverMemo;
    private TransferStatus status;
    private String failureReason;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Builder
    private BankTransferDTO(
            String requestKey,
            Long fromAccountId,
            Long toAccountId,
            BigDecimal amount,
            String senderMemo,
            String receiverMemo,
            TransferStatus status,
            String failureReason,
            LocalDateTime completedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.requestKey = requestKey;
        this.fromAccountId = fromAccountId;
        this.toAccountId = toAccountId;
        this.amount = amount;
        this.senderMemo = senderMemo;
        this.receiverMemo = receiverMemo;
        this.status = status;
        this.failureReason = failureReason;
        this.completedAt = completedAt;
        this.createdAt = createdAt;
        this.updatedAt =updatedAt;
    }

}
