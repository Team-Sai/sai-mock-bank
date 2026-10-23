
package org.teamsai.saimockbank.domain.transfer.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saimockbank.domain.fds.dto.FdsTransferCompletedEvent;
import org.teamsai.saimockbank.domain.fds.service.FdsPreTransferDecisionService;
import org.teamsai.saimockbank.domain.fds.service.FdsService;
import org.teamsai.saimockbank.domain.transfer.dto.BankTransferDTO;
import org.teamsai.saimockbank.domain.transfer.dto.TransferRequest;

@Service
@RequiredArgsConstructor
public class FdsTransferCommandService {

    private final TransferProcessor processor;
    private final ApplicationEventPublisher events;
    private final FdsService fdsService;

    @Transactional
    public BankTransferDTO execute(
            Long userId,
            TransferRequest request,
            Long receiverId,
            FdsPreTransferDecisionService.Decision decision
    ) {
        if (decision.requiresReview()) {
            BankTransferDTO pending =
                    processor.processHold(
                            request,
                            userId,
                            receiverId
                    );

            fdsService.evaluate(
                    pending.getTransferId(),
                    decision.context(),
                    decision.results(),
                    decision.score(),
                    decision.riskLevel()
            );

            return pending;
        }

        BankTransferDTO completed =
                processor.process(
                        request,
                        userId,
                        receiverId
                );

        events.publishEvent(
                new FdsTransferCompletedEvent(
                        completed.getTransferId(),
                        completed.getFromAccountId(),
                        completed.getToAccountId(),
                        completed.getAmount(),
                        completed.getCompletedAt()
                )
        );

        return completed;
    }
}
