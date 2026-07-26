package org.teamsai.saimockbank.domain.transfer.entity;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.teamsai.saimockbank.global.common.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class BankTransfer extends BaseEntity {
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

    @Builder
    private BankTransfer(
            String requestKey,
            Long fromAccountId,
            Long toAccountId,
            BigDecimal amount,
            String senderMemo,
            String receiverMemo,
            TransferStatus status,
            String failureReason,
            LocalDateTime completedAt
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
    }

}
