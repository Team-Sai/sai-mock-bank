package org.teamsai.saimockbank.domain.fds.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FdsTransferCompletedEvent(
        Long transferId,
        Long senderAccountId,
        Long receiverAccountId,
        BigDecimal amount,
        LocalDateTime completedAt
) {
}
