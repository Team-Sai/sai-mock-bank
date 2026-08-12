const API_BASE_URL = '';
const TRANSFER_ENDPOINT = `${API_BASE_URL}/api/mock-bank/transfers`;
const MY_ACCOUNTS_ENDPOINT = `${API_BASE_URL}/api/bank-user/accounts`;
const ACCOUNT_LOOKUP_ENDPOINT = `${API_BASE_URL}/api/mock-bank/customer-accounts/lookup`;
const ACCESS_TOKEN_STORAGE_KEY = 'accessToken';

const form = document.getElementById('transfer-form');
const submitBtn = document.getElementById('submit-btn');
const formError = document.getElementById('form-error');
const formSuccess = document.getElementById('form-success');
const formSuccessText = document.getElementById('form-success-text');
const formSuccessLink = document.getElementById('form-success-link');

const requestKeyInput = document.getElementById('requestKey');
const fromAccountSelect = document.getElementById('fromAccountId');
const toAccountNumberInput = document.getElementById('toAccountNumber');
const toAccountPreview = document.getElementById('toAccountPreview');
const amountInput = document.getElementById('amount');

function generateRequestKey() {
    if (window.crypto && window.crypto.randomUUID) {
        return window.crypto.randomUUID();
    }
    return `req-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

requestKeyInput.value = generateRequestKey();

function getAuthHeader() {
    const token = sessionStorage.getItem(ACCESS_TOKEN_STORAGE_KEY);
    return token ? { Authorization: `Bearer ${token}` } : {};
}

async function loadMyAccounts() {
    try {
        const response = await fetch(MY_ACCOUNTS_ENDPOINT, {
            headers: { ...getAuthHeader() },
        });

        if (response.status === 401) {
            fromAccountSelect.innerHTML = '<option value="">로그인이 필요합니다</option>';
            return;
        }

        if (!response.ok) {
            fromAccountSelect.innerHTML = '<option value="">계좌를 불러오지 못했습니다</option>';
            return;
        }

        const accounts = await response.json();

        if (!Array.isArray(accounts) || accounts.length === 0) {
            fromAccountSelect.innerHTML = '<option value="">연결된 계좌가 없습니다</option>';
            return;
        }

        fromAccountSelect.innerHTML = accounts
            .map((account) => {
                const label = `${account.bankName} · ${account.maskedAccountNumber} (${account.accountHolderName})`;
                return `<option value="${account.accountId}">${label}</option>`;
            })
            .join('');
        fromAccountSelect.disabled = false;
    } catch (err) {
        fromAccountSelect.innerHTML = '<option value="">계좌를 불러오지 못했습니다</option>';
    }
}

loadMyAccounts();

let lookupTimer = null;
let toAccountVerified = false;

toAccountNumberInput.addEventListener('input', () => {
    toAccountVerified = false;
    clearTimeout(lookupTimer);

    const accountNumber = toAccountNumberInput.value.trim();
    if (!accountNumber) {
        setToAccountPreview('', null);
        return;
    }

    setToAccountPreview('확인 중…', null);
    lookupTimer = setTimeout(() => lookupToAccount(accountNumber), 400);
});

async function lookupToAccount(accountNumber) {
    try {
        const response = await fetch(
            `${ACCOUNT_LOOKUP_ENDPOINT}?accountNumber=${encodeURIComponent(accountNumber)}`,
            { headers: { ...getAuthHeader() } }
        );

        if (toAccountNumberInput.value.trim() !== accountNumber) {
            return;
        }

        if (response.status === 401) {
            setToAccountPreview('로그인이 필요합니다.', 'error');
            return;
        }

        if (response.status === 404) {
            setToAccountPreview('일치하는 계좌를 찾을 수 없습니다.', 'error');
            return;
        }

        if (!response.ok) {
            setToAccountPreview('계좌 확인에 실패했습니다.', 'error');
            return;
        }

        const data = await response.json();
        toAccountVerified = true;
        setToAccountPreview(`${data.bankName} · ${data.accountHolderName}님`, 'confirmed');
    } catch (err) {
        setToAccountPreview('계좌 확인에 실패했습니다.', 'error');
    }
}

function setToAccountPreview(text, state) {
    toAccountPreview.textContent = text;
    toAccountPreview.classList.remove('is-confirmed', 'is-error');
    if (state === 'confirmed') {
        toAccountPreview.classList.add('is-confirmed');
    } else if (state === 'error') {
        toAccountPreview.classList.add('is-error');
    }
}

amountInput.addEventListener('input', () => {
    const digitsOnly = amountInput.value.replace(/[^0-9]/g, '');
    amountInput.value = digitsOnly === '' ? '' : Number(digitsOnly).toLocaleString('ko-KR');
});

form.addEventListener('submit', async (event) => {
    event.preventDefault();
    hideFormError();
    hideFormSuccess();

    const requestKey = requestKeyInput.value.trim();
    const fromAccountId = fromAccountSelect.value;
    const toAccountNumber = toAccountNumberInput.value.trim();
    const rawAmount = amountInput.value.replace(/[^0-9]/g, '');
    const senderMemo = document.getElementById('senderMemo').value.trim();
    const receiverMemo = document.getElementById('receiverMemo').value.trim();

    if (!requestKey) {
        showFormError('요청 키가 비어 있습니다.');
        return;
    }
    if (!fromAccountId) {
        showFormError('출금 계좌를 선택하세요.');
        return;
    }
    if (!toAccountNumber) {
        showFormError('받는 분 계좌번호를 입력하세요.');
        return;
    }
    if (!toAccountVerified) {
        showFormError('받는 분 계좌 확인이 완료되지 않았습니다.');
        return;
    }
    if (!rawAmount || Number(rawAmount) <= 0) {
        showFormError('이체 금액을 올바르게 입력하세요.');
        return;
    }

    const requestBody = {
        requestKey,
        fromAccountId: Number(fromAccountId),
        toAccountNumber,
        amount: Number(rawAmount),
        senderMemo: senderMemo || null,
        receiverMemo: receiverMemo || null,
    };

    setSubmitting(true);

    try {
        const response = await fetch(TRANSFER_ENDPOINT, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                ...getAuthHeader(),
            },
            body: JSON.stringify(requestBody),
        });

        if (response.status === 401) {
            showFormError('로그인이 필요합니다. 다시 로그인해주세요.');
            return;
        }

        if (!response.ok) {
            const message = await extractErrorMessage(response);
            showFormError(message);
            return;
        }

        const data = await response.json();
        showFormSuccess(data);

        form.reset();
        requestKeyInput.value = generateRequestKey();
        setToAccountPreview('', null);
        toAccountVerified = false;
    } catch (err) {
        showFormError('서버에 연결할 수 없습니다. sai-mock-bank 서버가 실행 중인지 확인하세요.');
    } finally {
        setSubmitting(false);
    }
});

function showFormSuccess(data) {
    const amountText = formatWon(data.amount);
    formSuccessText.textContent = `이체가 완료되었습니다. (이체 번호: ${data.transferId}, ${amountText})`;
    formSuccessLink.href = `/transfer-history?transferId=${encodeURIComponent(data.transferId)}`;
    formSuccess.hidden = false;
}

function hideFormSuccess() {
    formSuccess.hidden = true;
    formSuccessText.textContent = '';
}

async function extractErrorMessage(response) {
    try {
        const body = await response.json();
        return body.message || body.error || body.failureReason || `요청이 실패했습니다. (HTTP ${response.status})`;
    } catch {
        return `요청이 실패했습니다. (HTTP ${response.status})`;
    }
}

function formatWon(value) {
    if (value == null) return '—';
    return `${Number(value).toLocaleString('ko-KR')}원`;
}

function showFormError(message) {
    formError.textContent = message;
    formError.hidden = false;
}

function hideFormError() {
    formError.hidden = true;
    formError.textContent = '';
}

function setSubmitting(isSubmitting) {
    submitBtn.disabled = isSubmitting;
    submitBtn.querySelector('.btn-label').textContent = isSubmitting ? '이체 처리 중…' : '이체하기';
}