
package org.teamsai.saimockbank.domain.fds.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.teamsai.saimockbank.domain.fds.dto.FdsTransferHeldEvent;

@Slf4j
@Component
@RequiredArgsConstructor
public class FdsHoldListener {

    private final FdsHoldPersistenceService persistenceService;

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    public void onHeld(
            FdsTransferHeldEvent event
    ) {
        try {
            persistenceService.save(event);

        } catch (Exception exception) {
            log.error(
                    "보류 거래 FDS 평가 저장 실패. transferId={}",
                    event.transferId(),
                    exception
            );
        }
    }
}
