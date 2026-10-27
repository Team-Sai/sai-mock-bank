package org.teamsai.saimockbank.domain.fds.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class ClientIpExtractor {

    public String extract(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}