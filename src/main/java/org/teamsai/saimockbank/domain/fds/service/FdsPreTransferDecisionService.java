
package org.teamsai.saimockbank.domain.fds.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.teamsai.saimockbank.domain.fds.dto.FdsContext;
import org.teamsai.saimockbank.domain.fds.dto.FdsRuleResult;
import org.teamsai.saimockbank.domain.fds.type.FdsRiskLevel;
import org.teamsai.saimockbank.domain.fds.util.FdsRiskCalculator;
import org.teamsai.saimockbank.domain.transfer.exception.TransferReviewErrorCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FdsPreTransferDecisionService {

    private final FdsContextService contextService;
    private final FdsRuleService ruleService;
    private final FdsRiskCalculator calculator;

    public Decision assess(
            Long senderAccountId,
            Long receiverAccountId,
            BigDecimal amount
    ) {
        try {
            FdsContext context = contextService.create(
                    senderAccountId,
                    receiverAccountId,
                    amount,
                    LocalDateTime.now()
            );

            log.info(
                    "FDS Context: sender={}, receiver={}, amount={}, deposit={}, outgoing={}, recipients={}, newRecipients={}, recent10Min={}, previous60Min={}",
                    senderAccountId,
                    receiverAccountId,
                    amount,
                    context.recentLargeDepositAmount(),
                    context.outgoingAmountSinceDeposit(),
                    context.distinctRecipientsSinceDeposit(),
                    context.newRecipientsSinceDeposit(),
                    context.transferCount10Min(),
                    context.previousTransferCount60Min()
            );

            List<FdsRuleResult> results =
                    ruleService.evaluate(context);

            int score = calculator.calculateScore(
                    results,
                    context
            );

            FdsRiskLevel riskLevel =
                    calculator.determineRiskLevel(score);

            log.info(
                    "FDS Decision: sender={}, receiver={}, score={}, riskLevel={}, requiresReview={}",
                    senderAccountId,
                    receiverAccountId,
                    score,
                    riskLevel,
                    riskLevel == FdsRiskLevel.HIGH
            );

            return new Decision(
                    context,
                    riskLevel,
                    List.copyOf(results),
                    score
            );

        } catch (RuntimeException exception) {
            log.error(
                    "FDS 사전 평가 실패. sender={}, receiver={}",
                    senderAccountId,
                    receiverAccountId,
                    exception
            );

            throw TransferReviewErrorCode.FDS_UNAVAILABLE
                    .toException();
        }
    }

    public record Decision(
            FdsContext context,
            FdsRiskLevel riskLevel,
            List<FdsRuleResult> results,
            int score
    ) {
        public boolean requiresReview() {
            return riskLevel == FdsRiskLevel.HIGH;
        }
    }
}
