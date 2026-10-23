package org.teamsai.saimockbank.domain.fds.dto.request;

import jakarta.validation.constraints.Size;

public record FdsReviewDecisionRequest(
        @Size(max = 255)
        String reason
) {
}
