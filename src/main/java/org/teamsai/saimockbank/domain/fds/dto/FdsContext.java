package org.teamsai.saimockbank.domain.fds.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FdsContext(

        // 현재 평가 중인 이체 요청
        Long senderAccountId,
        Long receiverAccountId,
        BigDecimal amount,
        LocalDateTime requestedAt,

        // R01, R03: 최근 5분 전체 거래 집계
        int transferCount5Min,
        int distinctRecipients5Min,
        int sameRecipientTransferCount5Min,

        // R01: 최근 5분 소액 거래 집계
        int smallTransferCount5Min,
        int smallTransferRecipients5Min,

        // R02: 고액 입금 이후 거래 집계
        BigDecimal recentLargeDepositAmount,
        BigDecimal outgoingAmountSinceDeposit,
        int distinctRecipientsSinceDeposit,

        // HIGH: 복합 위험 판단 데이터
        int newRecipientsSinceDeposit,
        int transferCount10Min,
        int previousTransferCount60Min

) {


    public FdsContext {

        // 필수 거래 정보 검증
        if (senderAccountId == null
                || receiverAccountId == null
                || amount == null
                || requestedAt == null) {

            throw new IllegalArgumentException(
                    "FDS 평가에 필요한 거래 정보가 누락되었습니다."
            );
        }

        // 이체 금액 검증
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "이체 금액은 0원보다 커야 합니다."
            );
        }

        // 동일 계좌 이체 방지
        if (senderAccountId.equals(receiverAccountId)) {
            throw new IllegalArgumentException(
                    "송금인과 수취인 계좌가 동일합니다."
            );
        }

        // 거래 집계값 검증
        if (transferCount5Min < 0
                || distinctRecipients5Min < 0
                || sameRecipientTransferCount5Min < 0
                || smallTransferCount5Min < 0
                || smallTransferRecipients5Min < 0
                || distinctRecipientsSinceDeposit < 0
                || newRecipientsSinceDeposit < 0
                || transferCount10Min < 0
                || previousTransferCount60Min < 0) {

            throw new IllegalArgumentException(
                    "거래 집계값은 음수일 수 없습니다."
            );
        }

        // 고액 입금 정보가 없으면 0으로 초기화
        if (recentLargeDepositAmount == null) {
            recentLargeDepositAmount = BigDecimal.ZERO;
        }

        if (outgoingAmountSinceDeposit == null) {
            outgoingAmountSinceDeposit = BigDecimal.ZERO;
        }

        // 입출금 집계 금액 검증
        if (recentLargeDepositAmount.signum() < 0
                || outgoingAmountSinceDeposit.signum() < 0) {

            throw new IllegalArgumentException(
                    "입출금 집계 금액은 음수일 수 없습니다."
            );
        }
    }


    public FdsContext(
            Long senderAccountId,
            Long receiverAccountId,
            BigDecimal amount,
            LocalDateTime requestedAt,

            int transferCount5Min,
            int distinctRecipients5Min,
            int sameRecipientTransferCount5Min,

            int smallTransferCount5Min,
            int smallTransferRecipients5Min,

            BigDecimal recentLargeDepositAmount,
            BigDecimal outgoingAmountSinceDeposit,
            int distinctRecipientsSinceDeposit
    ) {

        this(
                senderAccountId,
                receiverAccountId,
                amount,
                requestedAt,

                transferCount5Min,
                distinctRecipients5Min,
                sameRecipientTransferCount5Min,

                smallTransferCount5Min,
                smallTransferRecipients5Min,

                recentLargeDepositAmount,
                outgoingAmountSinceDeposit,
                distinctRecipientsSinceDeposit,

                // 신규 HIGH 판단 필드 기본값
                0,
                0,
                0
        );
    }


    public boolean hasMultipleNewRecipients() {

        return newRecipientsSinceDeposit >= 3;
    }

    public boolean hasFrequencySpike() {

        return transferCount10Min >= 6
                && previousTransferCount60Min >= 3
                && transferCount10Min >= previousTransferCount60Min;
    }
}