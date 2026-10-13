package org.teamsai.saimockbank.domain.transfer.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record TransferRequest(
        @NotBlank(message = "요청 키는 필수입니다.")
        @Size(max = 100, message = "요청 키는 100자 이하로 입력해주세요.")
        String requestKey,
        @NotNull(message = "출금 계좌 ID는 필수입니다.")
        @Positive(message = "출금 계좌 ID는 양수여야 합니다.")
        Long fromAccountId,
        @Size(max=30, message = "계좌번호는 30자 이하로 입력해주세요.")
        String toAccountNumber,
        @NotNull(message = "이체 금액은 필수입니다.")
        @Digits(integer = 17, fraction = 2,message = "이체 금액은 정수부 17자리, 소수부 2자리 이하여야 합니다.")
        BigDecimal amount,
        @Size(max = 100, message = "보내는 메모는 100자 이하로 입력해주세요.")
        String senderMemo,
        @Size(max = 100, message = "받는 메모는 100자 이하로 입력해주세요.")
        String receiverMemo
) {
}
