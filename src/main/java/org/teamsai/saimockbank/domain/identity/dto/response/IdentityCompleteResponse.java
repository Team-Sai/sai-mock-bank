package org.teamsai.saimockbank.domain.identity.dto.response;

import org.teamsai.saimockbank.domain.identity.type.IdentityStatus;

import java.time.LocalDateTime;

public record IdentityCompleteResponse(
        String identityVerificationId,
        IdentityStatus status,
        LocalDateTime verifiedAt,
        LocalDateTime expiresAt
) {
}
