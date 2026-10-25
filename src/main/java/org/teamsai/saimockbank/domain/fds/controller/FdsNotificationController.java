package org.teamsai.saimockbank.domain.fds.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.teamsai.saimockbank.domain.fds.service.FdsNotificationService;
import org.teamsai.saimockbank.global.security.CustomUserDetails;

@RestController
@RequestMapping("/api/fds/notifications")
@RequiredArgsConstructor
public class FdsNotificationController {

    private final FdsNotificationService notificationService;

    @GetMapping(
            value = "/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public SseEmitter subscribe(
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        return notificationService.subscribe(user.getUserId());
    }
}