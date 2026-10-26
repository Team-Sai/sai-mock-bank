
package org.teamsai.saimockbank.domain.fds.service;

import org.springframework.stereotype.Service;
import org.teamsai.saimockbank.domain.fds.dto.FdsContext;
import org.teamsai.saimockbank.domain.fds.dto.FdsRuleResult;
import org.teamsai.saimockbank.domain.fds.dto.GeoIpResolution;
import org.teamsai.saimockbank.domain.fds.type.FdsRuleType;

import java.math.BigDecimal;
import java.util.ArrayList;
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

    public List<FdsRuleResult> evaluate(
            FdsContext context,
            GeoIpResolution location
    ) {
        List<FdsRuleResult> results =
                new ArrayList<>(evaluate(context));

        if (location != null && location.foreignIp()) {
            results.add(FdsRuleResult.matched(
                    FdsRuleType.R04,
                    "해외 IP 국가(" + location.countryCode()
                            + ")에서 송금을 요청했습니다."
            ));
        } else {
            results.add(FdsRuleResult.notMatched(FdsRuleType.R04));
        }

        return List.copyOf(results);
    }

    private FdsRuleResult evaluateR01(FdsContext context) {
        boolean repeated =
                context.smallTransferCount5Min() >= 20;

        boolean multipleRecipients =
                context.smallTransferRecipients5Min() >= 5;

        if (repeated && multipleRecipients) {
            return FdsRuleResult.matched(
                    FdsRuleType.R01,
                    "최근 5분간 1,000원 이하 송금이 "
                            + "20회 이상 발생했으며 "
                            + "소액 송금 수취계좌가 5개 이상입니다."
            );
        }

        return FdsRuleResult.notMatched(FdsRuleType.R01);
    }

    private FdsRuleResult evaluateR02(FdsContext context) {
        BigDecimal depositAmount =
                context.recentLargeDepositAmount();

        if (depositAmount.compareTo(LARGE_DEPOSIT_LIMIT) < 0) {
            return FdsRuleResult.notMatched(FdsRuleType.R02);
        }

        boolean multipleRecipients =
                context.distinctRecipientsSinceDeposit() >= 3;

        BigDecimal requiredAmount =
                depositAmount.multiply(DISTRIBUTION_RATIO);

        boolean highOutgoingRatio =
                context.outgoingAmountSinceDeposit()
                        .compareTo(requiredAmount) >= 0;

        if (multipleRecipients && highOutgoingRatio) {
            return FdsRuleResult.matched(
                    FdsRuleType.R02,
                    "최근 고액 입금 이후 3개 이상의 계좌로 "
                            + "입금액의 80% 이상을 송금했습니다."
            );
        }

        return FdsRuleResult.notMatched(FdsRuleType.R02);
    }

    private FdsRuleResult evaluateR03(FdsContext context) {
        boolean repeated = context.transferCount5Min() >= 15;

        boolean sameRecipientRepeated =
                context.sameRecipientTransferCount5Min() >= 15;

        if (repeated && sameRecipientRepeated) {
            return FdsRuleResult.matched(
                    FdsRuleType.R03,
                    "최근 5분간 동일 수취계좌에 "
                            + "15회 이상 송금했습니다."
            );
        }

        return FdsRuleResult.notMatched(FdsRuleType.R03);
    }
}
