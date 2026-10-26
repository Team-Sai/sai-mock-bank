package org.teamsai.saimockbank.domain.fds.service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.teamsai.saimockbank.domain.fds.dto.GeoIpResolution;

@Service
@Profile("!dev & !test")
public class UnconfiguredGeoIpService implements GeoIpService {

    @Override
    public GeoIpResolution resolve(String ipAddress) {
        throw new IllegalStateException(
                "운영 환경의 GeoIP 국가 판별 기능이 설정되지 않았습니다."
        );
    }
}
