/**
 * 거래내역 화면 공용 유틸
 * home.js, transfer-history.js 양쪽에서 <script> 로 먼저 로드해서 사용합니다.
 */

const TxUtils = {
    LOGIN_URL: "/login",

    /** 공통 인증 fetch 헬퍼 */
    async fetchWithAuth(url, accessToken) {
        const response = await fetch(url, {
            method: "GET",
            headers: {
                Authorization: `Bearer ${accessToken}`,
                Accept: "application/json",
            },
        });

        if (response.status === 401) {
            sessionStorage.removeItem("accessToken");
            window.location.href = TxUtils.LOGIN_URL;
            throw new Error("인증이 만료되었습니다.");
        }

        if (!response.ok) {
            throw new Error(`요청 실패 (${response.status}): ${url}`);
        }

        return response.json();
    },

    /** 1234567 -> "1,234,567" */
    formatAmount(amount) {
        const num = Number(amount) || 0;
        return num.toLocaleString("ko-KR");
    },

    /** "2023-10-24T10:15:30" -> { yearMonth: "2023년 10월", day: "24일" } */
    formatDate(isoString) {
        if (!isoString) return { yearMonth: "-", day: "-" };
        const date = new Date(isoString);
        if (Number.isNaN(date.getTime())) return { yearMonth: "-", day: "-" };

        return {
            yearMonth: `${date.getFullYear()}년 ${date.getMonth() + 1}월`,
            day: `${date.getDate()}일`,
        };
    },

    escapeHtml(value) {
        return String(value)
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll('"', "&quot;")
            .replaceAll("'", "&#39;");
    },

    /** 거래내역 한 건을 tx-row HTML 문자열로 변환.
     *  rowIndex: 짝/홀 배경용
     *  options.accountLabel: 여러 계좌를 합쳐 보여줄 때, 어느 계좌의 거래인지 표시할 라벨 (선택)
     */
    renderTransactionRow(tx, rowIndex, options = {}) {
        const { yearMonth, day } = TxUtils.formatDate(tx.transactionAt);
        const isDeposit = tx.transactionType === "DEPOSIT";
        const sign = isDeposit ? "+" : "-";
        const rowClass = rowIndex % 2 === 1 ? "tx-row tx-row--alt" : "tx-row";
        const amountClass = isDeposit ? "tx-cell tx-cell--amount tx-cell--positive" : "tx-cell tx-cell--amount";

        const detail = tx.maskedCounterpartyAccountNumber
            ? `${TxUtils.escapeHtml(tx.counterpartyName)} | ${TxUtils.escapeHtml(tx.maskedCounterpartyAccountNumber)}`
            : TxUtils.escapeHtml(tx.counterpartyName ?? "-");

        const accountLabelHtml = options.accountLabel
            ? `<span class="tx-cell__account">${TxUtils.escapeHtml(options.accountLabel)}</span>`
            : "";

        return `
            <div class="${rowClass}" role="row">
                <div class="tx-cell tx-cell--date" role="cell">${yearMonth}<br>${day}</div>
                <div class="tx-cell tx-cell--detail" role="cell">${detail}${accountLabelHtml}</div>
                <div class="${amountClass}" role="cell">${sign}<br>${TxUtils.formatAmount(tx.amount)}</div>
            </div>
        `;
    },
};