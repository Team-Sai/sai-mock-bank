package org.teamsai.saimockbank.domain.transfer.dto;

import java.math.BigDecimal;

public record TransferRequest(
        String requestKey,
        String fromUserKey,
        Long fromAccountId,
        Long toAccountId,
        BigDecimal amount,
        String senderMemo,
        String receiverMemo
) {
}
