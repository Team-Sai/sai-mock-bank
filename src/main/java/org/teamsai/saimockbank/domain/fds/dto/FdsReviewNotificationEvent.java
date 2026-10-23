package org.teamsai.saimockbank.domain.fds.dto;

import java.math.BigDecimal;

public record FdsReviewNotificationEvent(
        Long userId,
        Long transferId,
        BigDecimal amount,
        String status
) {
}