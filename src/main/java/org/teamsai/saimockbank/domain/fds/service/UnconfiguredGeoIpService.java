package org.teamsai.saimockbank.domain.fds.service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.teamsai.saimockbank.domain.fds.dto.GeoIpResolution;

@Service
@Profile("!dev & !test")
public class UnconfiguredGeoIpService implements GeoIpService {

    @Override
    public GeoIpResolution resolve(String ipAddress) {
        String normalizedIp =
                ipAddress == null || ipAddress.isBlank()
                        ? "UNKNOWN"
                        : ipAddress.trim();

        return new GeoIpResolution(
                normalizedIp,
                "UNKNOWN",
                false
        );
    }
}
