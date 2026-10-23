package org.teamsai.saimockbank.domain.fds.service;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.teamsai.saimockbank.domain.fds.dto.FdsReviewNotificationEvent;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class FdsNotificationService {

    private static final long SSE_TIMEOUT =
            Duration.ofMinutes(30).toMillis();

    private final Map<Long, Map<String, SseEmitter>> connections =
            new ConcurrentHashMap<>();

    private final ScheduledExecutorService heartbeatExecutor =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(
                        runnable,
                        "fds-notification-heartbeat"
                );
                thread.setDaemon(true);
                return thread;
            });

    public FdsNotificationService() {
        heartbeatExecutor.scheduleAtFixedRate(
                this::sendHeartbeats,
                20,
                20,
                TimeUnit.SECONDS
        );
    }

    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        String connectionId = UUID.randomUUID().toString();

        connections
                .computeIfAbsent(
                        userId,
                        key -> new ConcurrentHashMap<>()
                )
                .put(connectionId, emitter);

        emitter.onCompletion(
                () -> remove(userId, connectionId)
        );

        emitter.onTimeout(() -> {
            remove(userId, connectionId);
            emitter.complete();
        });

        emitter.onError(
                error -> remove(userId, connectionId)
        );

        try {
            emitter.send(
                    SseEmitter.event()
                            .name("connected")
                            .data(Map.of("status", "connected"))
            );
        } catch (IOException | IllegalStateException e) {
            remove(userId, connectionId);
            emitter.complete();
        }

        return emitter;
    }


    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    public void onReviewCompleted(
            FdsReviewNotificationEvent event
    ) {
        String status = event.status();

        String title = "SUCCESS".equals(status)
                ? "이체가 승인되었습니다"
                : "이체 요청이 거절되었습니다";

        String message = "SUCCESS".equals(status)
                ? "관리자 심사가 완료되어 이체가 정상 처리되었습니다."
                : "심사 결과 이체가 거절되었습니다. 실제 출금은 진행되지 않았습니다.";

        Map<String, Object> payload = Map.of(
                "transferId", event.transferId(),
                "amount", event.amount(),
                "status", status,
                "title", title,
                "message", message
        );

        Map<String, SseEmitter> userConnections =
                connections.get(event.userId());

        if (userConnections == null || userConnections.isEmpty()) {
            return;
        }

        for (Map.Entry<String, SseEmitter> entry :
                userConnections.entrySet()) {

            try {
                entry.getValue().send(
                        SseEmitter.event()
                                .name("fds-review")
                                .id(event.transferId() + "-" + status)
                                .data(payload)
                );
            } catch (IOException | IllegalStateException e) {
                remove(event.userId(), entry.getKey());
                entry.getValue().complete();
            }
        }
    }

    private void sendHeartbeats() {
        try {
            for (Map.Entry<Long, Map<String, SseEmitter>> user :
                    connections.entrySet()) {

                for (Map.Entry<String, SseEmitter> connection :
                        user.getValue().entrySet()) {

                    try {
                        connection.getValue().send(
                                SseEmitter.event()
                                        .comment("keep-alive")
                        );
                    } catch (IOException | IllegalStateException e) {
                        remove(user.getKey(), connection.getKey());

                        log.debug(
                                "FDS SSE 연결 종료 - userId={}, connectionId={}",
                                user.getKey(),
                                connection.getKey()
                        );
                    }
                }
            }
        } catch (Exception e) {
            log.warn(
                    "FDS 알림 heartbeat 처리 오류 - type={}",
                    e.getClass().getSimpleName()
            );
        }
    }

    private void remove(Long userId, String connectionId) {
        Map<String, SseEmitter> userConnections =
                connections.get(userId);

        if (userConnections == null) {
            return;
        }

        userConnections.remove(connectionId);

        if (userConnections.isEmpty()) {
            connections.remove(userId, userConnections);
        }
    }

    @PreDestroy
    public void shutdown() {
        heartbeatExecutor.shutdownNow();

        for (Map<String, SseEmitter> userConnections :
                connections.values()) {
            for (SseEmitter emitter : userConnections.values()) {
                emitter.complete();
            }
        }

        connections.clear();
    }
}