package org.teamsai.saimockbank.domain.transfer.dto;

import java.math.BigDecimal;

public record TransferRequest(
        String requestKey,
        Long fromAccountId,
        String toAccountNumber,
        BigDecimal amount,
        String senderMemo,
        String receiverMemo
) {
}
