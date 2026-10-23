/**
 * 사이뱅크 FDS 관리자 페이지
 * 기존 JWT 로그인 / Spring Boot API 사용
 */

(() => {
    "use strict";

    const API = {
        evaluations: "/api/fds/evaluations",
        reviews: "/api/fds/admin/reviews"
    };

    const PAGE_SIZE = 20;

    const state = {
        view: "dashboard",
        reviewPage: 0,
        evaluationPage: 0,
        transactionId: null,
        selectedReviewId: null,
        actionRunning: false,
        reviewRequest: 0,
        evaluationRequest: 0
    };

    const $ = id => document.getElementById(id);

    function getToken() {
        return sessionStorage.getItem("accessToken");
    }

    // 서버에서 받은 데이터를 HTML에 넣기 전 이스케이프 처리
    function escapeHtml(value) {
        return String(value ?? "")
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#39;");
    }

    async function apiFetch(url, options = {}) {
        const token = getToken();

        if (!token) {
            window.location.replace("/login?required=true");
            throw new Error("로그인이 필요합니다.");
        }

        const response = await fetch(url, {
            ...options,
            cache: "no-store",
            headers: {
                Accept: "application/json",
                Authorization: "Bearer " + token,
                ...(options.body
                    ? {"Content-Type": "application/json"}
                    : {}),
                ...(options.headers || {})
            }
        });

        if (response.status === 401) {
            sessionStorage.removeItem("accessToken");
            window.location.replace("/login?required=true");
            throw new Error("로그인이 만료되었습니다.");
        }

        if (response.status === 403) {
            throw new Error("관리자 권한이 없는 계정입니다.");
        }

        const text = await response.text();
        let data = null;

        if (text) {
            try {
                data = JSON.parse(text);
            } catch {
                throw new Error("서버 응답 형식이 올바르지 않습니다.");
            }
        }

        if (!response.ok) {
            throw new Error(
                data?.message ||
                data?.detail ||
                "요청 처리에 실패했습니다. (" + response.status + ")"
            );
        }

        return data;
    }

    function number(value) {
        return new Intl.NumberFormat("ko-KR").format(Number(value) || 0);
    }

    function money(value) {
        return number(value) + "원";
    }

    function dateTime(value) {
        if (!value) return "-";
        const date = new Date(value);

        if (Number.isNaN(date.getTime())) {
            return String(value).replace("T", " ");
        }

        return new Intl.DateTimeFormat("ko-KR", {
            year: "numeric",
            month: "2-digit",
            day: "2-digit",
            hour: "2-digit",
            minute: "2-digit",
            hour12: false
        }).format(date);
    }

    function badge(value) {
        const raw = String(value ?? "UNKNOWN");
        const known = [
            "HIGH", "MEDIUM", "LOW",
            "BLOCK", "MONITOR", "ALLOW",
            "PENDING_REVIEW", "PENDING",
            "SUCCESS", "REJECTED", "FAILED"
        ];

        const label = {
            HIGH: "높음",
            MEDIUM: "중간",
            LOW: "낮음",
            BLOCK: "차단",
            MONITOR: "모니터링",
            ALLOW: "허용",
            PENDING_REVIEW: "심사 대기",
            PENDING: "처리 대기",
            SUCCESS: "완료",
            REJECTED: "거절",
            FAILED: "실패"
        }[raw] || raw;

        const colorClass = known.includes(raw)
            ? raw.toLowerCase()
            : "unknown";

        return '<span class="badge badge-' + colorClass + '">' +
            escapeHtml(label) + "</span>";
    }

    function emptyRow(columns, message) {
        return '<tr><td class="empty-row" colspan="' + columns +
            '">' + escapeHtml(message) + "</td></tr>";
    }

    function notify(message) {
        const toast = $("toast");
        toast.textContent = message;
        toast.hidden = false;

        clearTimeout(notify.timer);
        notify.timer = setTimeout(() => {
            toast.hidden = true;
        }, 3500);
    }

    function showNotice(message) {
        const notice = $("notice");
        notice.textContent = message;
        notice.hidden = false;
    }

    function clearNotice() {
        $("notice").hidden = true;
    }

    function handleError(error) {
        console.error("[FDS Admin]", error);
        showNotice(error.message || "요청에 실패했습니다.");
    }

    function showView(view) {
        state.view = view;
        clearNotice();

        document.querySelectorAll(".view").forEach(section => {
            section.hidden = section.id !== "view-" + view;
        });

        document.querySelectorAll(".nav-item").forEach(button => {
            button.classList.toggle(
                "active",
                button.dataset.view === view
            );
        });

        if (view === "dashboard") loadDashboard();
        if (view === "reviews") loadReviews();
        if (view === "evaluations") loadEvaluations();
    }

    // -----------------------------------------
    // Dashboard
    // -----------------------------------------

    async function loadDashboard() {
        $("recentEvaluationBody").innerHTML =
            emptyRow(7, "평가 이력을 불러오는 중입니다.");

        try {
            // 현재 평가 API는 목록 전체를 Page로 반환함.
            // 최근 최대 100건을 기준으로 위험 분포 계산.
            const [evaluations, reviews] = await Promise.all([
                apiFetch(API.evaluations + "?page=0&size=100"),
                apiFetch(API.reviews + "?page=0&size=1")
            ]);

            const items = evaluations.content || [];
            const total = evaluations.totalElements ?? 0;
            const pending = reviews.totalElements ?? 0;

            const counts = {
                HIGH: 0,
                MEDIUM: 0,
                LOW: 0
            };

            items.forEach(item => {
                if (Object.hasOwn(counts, item.riskLevel)) {
                    counts[item.riskLevel]++;
                }
            });

            $("statTotal").textContent = number(total);
            $("statHigh").textContent = number(counts.HIGH);
            $("statMedium").textContent = number(counts.MEDIUM);
            $("statPending").textContent = number(pending);
            $("dashboardPending").textContent = number(pending);
            $("navPendingCount").textContent = number(pending);

            $("countHigh").textContent = number(counts.HIGH);
            $("countMedium").textContent = number(counts.MEDIUM);
            $("countLow").textContent = number(counts.LOW);

            const denominator = Math.max(items.length, 1);

            ["High", "Medium", "Low"].forEach(level => {
                const count = counts[level.toUpperCase()];
                $("bar" + level).style.width =
                    (count / denominator * 100) + "%";
            });

            $("sampleInfo").textContent =
                "최근 " + number(items.length) +
                "건의 평가를 기준으로 산출했습니다. " +
                "(전체 " + number(total) + "건)";

            renderRecentEvaluations(items.slice(0, 5));
            clearNotice();

        } catch (error) {
            handleError(error);
            $("recentEvaluationBody").innerHTML =
                emptyRow(7, "평가 이력을 불러올 수 없습니다.");
        }
    }

    function renderRecentEvaluations(items) {
        const body = $("recentEvaluationBody");

        if (!items.length) {
            body.innerHTML = emptyRow(7, "아직 평가 이력이 없습니다.");
            return;
        }

        body.innerHTML = items.map(item => `
            <tr>
                <td>#${escapeHtml(item.id)}</td>
                <td>${escapeHtml(item.transactionId ?? "-")}</td>
                <td class="amount">${money(item.amount)}</td>
                <td>${escapeHtml(item.riskScore)}점</td>
                <td>${badge(item.riskLevel)}</td>
                <td>${badge(item.action)}</td>
                <td class="muted">${escapeHtml(dateTime(item.evaluatedAt))}</td>
            </tr>
        `).join("");
    }

    // -----------------------------------------
    // Review List
    // -----------------------------------------

    async function loadReviews() {
        const requestId = ++state.reviewRequest;

        $("reviewsBody").innerHTML =
            emptyRow(8, "심사 대기 거래를 불러오는 중입니다.");

        try {
            const page = await apiFetch(
                API.reviews +
                "?page=" + state.reviewPage +
                "&size=" + PAGE_SIZE
            );

            if (requestId !== state.reviewRequest) return;

            renderReviews(page);

            $("reviewsCount").textContent =
                "전체 " + number(page.totalElements) + "건";

            $("navPendingCount").textContent =
                number(page.totalElements);

            updatePagination(
                "review",
                state.reviewPage,
                page.totalPages
            );

            clearNotice();
        } catch (error) {
            if (requestId !== state.reviewRequest) return;
            handleError(error);
            $("reviewsBody").innerHTML =
                emptyRow(8, "목록을 불러올 수 없습니다.");
        }
    }

    function renderReviews(page) {
        const items = page.content || [];
        const body = $("reviewsBody");

        if (!items.length) {
            body.innerHTML =
                emptyRow(8, "심사 대기 중인 거래가 없습니다.");
            return;
        }

        body.innerHTML = items.map(item => `
            <tr>
                <td><strong>#${escapeHtml(item.transferId)}</strong></td>
                <td>${escapeHtml(item.fromAccountId)}</td>
                <td>${escapeHtml(item.toAccountId)}</td>
                <td class="amount">${money(item.amount)}</td>
                <td>${badge(item.riskLevel)}</td>
                <td>${badge(item.status)}</td>
                <td class="muted">${escapeHtml(dateTime(item.createdAt))}</td>
                <td>
                    <button type="button" class="row-button"
                            data-detail="review"
                            data-id="${escapeHtml(item.transferId)}">
                        심사하기
                    </button>
                </td>
            </tr>
        `).join("");
    }

    // -----------------------------------------
    // Evaluation List
    // -----------------------------------------

    async function loadEvaluations() {
        const requestId = ++state.evaluationRequest;

        $("evaluationsBody").innerHTML =
            emptyRow(10, "평가 이력을 불러오는 중입니다.");

        try {
            let url;

            if (state.transactionId !== null) {
                url = API.evaluations +
                    "/transactions/" +
                    encodeURIComponent(state.transactionId);
            } else {
                url = API.evaluations;
            }

            url += "?page=" + state.evaluationPage +
                "&size=" + PAGE_SIZE;

            const page = await apiFetch(url);

            if (requestId !== state.evaluationRequest) return;

            renderEvaluations(page);

            $("evaluationsCount").textContent =
                "전체 " + number(page.totalElements) + "건";

            updatePagination(
                "evaluation",
                state.evaluationPage,
                page.totalPages
            );

            clearNotice();
        } catch (error) {
            if (requestId !== state.evaluationRequest) return;
            handleError(error);
            $("evaluationsBody").innerHTML =
                emptyRow(10, "평가 이력을 불러올 수 없습니다.");
        }
    }

    function renderEvaluations(page) {
        const items = page.content || [];
        const body = $("evaluationsBody");

        if (!items.length) {
            body.innerHTML =
                emptyRow(10, "조건에 해당하는 평가 이력이 없습니다.");
            return;
        }

        body.innerHTML = items.map(item => `
            <tr>
                <td>#${escapeHtml(item.id)}</td>
                <td>${escapeHtml(item.transactionId ?? "-")}</td>
                <td>${escapeHtml(item.senderAccountId)}</td>
                <td>${escapeHtml(item.receiverAccountId)}</td>
                <td class="amount">${money(item.amount)}</td>
                <td><strong>${escapeHtml(item.riskScore)}</strong></td>
                <td>${badge(item.riskLevel)}</td>
                <td>${badge(item.action)}</td>
                <td class="muted">${escapeHtml(dateTime(item.evaluatedAt))}</td>
                <td>
                    <button type="button" class="row-button"
                            data-detail="evaluation"
                            data-id="${escapeHtml(item.id)}">
                        상세
                    </button>
                </td>
            </tr>
        `).join("");
    }

    function updatePagination(prefix, currentPage, totalPages) {
        const pages = Math.max(Number(totalPages) || 0, 0);

        $(prefix + "Page").textContent =
            pages === 0
                ? "0 / 0"
                : (currentPage + 1) + " / " + pages;

        $(prefix + "Prev").disabled = currentPage <= 0;
        $(prefix + "Next").disabled =
            pages === 0 || currentPage + 1 >= pages;
    }

    // -----------------------------------------
    // Detail Modal
    // -----------------------------------------

    async function openDetail(type, id) {
        state.selectedReviewId = null;
        clearNotice();

        try {
            const url = type === "review"
                ? API.reviews + "/" + encodeURIComponent(id)
                : API.evaluations + "/" + encodeURIComponent(id);

            const data = await apiFetch(url);

            $("detailType").textContent =
                type === "review" ? "TRANSFER REVIEW" : "RISK EVALUATION";

            $("detailTitle").textContent =
                type === "review"
                    ? "거래 #" + data.transferId
                    : "평가 #" + data.id;

            $("detailStatus").innerHTML =
                badge(data.riskLevel) + " " +
                badge(type === "review" ? data.status : data.action);

            const fields = type === "review"
                ? [
                    ["거래 ID", data.transferId],
                    ["요청 키", data.requestKey],
                    ["보내는 계좌 ID", data.fromAccountId],
                    ["받는 계좌 ID", data.toAccountId],
                    ["거래 금액", money(data.amount)],
                    ["위험 점수", data.riskScore == null
                        ? "-" : data.riskScore + "점"],
                    ["거래 상태", data.status],
                    ["요청 일시", dateTime(data.createdAt)],
                    ["처리 일시", dateTime(data.completedAt)],
                    ["심사자 ID", data.reviewedBy],
                    ["심사 일시", dateTime(data.reviewedAt)],
                    ["기존 심사 사유", data.reviewReason]
                ]
                : [
                    ["평가 ID", data.id],
                    ["거래 ID", data.transactionId],
                    ["보내는 계좌 ID", data.senderAccountId],
                    ["받는 계좌 ID", data.receiverAccountId],
                    ["거래 금액", money(data.amount)],
                    ["위험 점수", data.riskScore + "점"],
                    ["위험 등급", data.riskLevel],
                    ["조치", data.action],
                    ["평가 일시", dateTime(data.evaluatedAt)]
                ];

            $("detailFields").innerHTML = fields.map(([label, value]) => `
                <div>
                    <dt>${escapeHtml(label)}</dt>
                    <dd>${escapeHtml(value ?? "-")}</dd>
                </div>
            `).join("");

            const rules = data.matchedRules || [];
            $("detailRules").innerHTML = rules.length
                ? rules.map(rule =>
                    '<span class="rule-chip">' +
                    escapeHtml(rule) + "</span>"
                ).join("")
                : '<span class="muted">매칭된 규칙 없음</span>';

            const reasons = data.reasons || [];
            $("detailReasons").innerHTML = reasons.length
                ? reasons.map(reason =>
                    "<li>" + escapeHtml(reason) + "</li>"
                ).join("")
                : '<li class="muted">등록된 탐지 사유 없음</li>';

            const actionable =
                type === "review" && data.status === "PENDING_REVIEW";

            $("reviewActionSection").hidden = !actionable;
            $("reviewReason").value = "";

            if (actionable) {
                state.selectedReviewId = data.transferId;
            }

            $("detailDialog").showModal();

        } catch (error) {
            handleError(error);
        }
    }

    // -----------------------------------------
    // Approve / Reject
    // -----------------------------------------

    async function decideReview(decision) {
        if (state.actionRunning || state.selectedReviewId === null) {
            return;
        }

        const reason = $("reviewReason").value.trim();

        if (decision === "reject" && !reason) {
            notify("거절 사유를 입력해 주세요.");
            $("reviewReason").focus();
            return;
        }

        const label = decision === "approve" ? "승인" : "거절";
        const message = decision === "approve"
            ? "거래를 승인하면 실제 송금 처리가 진행됩니다. 승인할까요?"
            : "이 거래를 거절할까요?";

        if (!window.confirm(message)) return;

        state.actionRunning = true;
        $("approveButton").disabled = true;
        $("rejectButton").disabled = true;

        try {
            const transferId = state.selectedReviewId;

            await apiFetch(
                API.reviews + "/" + transferId + "/" + decision,
                {
                    method: "POST",
                    body: JSON.stringify({
                        reason: reason || null
                    })
                }
            );

            $("detailDialog").close();
            state.selectedReviewId = null;

            notify("거래가 " + label + " 처리되었습니다.");

            await Promise.all([
                loadReviews(),
                loadDashboard()
            ]);

        } catch (error) {
            handleError(error);
            notify(error.message || "심사 처리에 실패했습니다.");
        } finally {
            state.actionRunning = false;
            $("approveButton").disabled = false;
            $("rejectButton").disabled = false;
        }
    }

    // -----------------------------------------
    // Event Listeners
    // -----------------------------------------

    function initEvents() {
        document.querySelectorAll("[data-view]").forEach(button => {
            button.addEventListener("click", () => {
                showView(button.dataset.view);
            });
        });

        document.querySelectorAll("[data-go]").forEach(button => {
            button.addEventListener("click", () => {
                showView(button.dataset.go);
            });
        });

        $("refreshButton").addEventListener("click", () => {
            showView(state.view);
        });

        $("reviewPrev").addEventListener("click", () => {
            if (state.reviewPage > 0) {
                state.reviewPage--;
                loadReviews();
            }
        });

        $("reviewNext").addEventListener("click", () => {
            state.reviewPage++;
            loadReviews();
        });

        $("evaluationPrev").addEventListener("click", () => {
            if (state.evaluationPage > 0) {
                state.evaluationPage--;
                loadEvaluations();
            }
        });

        $("evaluationNext").addEventListener("click", () => {
            state.evaluationPage++;
            loadEvaluations();
        });

        $("transactionSearch").addEventListener("submit", event => {
            event.preventDefault();

            const value = $("transactionIdInput").value.trim();

            if (value && !/^[1-9][0-9]*$/.test(value)) {
                notify("올바른 거래 ID를 입력해 주세요.");
                return;
            }

            state.transactionId = value || null;
            state.evaluationPage = 0;
            loadEvaluations();
        });

        $("clearSearch").addEventListener("click", () => {
            $("transactionIdInput").value = "";
            state.transactionId = null;
            state.evaluationPage = 0;
            loadEvaluations();
        });

        document.addEventListener("click", event => {
            const button = event.target.closest("[data-detail]");

            if (button) {
                openDetail(button.dataset.detail, button.dataset.id);
            }
        });

        $("closeDetail").addEventListener("click", () => {
            $("detailDialog").close();
        });

        $("approveButton").addEventListener("click", () => {
            decideReview("approve");
        });

        $("rejectButton").addEventListener("click", () => {
            decideReview("reject");
        });
    }

    async function init() {
        if (!getToken()) {
            window.location.replace("/login?required=true");
            return;
        }

        initEvents();
        showView("dashboard");
    }

    document.addEventListener("DOMContentLoaded", init);
})();