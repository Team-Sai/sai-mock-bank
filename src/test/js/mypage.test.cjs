const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const source = fs.readFileSync(path.join(__dirname, '../../main/resources/static/js/user/mypage.js'), 'utf8');

async function page({ accounts = [], accountStatus = 200, meStatus = 200, networkFailure = false, token = 'test-token' } = {}) {
    const elements = new Map();
    function element() {
        return { textContent: '', hidden: true, children: [], events: {},
            set innerHTML(value) { this.html = value; this.children = []; this.textContent = ''; },
            get innerHTML() { return this.html; },
            appendChild(child) { this.children.push(child); },
            addEventListener(name, fn) { this.events[name] = fn; },
            removeAttribute() {}, querySelector() { return element(); } };
    }
    const get = id => { if (!elements.has(id)) elements.set(id, element()); return elements.get(id); };
    get('account-list').textContent = '계좌를 불러오는 중…';
    const storage = new Map(token ? [['accessToken', token]] : []);
    const calls = [], redirects = [];
    let ready;
    const listeners = new Map();
    const me = { name: '검증 사용자', email: 'test@example.com', userToken: 'SAI-TEST', createdAt: '2026-11-09T12:00:00' };
    vm.runInNewContext(source, {
        document: { addEventListener: (event, fn) => { if (event === "DOMContentLoaded") ready = fn; else listeners.set(event, fn); }, getElementById: get,
            querySelectorAll: () => [], querySelector: () => element(), createElement: element },
        window: { location: { protocol: 'http:', replace: url => redirects.push(url) } },
        sessionStorage: { getItem: k => storage.get(k), removeItem: k => storage.delete(k), length: 0 },
        localStorage: { removeItem() {} },
        fetch: async (url, options) => {
            calls.push({ url, options });
            const isAccounts = url === '/api/mock-bank/accounts/my';
            if (isAccounts && networkFailure) throw new Error('network unavailable');
            const status = isAccounts ? accountStatus : url === '/api/users/me' ? meStatus : 200;
            return { ok: status === 200, status, text: async () => JSON.stringify(isAccounts ? accounts : me) };
        },
        console: { error() {}, warn() {} }, Date, Intl, Number,
    });
    await ready();
    return { get, storage, calls, redirects, setAccounts: next => { accounts = next; },
        setAccountStatus: next => { accountStatus = next; },
        setNetworkFailure: next => { networkFailure = next; },
        created: () => listeners.get('account-created')() };
}

test('profile and every account balance use actual API fields and authenticated requests', async () => {
    const p = await page({ accounts: [26335874, 0, 12345.67].map(balance => ({
        bankName: '신한은행', accountName: '입출금통장', accountNumber: '1101234560915', maskedAccountNumber: '***0915', balance,
    })) });
    assert.equal(p.get('member-name').textContent, '검증 사용자');
    assert.equal(p.get('member-token').textContent, 'SAI-TEST');
    assert.equal(p.get('detail-email').textContent, 'test@example.com');
    assert.match(p.get('joined-at').textContent, /2026/);
    assert.equal(p.get('header-user-name').hidden, false);
    const rows = p.get('account-list').children;
    assert.equal(rows.length, 3);
    ['26,335,874원', '0원', '12,345.67원'].forEach((value, i) => assert(rows[i].innerHTML.includes(value)));
    assert(rows[0].innerHTML.includes('입출금통장'));
    assert(rows.every(row => row.innerHTML.includes('1101234560915')));
    assert(rows.every(row => !row.innerHTML.includes('***0915')));
    assert(p.calls.every(c => c.options.headers.Authorization === 'Bearer test-token'));
});

test('empty account list displays an explicit empty state', async () => {
    const p = await page();
    assert.match(p.get('account-list').children[0].innerHTML, /보유한 계좌가 없습니다/);
});

test('successful account creation refreshes the account list without leaving mypage', async () => {
    const p = await page();
    p.setAccounts([{ bankName: '신한은행', accountName: '새 통장', balance: 54321 }]);
    await p.created();
    assert.equal(p.calls.filter(c => c.url === '/api/mock-bank/accounts/my').length, 2);
    assert.match(p.get('account-list').children[0].innerHTML, /54,321원/);
    assert.deepEqual(p.redirects, []);
});

for (const options of [{ accountStatus: 500 }, { networkFailure: true }, { accounts: { unexpected: true } }]) {
    test(`account failure preserves profile and shows error: ${JSON.stringify(options)}`, async () => {
        const p = await page(options);
        assert.equal(p.get('member-name').textContent, '검증 사용자');
        assert.equal(p.get('mypage-error').hidden, true);
        assert.equal(p.get('account-error').hidden, false);
        assert.match(p.get('account-list').textContent, /불러오지 못했습니다/);
    });
}

for (const failure of ['http', 'network', 'invalid']) {
    test(`refresh failure keeps previous full account numbers and balances, then recovers: ${failure}`, async () => {
        const account = { bankName: '신한은행', accountName: '기존 통장', accountNumber: '1101234560915', balance: 54321 };
        const p = await page({ accounts: [account] });
        const previousRow = p.get('account-list').children[0];
        if (failure === 'http') p.setAccountStatus(500);
        if (failure === 'network') p.setNetworkFailure(true);
        if (failure === 'invalid') p.setAccounts({ unexpected: true });
        await p.created();
        assert.equal(p.get('account-list').children[0], previousRow);
        assert.match(previousRow.innerHTML, /1101234560915/);
        assert.match(previousRow.innerHTML, /54,321원/);
        assert.equal(p.get('member-name').textContent, '검증 사용자');
        assert.equal(p.get('mypage-error').hidden, true);
        assert.equal(p.get('account-error').hidden, false);
        assert.match(p.get('account-error').textContent, /이전 조회 결과/);

        p.setAccountStatus(200);
        p.setNetworkFailure(false);
        p.setAccounts([account, { ...account, accountName: '새 통장', accountNumber: '1109876543210', balance: 100 }]);
        await p.created();
        assert.equal(p.get('account-list').children.length, 2);
        assert.match(p.get('account-list').children[1].innerHTML, /1109876543210/);
        assert.equal(p.get('account-error').hidden, true);
        assert.equal(p.get('account-error').textContent, '');
    });
}

test('successful empty response clears previous accounts without showing an error', async () => {
    const p = await page({ accounts: [{ accountNumber: '1101234560915', balance: 1 }] });
    p.setAccounts([]);
    await p.created();
    assert.match(p.get('account-list').children[0].innerHTML, /보유한 계좌가 없습니다/);
    assert.equal(p.get('account-error').hidden, true);
});

for (const options of [{ token: null }, { accountStatus: 401 }, { meStatus: 403 }]) {
    test(`missing or expired authorization redirects to login: ${JSON.stringify(options)}`, async () => {
        const p = await page(options);
        assert.deepEqual(p.redirects, ['/login?required=true']);
        assert.equal(p.storage.has('accessToken'), false);
    });
}

test('logout clears the token and calls server logout once on repeated clicks', async () => {
    const p = await page();
    await Promise.all([p.get('logout-button').events.click(), p.get('logout-button').events.click()]);
    assert.equal(p.storage.has('accessToken'), false);
    assert.equal(p.calls.filter(c => c.url === '/api/auth/logout').length, 1);
    assert.deepEqual(p.redirects, ['/login']);
});

test('bank and account labels are escaped before rendering', async () => {
    const p = await page({ accounts: [{ bankName: '<script>alert(1)</script>', accountName: '<img onerror=alert(1)>', balance: 1 }] });
    const html = p.get('account-list').children[0].innerHTML;
    assert(!html.includes('<script>'));
    assert(!html.includes('<img onerror'));
    assert(html.includes('&lt;script&gt;'));
});
