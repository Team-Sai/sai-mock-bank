package org.teamsai.saimockbank.global.config;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.teamsai.saimockbank.global.jwt.JwtAuthenticationEntryPoint;
import org.teamsai.saimockbank.global.jwt.JwtAuthenticationFilter;
import org.teamsai.saimockbank.global.security.InternalApiKeyFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    @Value("${management.server.port:8082}")
    private int managementServerPort;

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @Bean
    @Order(0)
    @Profile("loadtest")
    public SecurityFilterChain actuatorSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher(this::isPublicManagementEndpoint)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm ->
                        sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                );

        return http.build();
    }

    boolean isPublicManagementEndpoint(HttpServletRequest request) {
        if (request.getLocalPort() != managementServerPort) {
            return false;
        }

        String path = request.getRequestURI();
        return "/actuator/health".equals(path) || "/actuator/prometheus".equals(path);
    }

    @Bean
    public FilterRegistrationBean<InternalApiKeyFilter> internalApiKeyFilterRegistration(
            InternalApiKeyFilter filter
    ) {
        FilterRegistrationBean<InternalApiKeyFilter> registration =
                new FilterRegistrationBean<>(filter);

        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilterRegistration(
            JwtAuthenticationFilter filter
    ) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration =
                new FilterRegistrationBean<>(filter);

        registration.setEnabled(false);
        return registration;
    }

    @Bean
    @Order(1)
    public SecurityFilterChain internalApiFilterChain(
            HttpSecurity http,
            InternalApiKeyFilter internalApiKeyFilter
    ) throws Exception {

        http
                .securityMatcher(
                        "/api/link/confirm-key",
                        "/api/link/revoke-key",
                        "/api/link/recover-key",
                        "/api/mock-bank/link"
                )
                .authorizeHttpRequests(auth ->
                        auth.anyRequest().permitAll()
                )
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm ->
                        sm.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )
                .addFilterBefore(
                        internalApiKeyFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http
    ) throws Exception {

        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)

                .sessionManagement(sm ->
                        sm.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                // 인증 실패 및 관리자 페이지 접근 거부 처리
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(
                                (request, response, exception) -> {
                                    String path = request.getServletPath();

                                    if (isAdminPage(path)) {
                                        response.sendRedirect("/admin-denied");
                                        return;
                                    }

                                    jwtAuthenticationEntryPoint.commence(
                                            request,
                                            response,
                                            exception
                                    );
                                }
                        )
                        .accessDeniedHandler(
                                (request, response, exception) -> {
                                    String path = request.getServletPath();

                                    if (isAdminPage(path)) {
                                        response.sendRedirect("/admin-denied");
                                        return;
                                    }

                                    response.sendError(
                                            HttpServletResponse.SC_FORBIDDEN
                                    );
                                }
                        )
                )

                .authorizeHttpRequests(auth ->
                        auth.dispatcherTypeMatchers(
                                        jakarta.servlet.DispatcherType.ASYNC
                                )
                                .permitAll()

                                // FDS 관리자 API
                                .requestMatchers(
                                        "/api/fds/admin/**",
                                        "/api/fds/evaluations",
                                        "/api/fds/evaluations/**"
                                )
                                .hasRole("ADMIN")

                                // FDS 관리자 페이지
                                .requestMatchers(
                                        "/admin",
                                        "/admin/",
                                        "/admin/**"
                                )
                                .hasRole("ADMIN")

                                // 접근 권한 없음 페이지
                                .requestMatchers("/admin-denied")
                                .permitAll()

                                // 계좌 생성
                                .requestMatchers(
                                        HttpMethod.POST,
                                        "/api/mock-bank/accounts"
                                )
                                .authenticated()

                                // 인증이 필요한 API
                                .requestMatchers(
                                        "/api/mock-bank/transfers/**",
                                        "/api/mock-bank/customer-accounts/**",
                                        "/api/bank-user/**",
                                        "/api/mock-bank/accounts/my/**"
                                )
                                .authenticated()

                                // Swagger
                                .requestMatchers(
                                        "/swagger-ui/**",
                                        "/swagger-ui.html",
                                        "/v3/api-docs/**",
                                        "/v3/api-docs.yaml"
                                )
                                .permitAll()

                                // 인증 API 및 공개 Mock Bank API
                                .requestMatchers(
                                        "/api/auth/signup",
                                        "/api/auth/login",
                                        "/api/mock-bank/**"
                                )
                                .permitAll()

                                // 기존 SSR 화면
                                .requestMatchers(
                                        "/",
                                        "/login",
                                        "/signup",
                                        "/home",
                                        "/link/start",
                                        "/link/select",
                                        "/transfer",
                                        "/transfer-history",
                                        "/link/identity-mismatch",
                                        "/transactions",
                                        "/link-invalid"
                                )
                                .permitAll()

                                // 정적 리소스
                                .requestMatchers(
                                        "/css/**",
                                        "/js/**",
                                        "/img/**",
                                        "/favicon.ico",
                                        "/error"
                                )
                                .permitAll()

                                .anyRequest()
                                .authenticated()
                )

                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }
    private static boolean isAdminPage(String path) {
        return "/admin".equals(path)
                || "/admin/".equals(path)
                || path.startsWith("/admin/");
    }
}