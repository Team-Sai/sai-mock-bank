package org.teamsai.saimockbank.domain.fds.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saimockbank.domain.fds.dto.FdsContext;
import org.teamsai.saimockbank.domain.fds.dto.FdsTransferCompletedEvent;

@Slf4j
@Service
@RequiredArgsConstructor
public class FdsMonitoringService {

    private final FdsContextService fdsContextService;
    private final FdsService fdsService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void evaluateCompletedTransfer(
            FdsTransferCompletedEvent event
    ) {
        FdsContext context =
                fdsContextService.createForCompletedTransfer(
                        event.transferId(),
                        event.senderAccountId(),
                        event.receiverAccountId(),
                        event.amount(),
                        event.completedAt()
                );

        fdsService.evaluate(
                event.transferId(),
                context
        );

        log.debug(
                "FDS 평가 완료. transferId={}",
                event.transferId()
        );
    }
}