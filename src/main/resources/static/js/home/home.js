/**
 * 홈 화면(잔액 + 최근 거래내역) 데이터 로딩
 * transaction-utils.js(TxUtils)가 먼저 로드되어 있어야 합니다.
 *
 * 백엔드 의존:
 *  - GET /api/mock-bank/accounts/my
 *  - GET /api/mock-bank/accounts/my/{accountId}/transactions?size=N
 *      최신순(DESC), beforeTransactionId 생략 시 최신 N건.
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

    let accounts;
    try {
        accounts = await TxUtils.fetchWithAuth(CONFIG.ACCOUNTS_ME_URL, accessToken);
        if (!Array.isArray(accounts)) throw new Error("계좌 목록 응답이 올바르지 않습니다.");
    } catch (error) {
        console.error("[home] 계좌 조회 실패:", error);
        document.getElementById("homeAccountsState").textContent = "계좌를 불러올 수 없습니다.";
        document.getElementById("homeAccountsState").hidden = false;
        renderBalanceError();
        renderTransactionsError();
        return;
    }

    renderAccounts(accounts);
    const account = accounts[0];
    if (!account) {
        document.getElementById("balanceAmount").textContent = "0원";
        document.getElementById("transactionsBody").innerHTML = '<p class="tx2-state">계좌를 생성하면 거래내역을 확인할 수 있습니다.</p>';
        return;
    }
    renderBalance(account);

    try {
        const accountLabel = { bankName: account.bankName, maskedAccountNumber: account.maskedAccountNumber };
        const transactions = await loadTransactions(accessToken, account.accountId, CONFIG.RECENT_COUNT);
        renderTransactions(transactions, accountLabel);
    } catch (error) {
        console.error("[home] 거래내역 조회 실패:", error);
        renderTransactionsError();
    }
}

function renderAccounts(accounts) {
    const list = document.getElementById("homeAccounts");
    const state = document.getElementById("homeAccountsState");
    const items = accounts.map((account) => {
        const item = document.createElement("li");
        item.textContent = `${account.bankName} · ${account.accountName} · ${account.accountNumber}`;
        return item;
    });
    list.replaceChildren(...items);
    state.textContent = accounts.length === 0 ? "보유한 계좌가 없습니다." : "";
    state.hidden = accounts.length > 0;
}

/** 특정 계좌의 최신 거래내역 size건 조회 */
async function loadTransactions(accessToken, accountId, size) {
    const url = `${CONFIG.TRANSACTIONS_URL(accountId)}?size=${size}`;
    const data = await TxUtils.fetchWithAuth(url, accessToken);
    return Array.isArray(data) ? data : [];
}

/** 잔액 렌더링 */
function renderBalance(account) {
    const el = document.getElementById("balanceAmount");
    if (!el) return;

    el.textContent = `${TxUtils.formatAmount(account.balance)}원`;

}

function renderBalanceError() {
    const el = document.getElementById("balanceAmount");
    if (el) el.textContent = "잔액을 불러올 수 없습니다";
}

/** 거래내역 리스트 렌더링 */
function renderTransactions(transactions, accountLabel) {
    const container = document.getElementById("transactionsBody");
    if (!container) return;

    if (transactions.length === 0) {
        container.innerHTML = `<p class="tx2-state">최근 거래내역이 없습니다.</p>`;
        return;
    }

    container.innerHTML = transactions
        .map((tx) => TxUtils.renderTransactionRowV2(tx, { accountLabel }))
        .join("");
}

function renderTransactionsError() {
    const container = document.getElementById("transactionsBody");
    if (container) {
        container.innerHTML = `<p class="tx2-state">거래내역을 불러올 수 없습니다.</p>`;
    }
}
