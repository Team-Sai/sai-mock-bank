package org.teamsai.saimockbank.domain.fds.dto;

public record GeoIpResolution(
        String ipAddress,
        String countryCode,
        boolean foreignIp
) {
}