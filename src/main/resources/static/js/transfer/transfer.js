const API_BASE_URL = '';
const TRANSFER_ENDPOINT = `${API_BASE_URL}/api/mock-bank/transfers`;
const MY_ACCOUNTS_ENDPOINT = `${API_BASE_URL}/api/bank-user/accounts`;
const ACCOUNT_LOOKUP_ENDPOINT = `${API_BASE_URL}/api/mock-bank/customer-accounts/lookup`;
const ACCESS_TOKEN_STORAGE_KEY = 'accessToken';

const form = document.getElementById('transfer-form');
const submitBtn = document.getElementById('submit-btn');
const formError = document.getElementById('form-error');

const requestKeyInput = document.getElementById('requestKey');
const fromAccountSelect = document.getElementById('fromAccountId');
const toAccountNumberInput = document.getElementById('toAccountNumber');
const toAccountPreview = document.getElementById('toAccountPreview');
const amountInput = document.getElementById('amount');

const lookupInput = document.getElementById('lookup-id');
const lookupBtn = document.getElementById('lookup-btn');

const resultEmpty = document.getElementById('result-empty');
const resultSlip = document.getElementById('result-slip');
const slipStatus = document.getElementById('slip-status');
const slipId = document.getElementById('slip-id');
const slipRequestKey = document.getElementById('slip-request-key');
const slipFrom = document.getElementById('slip-from');
const slipTo = document.getElementById('slip-to');
const slipAmount = document.getElementById('slip-amount');
const slipFailureRow = document.getElementById('slip-failure-row');
const slipFailureReason = document.getElementById('slip-failure-reason');
const slipDate = document.getElementById('slip-date');

// 요청 키(멱등성 키) 자동 발급
function generateRequestKey() {
    if (window.crypto && window.crypto.randomUUID) {
        return window.crypto.randomUUID();
    }
    return `req-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

requestKeyInput.value = generateRequestKey();

// 로그인 시 저장해둔 JWT를 Authorization 헤더로 변환
function getAuthHeader() {
    const token = sessionStorage.getItem(ACCESS_TOKEN_STORAGE_KEY);
    return token ? { Authorization: `Bearer ${token}` } : {};
}

// 출금 계좌 목록 로드 (로그인한 사용자의 계좌만 반환됨)
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

// 받는 분 계좌번호로 예금주명/은행명 조회 (디바운스)
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

        // 입력이 바뀌는 도중 이전 요청 결과가 늦게 와서 덮어쓰지 않도록 확인
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

// 금액 입력: 숫자만 남기고 천 단위 콤마 표시
amountInput.addEventListener('input', () => {
    const digitsOnly = amountInput.value.replace(/[^0-9]/g, '');
    amountInput.value = digitsOnly === '' ? '' : Number(digitsOnly).toLocaleString('ko-KR');
});

form.addEventListener('submit', async (event) => {
    event.preventDefault();
    hideFormError();

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
            const message = '로그인이 필요합니다. 다시 로그인해주세요.';
            showFormError(message);
            renderErrorSlip(message);
            return;
        }

        if (!response.ok) {
            const message = await extractErrorMessage(response);
            showFormError(message);
            renderErrorSlip(message);
            return;
        }

        const data = await response.json();
        renderSlip(data);

        form.reset();
        requestKeyInput.value = generateRequestKey();
        setToAccountPreview('', null);
        toAccountVerified = false;
    } catch (err) {
        const message = '서버에 연결할 수 없습니다. sai-mock-bank 서버가 실행 중인지 확인하세요.';
        showFormError(message);
        renderErrorSlip(message);
    } finally {
        setSubmitting(false);
    }
});

lookupBtn.addEventListener('click', async () => {
    const transferId = lookupInput.value.trim();
    if (!transferId) {
        lookupInput.focus();
        return;
    }

    lookupBtn.disabled = true;
    lookupBtn.textContent = '조회 중…';

    try {
        const response = await fetch(`${TRANSFER_ENDPOINT}/${encodeURIComponent(transferId)}`, {
            headers: { ...getAuthHeader() },
        });

        if (response.status === 401) {
            renderErrorSlip('로그인이 필요합니다. 다시 로그인해주세요.');
            return;
        }

        if (!response.ok) {
            const message = response.status === 404
                ? `이체 번호 ${transferId}를 찾을 수 없습니다.`
                : await extractErrorMessage(response);
            renderErrorSlip(message);
            return;
        }

        const data = await response.json();
        renderSlip(data);
    } catch (err) {
        renderErrorSlip('서버에 연결할 수 없습니다. sai-mock-bank 서버가 실행 중인지 확인하세요.');
    } finally {
        lookupBtn.disabled = false;
        lookupBtn.textContent = '조회';
    }
});

lookupInput.addEventListener('keydown', (event) => {
    if (event.key === 'Enter') {
        event.preventDefault();
        lookupBtn.click();
    }
});

function renderSlip(data) {
    resultEmpty.hidden = true;
    resultSlip.hidden = false;

    const presentation = resolveStatusPresentation(data.status);
    slipStatus.textContent = presentation.label;
    slipStatus.classList.remove('is-error', 'is-pending');
    if (presentation.className) {
        slipStatus.classList.add(presentation.className);
    }

    slipId.textContent = data.transferId ?? '—';
    slipRequestKey.textContent = data.requestKey ?? '—';
    slipFrom.textContent = data.fromMaskedAccountNumber ?? '—';
    slipTo.textContent = data.toMaskedAccountNumber ?? '—';
    slipAmount.textContent = formatWon(data.amount);
    slipDate.textContent = formatDate(data.completedAt);

    if (data.failureReason) {
        slipFailureRow.hidden = false;
        slipFailureReason.textContent = data.failureReason;
    } else {
        slipFailureRow.hidden = true;
        slipFailureReason.textContent = '—';
    }
}

function renderErrorSlip(message) {
    resultEmpty.hidden = true;
    resultSlip.hidden = false;

    slipStatus.textContent = '이체 실패';
    slipStatus.classList.remove('is-pending');
    slipStatus.classList.add('is-error');

    slipId.textContent = '—';
    slipRequestKey.textContent = '—';
    slipFrom.textContent = '—';
    slipTo.textContent = '—';
    slipAmount.textContent = '—';
    slipDate.textContent = formatDate(new Date().toISOString());

    slipFailureRow.hidden = false;
    slipFailureReason.textContent = message;
}

// TransferStatus enum: PENDING / SUCCESS / FAILED
const STATUS_PRESENTATION = {
    SUCCESS: { label: '이체 완료', className: null },
    FAILED: { label: '이체 실패', className: 'is-error' },
    PENDING: { label: '처리 중', className: 'is-pending' },
};

function resolveStatusPresentation(status) {
    return STATUS_PRESENTATION[status] ?? { label: status ?? '알 수 없음', className: 'is-pending' };
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

function formatDate(value) {
    if (!value) return '—';
    const d = new Date(value);
    if (Number.isNaN(d.getTime())) return String(value);
    return d.toLocaleString('ko-KR', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
        hour12: false,
    });
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