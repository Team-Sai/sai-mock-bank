package org.teamsai.saimockbank.domain.fds.dto;

public record FdsTransferHeldEvent(
        Long transferId,
        FdsContext context
) {
}
