
package org.teamsai.saimockbank.domain.transfer.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saimockbank.domain.account.mapper.BankAccountMapper;
import org.teamsai.saimockbank.domain.fds.dto.FdsTransferCompletedEvent;
import org.teamsai.saimockbank.domain.fds.service.FdsPreTransferDecisionService;
import org.teamsai.saimockbank.domain.fds.service.FdsService;
import org.teamsai.saimockbank.domain.transfer.dto.BankTransferDTO;
import org.teamsai.saimockbank.domain.transfer.dto.TransferRequest;
import org.teamsai.saimockbank.domain.transfer.exception.TransferErrorCode;
import org.teamsai.saimockbank.domain.transfer.mapper.BankTransferMapper;

@Service
@RequiredArgsConstructor
public class FdsTransferCommandService {

    private final BankTransferMapper transferMapper;
    private final BankAccountMapper accountMapper;
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
        BankTransferDTO existing =
                transferMapper.findByRequestKey(
                        request.requestKey()
                ).orElse(null);

        if (existing != null) {
            boolean owner = accountMapper
                    .findById(existing.getFromAccountId())
                    .map(account ->
                            account.getBankUserId().equals(userId)
                    )
                    .orElse(false);

            if (!owner) {
                throw TransferErrorCode.ACCOUNT_ACCESS_DENIED
                        .toException();
            }

            if (!existing.getFromAccountId()
                    .equals(request.fromAccountId())
                    || !existing.getToAccountId().equals(receiverId)
                    || existing.getAmount()
                    .compareTo(request.amount()) != 0) {

                throw TransferErrorCode.DUPLICATE_REQUEST_KEY
                        .toException();
            }

            return existing;
        }

        if (decision.requiresReview()) {
            BankTransferDTO pending =
                    processor.processHold(
                            request,
                            userId,
                            receiverId
                    );

            fdsService.evaluate(
                    pending.getTransferId(),
                    decision.context()
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
