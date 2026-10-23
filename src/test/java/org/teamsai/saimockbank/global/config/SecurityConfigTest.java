package org.teamsai.saimockbank.global.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.test.util.ReflectionTestUtils;
import org.teamsai.saimockbank.global.jwt.JwtAuthenticationEntryPoint;
import org.teamsai.saimockbank.global.jwt.JwtAuthenticationFilter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SecurityConfigTest {

    private SecurityConfig securityConfig;

    @BeforeEach
    void setUp() {
        securityConfig = new SecurityConfig(
                mock(JwtAuthenticationFilter.class),
                mock(JwtAuthenticationEntryPoint.class)
        );
        ReflectionTestUtils.setField(securityConfig, "managementServerPort", 8082);
    }

    @Test
    void permitsOnlyHealthAndPrometheusOnManagementPort() {
        assertThat(isPublicManagementEndpoint(8082, "/actuator/health")).isTrue();
        assertThat(isPublicManagementEndpoint(8082, "/actuator/prometheus")).isTrue();
        assertThat(isPublicManagementEndpoint(8081, "/actuator/health")).isFalse();
        assertThat(isPublicManagementEndpoint(8081, "/actuator/prometheus")).isFalse();
        assertThat(isPublicManagementEndpoint(8082, "/actuator/env")).isFalse();
    }

    @Test
    void actuatorPermitAllChainIsLoadtestOnly() throws NoSuchMethodException {
        var method = SecurityConfig.class.getMethod(
                "actuatorSecurityFilterChain",
                HttpSecurity.class
        );
        Profile profile = method.getAnnotation(Profile.class);

        assertThat(profile).isNotNull();
        assertThat(profile.value()).contains("loadtest");
    }

    private boolean isPublicManagementEndpoint(int port, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setLocalPort(port);
        request.setRequestURI(path);
        return securityConfig.isPublicManagementEndpoint(request);
    }
}
