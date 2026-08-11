const API_BASE_URL = '';
const TRANSFER_ENDPOINT = `${API_BASE_URL}/api/mock-bank/transfers`;
const ACCESS_TOKEN_STORAGE_KEY = 'accessToken';

const lookupInput = document.getElementById('lookup-id');
const lookupBtn = document.getElementById('lookup-btn');

const resultEmpty = document.getElementById('result-empty');
const resultSlip = document.getElementById('result-slip');
const slipStatus = document.getElementById('slip-status');
const slipId = document.getElementById('slip-id');
const slipFrom = document.getElementById('slip-from');
const slipTo = document.getElementById('slip-to');
const slipAmount = document.getElementById('slip-amount');
const slipFailureRow = document.getElementById('slip-failure-row');
const slipFailureReason = document.getElementById('slip-failure-reason');
const slipDate = document.getElementById('slip-date');

function getAuthHeader() {
    const token = sessionStorage.getItem(ACCESS_TOKEN_STORAGE_KEY);
    return token ? { Authorization: `Bearer ${token}` } : {};
}

lookupBtn.addEventListener('click', () => {
    const transferId = lookupInput.value.trim();
    if (!transferId) {
        lookupInput.focus();
        return;
    }
    lookupTransfer(transferId);
});

lookupInput.addEventListener('keydown', (event) => {
    if (event.key === 'Enter') {
        event.preventDefault();
        lookupBtn.click();
    }
});

async function lookupTransfer(transferId) {
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
}

// URL에 ?transferId=가 있으면 자동으로 채워서 조회 (이체 화면의 "상세 내역 보기" 링크에서 넘어온 경우)
(function autoLookupFromQuery() {
    const params = new URLSearchParams(window.location.search);
    const transferId = params.get('transferId');
    if (transferId) {
        lookupInput.value = transferId;
        lookupTransfer(transferId);
    }
})();

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

    slipStatus.textContent = '조회 실패';
    slipStatus.classList.remove('is-pending');
    slipStatus.classList.add('is-error');

    slipId.textContent = '—';
    slipFrom.textContent = '—';
    slipTo.textContent = '—';
    slipAmount.textContent = '—';
    slipDate.textContent = '—';

    slipFailureRow.hidden = false;
    slipFailureReason.textContent = message;
}

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