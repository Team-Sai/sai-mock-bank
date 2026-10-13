package org.teamsai.saimockbank.domain.identity.dto.response;

public record IdentityPrepareResponse(
        String identityVerificationId,
        String storeId,
        String channelKey
) {
}
