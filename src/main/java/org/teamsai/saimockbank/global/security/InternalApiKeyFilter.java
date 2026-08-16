package org.teamsai.saimockbank.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class InternalApiKeyFilter extends OncePerRequestFilter {

    @Value("${link-callback.api-key}")
    private String expectedApiKey;

    private static final String HEADER_NAME = "X-Internal-Api-Key";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String providedKey = request.getHeader(HEADER_NAME);
        if (isValidKey(providedKey)) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    private boolean isValidKey(String providedKey) {
        if (providedKey == null) {
            return false;
        }
        byte[] expectedBytes = expectedApiKey.getBytes(StandardCharsets.UTF_8);
        byte[] providedBytes = providedKey.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedBytes, providedBytes);
    }
}