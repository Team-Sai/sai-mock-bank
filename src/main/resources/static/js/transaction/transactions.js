/**
 * 전체 거래내역 화면 - 내 모든 계좌의 거래내역을 합쳐서 최신순 10건씩 무한 스크롤
 * transaction-utils.js(TxUtils)가 먼저 로드되어 있어야 합니다.
 *
 * 백엔드 의존:
 *  - GET /api/mock-bank/accounts/my
 *      계좌 목록 + 각 계좌 라벨(은행명/별명/마스킹 계좌번호) 구성용
 *  - GET /api/mock-bank/accounts/my/transactions?beforeTransactionId&size
 *      내 소유 전체 계좌를 합쳐 transaction_id 기준 최신순(DESC)으로 반환.
 *      beforeTransactionId 생략 시 최신 size건, 이후에는 직전 응답의 마지막
 *      (가장 오래된) transactionId를 beforeTransactionId로 전달.
 *      (accountId 단일 계좌용 /my/{accountId}/transactions 와는 별개 엔드포인트)
 */

const CONFIG = {
    ACCOUNTS_ME_URL: "/api/mock-bank/accounts/my",
    TRANSACTIONS_URL: "/api/mock-bank/accounts/my/transactions",
    LOGIN_URL: "/login",
    PAGE_SIZE: 10,
};

const state = {
    accessToken: null,
    accountLabelById: new Map(),
    beforeTransactionId: null, // 다음 페이지 커서. null이면 첫 페이지
    isLoading: false,
    hasMore: true,
    rowIndex: 0, // 짝/홀 배경 스타일용 누적 인덱스
};

document.addEventListener("DOMContentLoaded", () => {
    initHistory();
});

async function initHistory() {
    state.accessToken = sessionStorage.getItem("accessToken");
    if (!state.accessToken) {
        window.location.href = CONFIG.LOGIN_URL;
        return;
    }

    try {
        await loadAccountLabels(state.accessToken);

        clearInitialState();
        await loadNextPage();
        observeSentinel();
    } catch (error) {
        console.error("[transactions] 초기화 실패:", error);
        renderError();
    }
}

/** 계좌 목록을 조회해 accountId -> 표시 라벨 맵 구성 */
async function loadAccountLabels(accessToken) {
    const accounts = await TxUtils.fetchWithAuth(CONFIG.ACCOUNTS_ME_URL, accessToken);
    if (!Array.isArray(accounts)) return;

    accounts.forEach((account) => {
        const label = [account.bankName, account.accountName].filter(Boolean).join(" ");
        const withNumber = account.maskedAccountNumber ? `${label} · ${account.maskedAccountNumber}` : label;
        state.accountLabelById.set(account.accountId, withNumber);
    });
}

/** 다음 페이지(최대 PAGE_SIZE건) 로드 후 append */
async function loadNextPage() {
    if (state.isLoading || !state.hasMore) return;
    state.isLoading = true;

    try {
        const url = buildTransactionsUrl();
        const transactions = await TxUtils.fetchWithAuth(url, state.accessToken);

        if (!Array.isArray(transactions) || transactions.length === 0) {
            state.hasMore = false;
            showEndState();
            return;
        }

        appendTransactions(transactions);

        // 응답이 PAGE_SIZE보다 적게 오면 더 이상 없는 것으로 간주
        if (transactions.length < CONFIG.PAGE_SIZE) {
            state.hasMore = false;
            showEndState();
            return;
        }

        // 다음 커서 = 이번 페이지의 마지막(가장 오래된) transactionId
        state.beforeTransactionId = transactions[transactions.length - 1].transactionId;
    } catch (error) {
        console.error("[transactions] 거래내역 로드 실패:", error);
        renderError();
        state.hasMore = false;
    } finally {
        state.isLoading = false;
    }
}

function buildTransactionsUrl() {
    const params = new URLSearchParams({ size: String(CONFIG.PAGE_SIZE) });
    if (state.beforeTransactionId != null) {
        params.set("beforeTransactionId", String(state.beforeTransactionId));
    }
    return `${CONFIG.TRANSACTIONS_URL}?${params.toString()}`;
}

/** 스크롤 감지: 화면 하단 sentinel이 보이면 다음 페이지 로드 */
function observeSentinel() {
    const sentinel = document.getElementById("scrollSentinel");
    if (!sentinel || !("IntersectionObserver" in window)) return;

    const observer = new IntersectionObserver(
        (entries) => {
            const isVisible = entries.some((entry) => entry.isIntersecting);
            if (isVisible) {
                loadNextPage();
            }
        },
        { rootMargin: "200px" }
    );

    observer.observe(sentinel);
}

function clearInitialState() {
    const container = document.getElementById("transactionsBody");
    if (container) container.innerHTML = "";
}

function appendTransactions(transactions) {
    const container = document.getElementById("transactionsBody");
    if (!container) return;

    const html = transactions
        .map((tx) => {
            const accountLabel = state.accountLabelById.get(tx.accountId);
            const row = TxUtils.renderTransactionRow(tx, state.rowIndex, { accountLabel });
            state.rowIndex += 1;
            return row;
        })
        .join("");

    container.insertAdjacentHTML("beforeend", html);
}

function showEndState() {
    const container = document.getElementById("transactionsBody");
    const endState = document.getElementById("historyEndState");

    if (state.rowIndex === 0 && container) {
        container.innerHTML = `<p class="transactions__state">거래내역이 없습니다.</p>`;
        return;
    }

    if (endState) endState.hidden = false;
}

function renderError() {
    const container = document.getElementById("transactionsBody");
    if (container && state.rowIndex === 0) {
        container.innerHTML = `<p class="transactions__state">거래내역을 불러올 수 없습니다.</p>`;
    }
}