document.addEventListener("DOMContentLoaded", async () => {
    const API = {
        myAccounts: "/api/bank-user/accounts",
        confirm: "/api/link/confirm"
    };

    const listEl = document.getElementById("account-select-list");
    const errorEl = document.getElementById("account-select-error");
    const agreeCheckbox = document.getElementById("link-select-agree-checkbox");
    const confirmButton = document.getElementById("link-select-confirm");
    const cancelButton = document.getElementById("link-select-cancel");

    const selectedIds = new Set();

    function getToken() {
        return sessionStorage.getItem("accessToken");
    }

    function redirectToLogin() {
        window.location.replace(`/login?next=${encodeURIComponent(window.location.pathname)}`);
    }

    function showError(message) {
        errorEl.textContent = message;
        errorEl.hidden = false;
    }

    function maskAccountNumber(raw) {
        if (!raw) return "";
        const digits = String(raw).replace(/\D/g, "");
        if (digits.length < 4) return raw;
        return `${digits.slice(0, 3)}-***-${digits.slice(-4)}`;
    }

    function formatBalance(balance) {
        if (balance == null) return "0";
        return Number(balance).toLocaleString("ko-KR");
    }

    function updateConfirmState() {
        confirmButton.disabled = !(agreeCheckbox.checked && selectedIds.size > 0);
    }

    function toggleSelect(accountId, cardEl) {
        if (selectedIds.has(accountId)) {
            selectedIds.delete(accountId);
            cardEl.classList.remove("selected");
        } else {
            selectedIds.add(accountId);
            cardEl.classList.add("selected");
        }
        updateConfirmState();
    }

    function renderAccounts(accounts) {
        listEl.innerHTML = "";

        if (!Array.isArray(accounts) || accounts.length === 0) {
            showError("연결 가능한 계좌가 없습니다.");
            return;
        }

        accounts.forEach(account => {
            const card = document.createElement("div");
            card.className = "account-select-item";
            card.dataset.accountId = account.accountId;

            card.innerHTML = `
                <div class="account-select-left">
                    <div class="account-select-icon">
                        <svg viewBox="0 0 24 24" aria-hidden="true">
                            <path d="m3 10 9-6 9 6"></path>
                            <path d="M5 10v8"></path>
                            <path d="M9 10v8"></path>
                            <path d="M15 10v8"></path>
                            <path d="M19 10v8"></path>
                            <path d="M3 18h18"></path>
                            <path d="M2 21h20"></path>
                        </svg>
                    </div>
                    <div>
                        <div class="account-select-bank-name">${escapeHtml(account.bankName)}</div>
                        <div class="account-select-alias">${escapeHtml(account.accountName)}</div>
                        <div class="account-select-numbers">
                            ${maskAccountNumber(account.maskedAccountNumber)} · 예금주: ${escapeHtml(account.accountHolderName)}
                        </div>
                    </div>
                </div>

                <div class="account-select-right">
                    <span class="account-select-balance-label">잔액</span>
                    <span class="account-select-balance">${formatBalance(account.balance)}원</span>
                    <span class="account-select-check">
                        <svg viewBox="0 0 24 24" aria-hidden="true">
                            <path d="M20 6 9 17l-5-5"></path>
                        </svg>
                    </span>
                </div>
            `;

            card.addEventListener("click", () => toggleSelect(account.accountId, card));
            listEl.appendChild(card);
        });
    }

    function escapeHtml(value) {
        if (value == null) return "";
        return String(value)
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;");
    }

    async function loadAccounts() {
        const token = getToken();
        if (!token) {
            redirectToLogin();
            return;
        }

        try {
            const response = await fetch(API.myAccounts, {
                headers: { "Authorization": `Bearer ${token}` }
            });

            if (response.status === 401) {
                redirectToLogin();
                return;
            }

            if (response.status === 400) {
                window.location.replace("/link/invalid");
                return;
            }

            if (response.status === 403) {
                // NOTE: 경로에 "link/"를 중복으로 붙이지 않는다.
                // 실제 매핑은 @GetMapping("/link/identity-mismatch") 이다.
                window.location.replace("/link/identity-mismatch");
                return;
            }

            if (!response.ok) {
                throw new Error("계좌 목록을 불러오지 못했습니다.");
            }

            const accounts = await response.json();
            renderAccounts(accounts);
        } catch (error) {
            console.error(error);
            showError(error.message || "계좌 목록을 불러오지 못했습니다.");
        }
    }

    agreeCheckbox?.addEventListener("change", updateConfirmState);

    cancelButton?.addEventListener("click", () => {
        if (window.opener || window.history.length <= 1) {
           window.close();
        }
        setTimeout(() => {
            if (!window.closed) {
                document.body.innerHTML = "<p style='text-align:center; margin-top:40px;'>이 창을 닫아주세요.</p>";
            }
        }, 300);
    });
    confirmButton?.addEventListener("click", async () => {
        const token = getToken();
        if (!token) {
            redirectToLogin();
            return;
        }

        confirmButton.disabled = true;
        const originalText = confirmButton.textContent;
        confirmButton.textContent = "연결 중...";

        try {
            const response = await fetch(API.confirm, {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    "Authorization": `Bearer ${token}`
                },
                body: JSON.stringify({ accountIds: Array.from(selectedIds) })
            });

            if (response.status === 401) {
                redirectToLogin();
                return;
            }

            if (response.status === 400) {
                window.location.replace("/link/invalid");
                return;
            }

            if (response.status === 403) {
                window.location.replace("/link/identity-mismatch");
                return;
            }

            if (!response.ok) {
                throw new Error("계좌 연결에 실패했습니다.");
            }

            const { redirectUrl } = await response.json();
            window.location.href = redirectUrl;
        } catch (error) {
            console.error(error);
            window.alert(error.message || "계좌 연결 중 오류가 발생했습니다.");
            confirmButton.disabled = false;
            confirmButton.textContent = originalText;
        }
    });

    await loadAccounts();
});