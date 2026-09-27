package org.teamsai.saimockbank.domain.user.dto;

public record KeyIssuanceState(
        String activeKeyHash,
        String pendingKeyHash,
        String operationId,
        String rotationKeyHash,
        String keyStatus,
        boolean pendingExpired
) {}
