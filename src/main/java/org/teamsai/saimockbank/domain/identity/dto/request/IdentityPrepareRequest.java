package org.teamsai.saimockbank.domain.identity.dto.request;

import jakarta.validation.constraints.NotNull;
import org.teamsai.saimockbank.domain.identity.type.IdentityPurpose;

public record IdentityPrepareRequest(
        @NotNull(message = "본인인증 목적은 필수입니다.")
        IdentityPurpose purpose
) {
}