package org.teamsai.saimockbank.domain.transfer.entity;

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

}
