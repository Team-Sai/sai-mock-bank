package org.teamsai.saimockbank.domain.fds.service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.teamsai.saimockbank.domain.fds.dto.GeoIpResolution;

@Service
@Profile("dev")
public class DevGeoIpService implements GeoIpService {

    @Override
    public GeoIpResolution resolve(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return new GeoIpResolution("UNKNOWN", "UNKNOWN", false);
        }

        return switch (ipAddress) {
            case "127.0.0.1", "::1", "0:0:0:0:0:0:0:1" ->
                    new GeoIpResolution(ipAddress, "KR", false);
            case "203.0.113.10" ->
                    new GeoIpResolution(ipAddress, "US", true);
            case "198.51.100.20" ->
                    new GeoIpResolution(ipAddress, "JP", true);
            default ->
                    new GeoIpResolution(ipAddress, "UNKNOWN", false);
        };
    }
}