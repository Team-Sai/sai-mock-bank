/* Persist the request BEFORE sending it. A timeout/reload must reuse its key and body. */
document.addEventListener("DOMContentLoaded", () => {
    const openButton = document.getElementById("createAccountButton");
    const dialog = document.getElementById("createAccountDialog");
    const form = document.getElementById("createAccountForm");
    const bank = document.getElementById("creationBankCode");
    const bankName = document.getElementById("creationBankName");
    const customFields = document.getElementById("customBankFields");
    const amount = document.getElementById("creationInitialBalance");
    const complete = document.getElementById("creationComplete");
    const title = document.getElementById("createAccountTitle");
    const completeMessage = document.getElementById("creationCompleteMessage");
    function syncCustomBank() {
        customFields.hidden = bank.value !== "CUSTOM";
        bankName.required = bank.value === "CUSTOM";
    }
    bank.addEventListener("change", syncCustomBank);
    const name = document.getElementById("creationAccountName");
    const submit = document.getElementById("submitCreateAccount");
    const message = document.getElementById("creationMessage");
    const status = document.getElementById("accountCreationStatus");
    let sending = false;
    let attempt = null;
    let owner = null;

    function identity() {
        const token = sessionStorage.getItem("accessToken");
        if (!token) throw new Error("로그인이 필요합니다.");
        const payload = token.split(".")[1].replaceAll("-", "+").replaceAll("_", "/");
        const userId = JSON.parse(atob(payload)).sub;
        if (!/^\d+$/.test(String(userId))) throw new Error("로그인 정보를 확인해 주세요.");
        return { token, key: `account-creation:${userId}` };
    }

    openButton.addEventListener("click", () => {
        if (dialog.open || sending) return;
        try {
            owner = identity();
            const saved = JSON.parse(sessionStorage.getItem(owner.key) || "null");
            // Only an explicit new visit to the creation form starts another completed operation.
            attempt = saved?.status === "completed" ? null : saved;
            bank.value = attempt?.body.bankCode ?? "088";
            name.value = attempt?.body.accountName ?? "입출금통장";
            bankName.value = attempt?.body.bankName ?? "";
            amount.value = attempt ? String(attempt.body.initialBalance ?? "0") : "";
            bank.disabled = name.disabled = bankName.disabled = amount.disabled = !!attempt;
            syncCustomBank();
            form.hidden = false;
            complete.hidden = true;
            title.textContent = "계좌 생성";
            submit.setAttribute("aria-busy", "false");
            submit.disabled = attempt?.status === "conflict";
            submit.textContent = attempt ? "같은 요청 재시도" : "생성하기";
            message.textContent = attempt
                ? "이전 생성 요청의 결과를 확인합니다. 같은 조건으로 재시도해 주세요."
                : "";
            dialog.showModal();
        } catch (error) {
            status.textContent = error.message || "계좌 생성 정보를 불러올 수 없습니다.";
        }
    });

    document.getElementById("closeCreationDialog").addEventListener("click", () => dialog.close());

    document.getElementById("closeCreationComplete").addEventListener("click", () => dialog.close());

    form.addEventListener("submit", async (event) => {
        event.preventDefault();
        if (sending || attempt?.status === "completed" || attempt?.status === "conflict") return;
        sending = true;
        submit.disabled = true;
        submit.textContent = "생성 중…";
        submit.setAttribute("aria-busy", "true");
        const controller = new AbortController();
        const timeout = setTimeout(() => controller.abort(), 20000);
        try {
            const current = identity();
            if (current.key !== owner.key) throw new Error("로그인 사용자가 변경되었습니다. 창을 다시 열어 주세요.");
            if (!attempt) {
                const initialBalance = amount.value.trim();
                if (!/^\d{1,17}(\.\d{1,2})?$/.test(initialBalance)) {
                    throw new Error("초기 금액을 0 이상, 소수점 둘째 자리까지 입력해 주세요.");
                }
                const body = { bankCode: bank.value, accountName: name.value.trim(), initialBalance };
                if (bank.value === "CUSTOM") {
                    body.bankName = bankName.value.trim();
                    if (!body.bankName) throw new Error("은행명을 입력해 주세요.");
                }
                if (!body.accountName) throw new Error("계좌명을 입력해 주세요.");
                attempt = { id: crypto.randomUUID(), body, status: "pending" };
            }
            sessionStorage.setItem(owner.key, JSON.stringify(attempt));
            bank.disabled = name.disabled = bankName.disabled = amount.disabled = true;
            const response = await fetch("/api/mock-bank/accounts", {
                method: "POST",
                headers: {
                    Authorization: `Bearer ${current.token}`,
                    "Content-Type": "application/json",
                    "Idempotency-Key": attempt.id,
                },
                body: JSON.stringify(attempt.body),
                signal: controller.signal,
            });
            const data = await response.json();
            if (!response.ok) {
                if (response.status === 409) {
                    attempt.status = "conflict";
                    sessionStorage.setItem(owner.key, JSON.stringify(attempt));
                } else if (response.status === 400) {
                    sessionStorage.removeItem(owner.key);
                    attempt = null;
                    bank.disabled = name.disabled = bankName.disabled = amount.disabled = false;
                }
                throw new Error(data.message || `계좌 생성 요청 실패 (${response.status})`);
            }
            if (!data.accountId) throw new Error("생성 결과를 확인하지 못했습니다. 같은 요청으로 다시 확인해 주세요.");
            attempt.status = "completed";
            attempt.accountId = data.accountId;
            sessionStorage.setItem(owner.key, JSON.stringify(attempt));
            message.textContent = `${data.bankName} ${data.accountName} (${data.maskedAccountNumber}) 계좌가 생성되었습니다.`;
            status.textContent = message.textContent;
            title.textContent = "계좌 생성이 완료되었습니다";
            completeMessage.textContent = message.textContent;
            form.hidden = true;
            complete.hidden = false;
            if (!dialog.open) dialog.showModal();
            document.getElementById("closeCreationComplete").focus();
            // Refresh failures cannot turn a successful creation into another creation attempt.
            await initHome();
        } catch (error) {
            message.textContent = error.name === "AbortError" || error instanceof TypeError
                ? "응답을 확인하지 못했습니다. 같은 요청으로 재시도하면 중복 생성되지 않습니다."
                : error.message;
        } finally {
            clearTimeout(timeout);
            sending = false;
            submit.setAttribute("aria-busy", "false");
            submit.disabled = attempt?.status === "completed" || attempt?.status === "conflict";
            submit.textContent = attempt?.status === "completed" ? "생성 완료" : attempt ? "같은 요청 재시도" : "생성하기";
        }
    });
});
