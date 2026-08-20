/**
 * 홈 화면(잔액 + 최근 거래내역) 데이터 로딩
 *
 * ⚠️ 백엔드 선행 작업 필요
 *  - GET /api/mock-bank/accounts/my
 *      기존 transfer.js 에서 쓰는 것과 동일한 self-service 계좌 목록 조회 (JWT)
 *  - GET /api/mock-bank/accounts/my/{accountId}/transactions
 *      아직 mock-bank에 없음. 기존 BankTransactionController(X-User-Key, 파트너 전용)와는
 *      별개로, JWT(Authorization: Bearer) 인증 self-service 엔드포인트를 새로 추가해야 함.
 *      서비스 로직(BankTransactionService)은 그대로 재사용하고 컨트롤러/인증만 분리하면 됨.
 *
 *  응답 필드명은 sai-backend 쪽 TransactionResponse 기준으로 맞춰뒀습니다.
 *  mock-bank의 실제 TransactionResponse가 다르면 mapTransaction() 만 고치면 됩니다.
 */

const CONFIG = {
    ACCOUNTS_ME_URL: "/api/mock-bank/accounts/my",
    TRANSACTIONS_URL: (accountId) => `/api/mock-bank/accounts/my/${accountId}/transactions`,
    LOGIN_URL: "/login",
    RECENT_COUNT: 5,
};

document.addEventListener("DOMContentLoaded", () => {
    initHome();
});

async function initHome() {
    const accessToken = sessionStorage.getItem("accessToken");
    if (!accessToken) {
        window.location.href = CONFIG.LOGIN_URL;
        return;
    }

    try {
        const account = await loadPrimaryAccount(accessToken);
        renderBalance(account);

        const transactions = await loadTransactions(accessToken, account.accountId);
        renderTransactions(transactions.slice(0, CONFIG.RECENT_COUNT));
    } catch (error) {
        console.error("[home] 초기화 실패:", error);
        renderBalanceError();
        renderTransactionsError();
    }
}

/** 계좌 목록 조회 후 대표 계좌(첫 번째) 반환 */
async function loadPrimaryAccount(accessToken) {
    const accounts = await fetchWithAuth(CONFIG.ACCOUNTS_ME_URL, accessToken);
    if (!Array.isArray(accounts) || accounts.length === 0) {
        throw new Error("연동된 계좌가 없습니다.");
    }
    return accounts[0];
}

/** 특정 계좌의 거래내역 조회 */
async function loadTransactions(accessToken, accountId) {
    const url = `${CONFIG.TRANSACTIONS_URL(accountId)}?afterTransactionId=0`;
    const data = await fetchWithAuth(url, accessToken);
    return Array.isArray(data) ? data : [];
}

/** 공통 인증 fetch 헬퍼 */
async function fetchWithAuth(url, accessToken) {
    const response = await fetch(url, {
        method: "GET",
        headers: {
            Authorization: `Bearer ${accessToken}`,
            Accept: "application/json",
        },
    });

    if (response.status === 401) {
        sessionStorage.removeItem("accessToken");
        window.location.href = CONFIG.LOGIN_URL;
        throw new Error("인증이 만료되었습니다.");
    }

    if (!response.ok) {
        throw new Error(`요청 실패 (${response.status}): ${url}`);
    }

    return response.json();
}

/** 잔액 렌더링 */
function renderBalance(account) {
    const el = document.getElementById("balanceAmount");
    if (!el) return;

    // TODO: mock-bank 계좌 응답 필드명이 다르면 여기만 수정
    const balance = account.balance ?? account.availableBalance ?? account.accountBalance ?? 0;
    el.textContent = `${formatAmount(balance)}원`;
}

function renderBalanceError() {
    const el = document.getElementById("balanceAmount");
    if (el) el.textContent = "잔액을 불러올 수 없습니다";
}

/** 거래내역 리스트 렌더링 */
function renderTransactions(transactions) {
    const container = document.getElementById("transactionsBody");
    if (!container) return;

    if (transactions.length === 0) {
        container.innerHTML = `<p class="transactions__state">최근 거래내역이 없습니다.</p>`;
        return;
    }

    container.innerHTML = transactions.map((tx, index) => renderTransactionRow(tx, index)).join("");
}

function renderTransactionsError() {
    const container = document.getElementById("transactionsBody");
    if (container) {
        container.innerHTML = `<p class="transactions__state">거래내역을 불러올 수 없습니다.</p>`;
    }
}

function renderTransactionRow(tx, index) {
    const { yearMonth, day } = formatDate(tx.transactionAt);
    const isDeposit = tx.transactionType === "DEPOSIT";
    const sign = isDeposit ? "+" : "-";
    const rowClass = index % 2 === 1 ? "tx-row tx-row--alt" : "tx-row";
    const amountClass = isDeposit ? "tx-cell tx-cell--amount tx-cell--positive" : "tx-cell tx-cell--amount";

    const detail = tx.maskedCounterpartyAccountNumber
        ? `${escapeHtml(tx.counterpartyName)} | ${escapeHtml(tx.maskedCounterpartyAccountNumber)}`
        : escapeHtml(tx.counterpartyName ?? "-");

    return `
        <div class="${rowClass}" role="row">
            <div class="tx-cell tx-cell--date" role="cell">${yearMonth}<br>${day}</div>
            <div class="tx-cell tx-cell--detail" role="cell">${detail}</div>
            <div class="${amountClass}" role="cell">${sign}<br>${formatAmount(tx.amount)}</div>
        </div>
    `;
}

/** 1234567 -> "1,234,567" */
function formatAmount(amount) {
    const num = Number(amount) || 0;
    return num.toLocaleString("ko-KR");
}

/** "2023-10-24T10:15:30" -> { yearMonth: "2023년 10월", day: "24일" } */
function formatDate(isoString) {
    if (!isoString) return { yearMonth: "-", day: "-" };
    const date = new Date(isoString);
    if (Number.isNaN(date.getTime())) return { yearMonth: "-", day: "-" };

    return {
        yearMonth: `${date.getFullYear()}년 ${date.getMonth() + 1}월`,
        day: `${date.getDate()}일`,
    };
}

function escapeHtml(value) {
    return String(value)
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#39;");
}