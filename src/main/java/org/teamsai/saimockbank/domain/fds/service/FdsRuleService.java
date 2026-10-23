package org.teamsai.saimockbank.domain.fds.service;

import org.springframework.stereotype.Service;
import org.teamsai.saimockbank.domain.fds.dto.FdsContext;
import org.teamsai.saimockbank.domain.fds.dto.FdsRuleResult;
import org.teamsai.saimockbank.domain.fds.type.FdsRuleType;

import java.math.BigDecimal;
import java.util.List;

@Service
public class FdsRuleService {

    private static final BigDecimal LARGE_DEPOSIT_LIMIT =
            BigDecimal.valueOf(1_000_000);

    private static final BigDecimal DISTRIBUTION_RATIO =
            new BigDecimal("0.80");

    public List<FdsRuleResult> evaluate(FdsContext context) {

        return List.of(
                evaluateR01(context),
                evaluateR02(context),
                evaluateR03(context)
        );
    }

    /**
     * R01: 자동화 소액 송금 의심
     *
     * - 최근 5분간 1,000원 이하 송금 20회 이상
     * - 소액 송금 수취계좌 5개 이상
     */
    private FdsRuleResult evaluateR01(FdsContext context) {

        boolean isRepeatedSmallTransfer =
                context.smallTransferCount5Min() >= 20;

        boolean hasMultipleRecipients =
                context.smallTransferRecipients5Min() >= 5;

        if (isRepeatedSmallTransfer
                && hasMultipleRecipients) {

            return FdsRuleResult.matched(
                    FdsRuleType.R01,
                    "최근 5분간 1,000원 이하 송금이 "
                            + "20회 이상 발생했으며 "
                            + "소액 송금 수취계좌가 5개 이상입니다."
            );
        }

        return FdsRuleResult.notMatched(FdsRuleType.R01);
    }

    /**
     * R02: 입금 직후 자금 분산 의심
     *
     * - 최근 10분 내 100만원 이상 단일 입금
     * - 해당 입금 이후 수취계좌 3개 이상
     * - 입금액 대비 출금액 80% 이상
     */
    private FdsRuleResult evaluateR02(FdsContext context) {

        BigDecimal depositAmount =
                context.recentLargeDepositAmount();

        boolean hasLargeDeposit =
                depositAmount.compareTo(LARGE_DEPOSIT_LIMIT) >= 0;

        if (!hasLargeDeposit) {
            return FdsRuleResult.notMatched(FdsRuleType.R02);
        }

        boolean hasMultipleRecipients =
                context.distinctRecipientsSinceDeposit() >= 3;

        BigDecimal requiredOutgoingAmount =
                depositAmount.multiply(DISTRIBUTION_RATIO);

        boolean hasHighOutgoingRatio =
                context.outgoingAmountSinceDeposit()
                        .compareTo(requiredOutgoingAmount) >= 0;

        if (hasMultipleRecipients && hasHighOutgoingRatio) {

            return FdsRuleResult.matched(
                    FdsRuleType.R02,
                    "최근 고액 입금 이후 3개 이상의 계좌로 "
                            + "입금액의 80% 이상을 송금했습니다."
            );
        }

        return FdsRuleResult.notMatched(FdsRuleType.R02);
    }

    /**
     * R03: 특정 계좌 집중 송금 의심
     *
     * - 최근 5분간 전체 송금 15회 이상
     * - 동일 수취계좌에 15회 이상
     */
    private FdsRuleResult evaluateR03(FdsContext context) {

        boolean isRepeated =
                context.transferCount5Min() >= 15;

        boolean isSameRecipientRepeated =
                context.sameRecipientTransferCount5Min() >= 15;

        if (isRepeated && isSameRecipientRepeated) {

            return FdsRuleResult.matched(
                    FdsRuleType.R03,
                    "최근 5분간 동일 수취계좌에 "
                            + "15회 이상 송금했습니다."
            );
        }

        return FdsRuleResult.notMatched(FdsRuleType.R03);
    }
}