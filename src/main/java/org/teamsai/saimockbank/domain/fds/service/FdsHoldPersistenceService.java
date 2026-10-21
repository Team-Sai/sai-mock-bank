package org.teamsai.saimockbank.domain.fds.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saimockbank.domain.fds.dto.FdsTransferHeldEvent;

@Service
@RequiredArgsConstructor
public class FdsHoldPersistenceService {

    private final FdsService fdsService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(FdsTransferHeldEvent event) {
        fdsService.evaluate(
                event.transferId(),
                event.context()
        );
    }
}
