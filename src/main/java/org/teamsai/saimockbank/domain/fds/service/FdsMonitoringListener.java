package org.teamsai.saimockbank.domain.fds.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.teamsai.saimockbank.domain.fds.dto.FdsTransferCompletedEvent;

@Slf4j
@Component
@RequiredArgsConstructor
public class FdsMonitoringListener {

    private final FdsMonitoringService monitoringService;

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    public void onTransferCompleted(
            FdsTransferCompletedEvent event
    ) {

        try {
            monitoringService.evaluateCompletedTransfer(event);

        } catch (Exception exception) {

            log.error(
                    "FDS 사후 평가 실패. transferId={}",
                    event.transferId(),
                    exception
            );
        }
    }
}