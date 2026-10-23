(() => {
    "use strict";

    const STREAM_URL = "/api/fds/notifications/stream";
    const STORAGE_PREFIX = "fdsReviewNotifications";
    const LEGACY_STORAGE_KEY = "fdsReviewNotifications";

    const MAX_NOTIFICATIONS = 20;
    const RECONNECT_DELAY = 3000;
    const TOAST_DURATION = 6000;
    const MAX_SSE_BUFFER = 65536;

    const $ = (id) => document.getElementById(id);

    let controller = null;
    let connectedToken = null;
    let reconnectTimer = null;
    let toastTimer = null;
    let hideTimer = null;
    let stopped = false;

    // =========================================
    // JWT 및 사용자 식별
    // =========================================

    function getToken() {
        return sessionStorage.getItem("accessToken");
    }

    function getUserIdFromToken(token) {
        if (!token) return null;

        try {
            const payload = token.split(".")[1];
            if (!payload) return null;

            const base64 = payload
                .replace(/-/g, "+")
                .replace(/_/g, "/");

            const padded = base64.padEnd(
                Math.ceil(base64.length / 4) * 4,
                "="
            );

            const bytes = Uint8Array.from(
                atob(padded),
                (char) => char.charCodeAt(0)
            );

            const decoded = JSON.parse(
                new TextDecoder("utf-8").decode(bytes)
            );

            if (decoded.sub === undefined ||
                decoded.sub === null) {
                return null;
            }

            const userId = String(decoded.sub).trim();
            return userId || null;

        } catch (error) {
            console.warn(
                "[FDS Notification] JWT 사용자 식별 실패",
                error
            );
            return null;
        }
    }

    function getStorageKey(token = getToken()) {
        const userId = getUserIdFromToken(token);

        if (!userId) return null;

        return STORAGE_PREFIX + ":" + userId;
    }

    // =========================================
    // 알림 저장 및 조회
    // =========================================

    function readNotifications() {
        const key = getStorageKey();
        if (!key) return [];

        try {
            const raw = sessionStorage.getItem(key);
            if (!raw) return [];

            const parsed = JSON.parse(raw);

            return Array.isArray(parsed) ? parsed : [];

        } catch (error) {
            console.warn(
                "[FDS Notification] 알림 조회 실패",
                error
            );
            return [];
        }
    }

    function saveNotifications(items) {
        const key = getStorageKey();
        if (!key) return;

        try {
            sessionStorage.setItem(
                key,
                JSON.stringify(
                    items.slice(0, MAX_NOTIFICATIONS)
                )
            );

        } catch (error) {
            console.warn(
                "[FDS Notification] 알림 저장 실패",
                error
            );
        }
    }

    function clearNotifications() {
        saveNotifications([]);
        renderNotificationList();
    }

    function formatWon(amount) {
        return Number(amount || 0)
            .toLocaleString("ko-KR") + "원";
    }

    function detailUrl(transferId) {
        return "/transfer-history?transferId=" +
            encodeURIComponent(transferId);
    }

    function makeNotification(data, eventId) {
        const approved = data.status === "SUCCESS";

        return {
            id: eventId ||
                String(data.transferId) + "-" + data.status,

            transferId: data.transferId,
            amount: data.amount,
            status: data.status,

            title: approved
                ? "이체가 승인되었습니다"
                : "이체 요청이 거절되었습니다",

            message: approved
                ? formatWon(data.amount) +
                " 이체가 승인되어 정상 처리되었습니다."
                : formatWon(data.amount) +
                " 이체가 거절되었습니다. 출금은 진행되지 않았습니다.",

            createdAt: new Date().toISOString(),
            read: false
        };
    }

    function handleNotification(data, eventId, sourceToken) {
        // 이전 계정의 SSE 이벤트는 처리하지 않음
        if (!sourceToken || sourceToken !== getToken()) {
            return;
        }

        if (!data ||
            data.transferId == null ||
            !["SUCCESS", "REJECTED"].includes(data.status)) {
            return;
        }

        const item = makeNotification(data, eventId);
        const items = readNotifications();

        // 중복 이벤트 방지
        if (items.some((saved) => saved.id === item.id)) {
            return;
        }

        items.unshift(item);
        saveNotifications(items);

        renderNotificationList();
        showToast(item);
    }

    function markAsRead(id) {
        const items = readNotifications();

        for (const item of items) {
            if (item.id === id) {
                item.read = true;
            }
        }

        saveNotifications(items);
        renderNotificationList();
    }

    // =========================================
    // 상단 토스트 알림
    // =========================================

    function showToast(item) {
        const toast = $("fdsToast");

        if (!toast) return;

        clearTimeout(toastTimer);
        clearTimeout(hideTimer);

        $("fdsToastTitle").textContent = item.title;
        $("fdsToastMessage").textContent = item.message;

        $("fdsToastIcon").textContent =
            item.status === "SUCCESS" ? "✓" : "!";

        toast.classList.toggle(
            "fds-toast--rejected",
            item.status === "REJECTED"
        );

        toast.dataset.transferId = String(item.transferId);
        toast.dataset.notificationId = item.id;

        toast.hidden = false;
        toast.classList.remove("fds-toast--visible");

        requestAnimationFrame(() => {
            requestAnimationFrame(() => {
                if (!toast.hidden) {
                    toast.classList.add("fds-toast--visible");
                }
            });
        });

        toastTimer = setTimeout(
            hideToast,
            TOAST_DURATION
        );
    }

    function hideToast() {
        const toast = $("fdsToast");

        if (!toast) return;

        clearTimeout(toastTimer);
        clearTimeout(hideTimer);

        toast.classList.remove("fds-toast--visible");

        hideTimer = setTimeout(() => {
            if (!toast.classList.contains("fds-toast--visible")) {
                toast.hidden = true;
            }
        }, 300);
    }

    // =========================================
    // 종 아이콘 및 알림 목록
    // =========================================

    function renderNotificationList() {
        const list = $("fdsNotificationList");
        const dot = $("fdsNotificationDot");

        if (!list) return;

        const items = readNotifications();

        if (dot) {
            dot.hidden = !items.some((item) => !item.read);
        }

        list.replaceChildren();

        if (items.length === 0) {
            const empty = document.createElement("p");
            empty.className = "fds-notification-empty";
            empty.textContent =
                "새로운 심사 결과 알림이 없습니다.";

            list.appendChild(empty);
            return;
        }

        for (const item of items) {
            const button = document.createElement("button");
            button.type = "button";
            button.className = "fds-notification-item";

            if (!item.read) {
                button.classList.add("is-unread");
            }

            const icon = document.createElement("span");
            icon.className = "fds-notification-item__icon";

            icon.textContent =
                item.status === "SUCCESS" ? "✓" : "!";

            if (item.status === "REJECTED") {
                icon.classList.add("is-rejected");
            }

            const content = document.createElement("span");
            content.className =
                "fds-notification-item__content";

            const title = document.createElement("strong");
            title.textContent = item.title;

            const message = document.createElement("span");
            message.textContent = item.message;

            const date = document.createElement("small");
            const parsedDate = new Date(item.createdAt);

            date.textContent = Number.isNaN(parsedDate.getTime())
                ? ""
                : parsedDate.toLocaleString("ko-KR");

            content.append(title, message, date);
            button.append(icon, content);

            button.addEventListener("click", () => {
                markAsRead(item.id);
                closePanel();

                window.location.href =
                    detailUrl(item.transferId);
            });

            list.appendChild(button);
        }
    }

    function openPanel() {
        const panel = $("fdsNotificationPanel");
        const button = $("fdsNotificationButton");

        if (!panel || !button) return;

        renderNotificationList();

        panel.hidden = false;
        button.setAttribute("aria-expanded", "true");
    }

    function closePanel() {
        const panel = $("fdsNotificationPanel");
        const button = $("fdsNotificationButton");

        if (panel) {
            panel.hidden = true;
        }

        if (button) {
            button.setAttribute("aria-expanded", "false");
        }
    }

    function togglePanel() {
        const panel = $("fdsNotificationPanel");

        if (!panel) return;

        if (panel.hidden) {
            openPanel();
        } else {
            closePanel();
        }
    }

    // =========================================
    // SSE 이벤트 파싱
    // =========================================

    function parseSseBlock(block, sourceToken) {
        let eventType = "message";
        let eventId = "";
        const dataLines = [];

        for (const line of block.split("\n")) {
            if (!line || line.startsWith(":")) continue;

            const colon = line.indexOf(":");

            const name = colon < 0
                ? line
                : line.slice(0, colon);

            let value = colon < 0
                ? ""
                : line.slice(colon + 1);

            if (value.startsWith(" ")) {
                value = value.slice(1);
            }

            if (name === "event") {
                eventType = value;
            } else if (name === "id") {
                eventId = value;
            } else if (name === "data") {
                dataLines.push(value);
            }
        }

        if (eventType !== "fds-review" ||
            dataLines.length === 0) {
            return;
        }

        try {
            const data = JSON.parse(dataLines.join("\n"));

            handleNotification(
                data,
                eventId,
                sourceToken
            );

        } catch (error) {
            console.warn(
                "[FDS Notification] SSE 이벤트 파싱 실패",
                error
            );
        }
    }

    // =========================================
    // SSE 연결
    // =========================================

    async function connect() {
        if (stopped || controller) return;

        const token = getToken();

        if (!token) {
            scheduleReconnect();
            return;
        }

        const currentController = new AbortController();

        controller = currentController;
        connectedToken = token;

        try {
            const response = await fetch(STREAM_URL, {
                method: "GET",
                headers: {
                    Authorization: "Bearer " + token,
                    Accept: "text/event-stream"
                },
                cache: "no-store",
                signal: currentController.signal
            });

            if (response.status === 401 ||
                response.status === 403) {
                console.warn(
                    "[FDS Notification] SSE 인증 실패"
                );
                stopped = true;
                return;
            }

            if (!response.ok || !response.body) {
                throw new Error(
                    "SSE 연결 실패: HTTP " + response.status
                );
            }

            const reader = response.body.getReader();
            const decoder = new TextDecoder("utf-8");

            let buffer = "";

            while (!stopped) {
                if (getToken() !== token) {
                    currentController.abort();
                    break;
                }

                const {value, done} = await reader.read();

                if (done) break;

                buffer += decoder.decode(
                    value,
                    {stream: true}
                );

                // SSE 줄바꿈 정규화
                buffer = buffer
                    .replace(/\r\n/g, "\n")
                    .replace(/\r/g, "\n");

                let boundary;

                while (
                    (boundary = buffer.indexOf("\n\n")) !== -1
                    ) {
                    const block = buffer.slice(0, boundary);
                    buffer = buffer.slice(boundary + 2);

                    parseSseBlock(block, token);
                }

                if (buffer.length > MAX_SSE_BUFFER) {
                    throw new Error(
                        "SSE 데이터 크기 초과"
                    );
                }
            }

        } catch (error) {
            if (!stopped &&
                error.name !== "AbortError") {
                console.warn(
                    "[FDS Notification] SSE 연결 종료",
                    error
                );
            }

        } finally {
            if (controller === currentController) {
                controller = null;
                connectedToken = null;
            }

            if (!stopped) {
                scheduleReconnect();
            }
        }
    }

    function scheduleReconnect() {
        if (stopped || reconnectTimer) return;

        reconnectTimer = setTimeout(() => {
            reconnectTimer = null;
            connect();
        }, RECONNECT_DELAY);
    }

    // =========================================
    // 계정 변경 및 연결 종료
    // =========================================

    function checkAccountChange() {
        const token = getToken();

        if (connectedToken &&
            connectedToken !== token) {
            controller?.abort();

            hideToast();
            closePanel();
            renderNotificationList();
        }

        if (token &&
            !controller &&
            !reconnectTimer &&
            !stopped) {
            connect();
        }
    }

    function stopNotificationStream() {
        stopped = true;

        clearTimeout(reconnectTimer);
        clearTimeout(toastTimer);
        clearTimeout(hideTimer);

        reconnectTimer = null;

        controller?.abort();
        controller = null;
        connectedToken = null;

        const toast = $("fdsToast");

        if (toast) {
            toast.hidden = true;
            toast.classList.remove("fds-toast--visible");
        }

        closePanel();
    }

    // 로그아웃 시 사용할 수 있는 종료 함수
    window.stopFdsNotificationStream = stopNotificationStream;

    // =========================================
    // 이벤트 등록
    // =========================================

    function initEvents() {
        const button = $("fdsNotificationButton");
        const panel = $("fdsNotificationPanel");
        const toast = $("fdsToast");

        if (!button || !panel) {
            console.error(
                "[FDS Notification] 알림 UI 요소 누락"
            );
            return;
        }

        // 헤더 레이아웃의 overflow 영향을 방지
        if (panel.parentElement !== document.body) {
            document.body.appendChild(panel);
        }

        if (toast &&
            toast.parentElement !== document.body) {
            document.body.appendChild(toast);
        }

        button.addEventListener("click", (event) => {
            event.preventDefault();
            event.stopPropagation();

            togglePanel();
        });

        // 알림함 외부 클릭 시 닫기
        document.addEventListener("click", (event) => {
            if (panel.hidden) return;

            if (!panel.contains(event.target) &&
                !button.contains(event.target)) {
                closePanel();
            }
        });

        // ESC 키로 알림함 닫기
        document.addEventListener("keydown", (event) => {
            if (event.key === "Escape") {
                closePanel();
            }
        });

        $("fdsNotificationClear")?.addEventListener(
            "click",
            (event) => {
                event.preventDefault();
                clearNotifications();
            }
        );

        $("fdsToastClose")?.addEventListener(
            "click",
            (event) => {
                event.preventDefault();
                event.stopPropagation();

                hideToast();
            }
        );

        toast?.addEventListener("click", (event) => {
            if (event.target.closest("#fdsToastClose")) {
                return;
            }

            const transferId = toast.dataset.transferId;
            const notificationId =
                toast.dataset.notificationId;

            if (!transferId) return;

            if (notificationId) {
                markAsRead(notificationId);
            }

            window.location.href = detailUrl(transferId);
        });

        document.addEventListener(
            "visibilitychange",
            () => {
                if (!document.hidden) {
                    checkAccountChange();
                }
            }
        );

        window.addEventListener(
            "focus",
            checkAccountChange
        );
    }

    // =========================================
    // 초기화
    // =========================================

    function init() {
        // 과거 공통 저장 키 제거
        sessionStorage.removeItem(LEGACY_STORAGE_KEY);

        if (!$("fdsNotificationButton")) return;

        initEvents();
        renderNotificationList();
        closePanel();

        connect();
    }

    // 페이지를 떠날 때 연결 종료
    window.addEventListener("pagehide", () => {
        stopNotificationStream();
    });

    if (document.readyState === "loading") {
        document.addEventListener(
            "DOMContentLoaded",
            init
        );
    } else {
        init();
    }
})();