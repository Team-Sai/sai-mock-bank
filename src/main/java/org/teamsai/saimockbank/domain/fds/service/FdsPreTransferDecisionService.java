
package org.teamsai.saimockbank.domain.fds.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.teamsai.saimockbank.domain.fds.dto.FdsContext;
import org.teamsai.saimockbank.domain.fds.dto.FdsRuleResult;
import org.teamsai.saimockbank.domain.fds.dto.GeoIpResolution;
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
    private final GeoIpService geoIpService;

    public Decision assess(
            Long senderAccountId,
            Long receiverAccountId,
            BigDecimal amount
    ) {
        return assess(senderAccountId, receiverAccountId, amount, null);
    }

    public Decision assess(
            Long senderAccountId,
            Long receiverAccountId,
            BigDecimal amount,
            String clientIp
    ) {
        try {
            FdsContext context = contextService.create(
                    senderAccountId,
                    receiverAccountId,
                    amount,
                    LocalDateTime.now()
            );

            GeoIpResolution location = geoIpService.resolve(clientIp);

            List<FdsRuleResult> results =
                    ruleService.evaluate(context, location);

            int score = calculator.calculateScore(results, context);

            FdsRiskLevel riskLevel =
                    calculator.determineRiskLevel(score);

            log.info(
                    "FDS Decision: sender={}, receiver={}, country={}, score={}, riskLevel={}, requiresReview={}",
                    senderAccountId,
                    receiverAccountId,
                    location.countryCode(),
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
