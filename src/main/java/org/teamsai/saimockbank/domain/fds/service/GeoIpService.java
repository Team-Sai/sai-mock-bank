package org.teamsai.saimockbank.domain.fds.service;

import org.teamsai.saimockbank.domain.fds.dto.GeoIpResolution;

public interface GeoIpService {
    GeoIpResolution resolve(String ipAddress);
}