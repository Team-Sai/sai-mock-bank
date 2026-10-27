
package org.teamsai.saimockbank.domain.fds.service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.teamsai.saimockbank.domain.fds.dto.GeoIpResolution;

@Service
@Profile({"dev", "test"})
public class DevGeoIpService implements GeoIpService {

    @Override
    public GeoIpResolution resolve(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return new GeoIpResolution(
                    "UNKNOWN",
                    "UNKNOWN",
                    false
            );
        }

        String ip = ipAddress.trim();

        return switch (ip) {
            case "127.0.0.1", "::1", "0:0:0:0:0:0:0:1" ->
                    new GeoIpResolution(ip, "KR", false);

            case "203.0.113.10" ->
                    new GeoIpResolution(ip, "US", true);

            case "198.51.100.20" ->
                    new GeoIpResolution(ip, "JP", true);

            case "192.0.2.30" ->
                    new GeoIpResolution(ip, "SG", true);

            default ->
                    new GeoIpResolution(ip, "UNKNOWN", false);
        };
    }
}
