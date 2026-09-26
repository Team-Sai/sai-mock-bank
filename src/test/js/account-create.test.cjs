const { test } = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');
const { webcrypto } = require('node:crypto');
const source = fs.readFileSync(path.join(__dirname, '../../main/resources/static/js/home/account-create.js'), 'utf8');

function page(fetch, storage = new Map()) {
    const elements = new Map();
    const get = id => {
        if (!elements.has(id)) elements.set(id, {
            hidden: false, attributes: {}, value: '', textContent: '', disabled: false, open: false, events: {},
            setAttribute(name, value) { this.attributes[name] = value; },
            focus() {},
            addEventListener(name, fn) { this.events[name] = fn; },
            showModal() { this.open = true; }, close() { this.open = false; },
        });
        return elements.get(id);
    };
    storage.set('accessToken', `header.${Buffer.from('{"sub":"42"}').toString('base64url')}.signature`);
    const context = {
        document: { getElementById: get, addEventListener: (_, fn) => fn() },
        sessionStorage: { getItem: k => storage.get(k) ?? null, setItem: (k, v) => storage.set(k, v), removeItem: k => storage.delete(k) },
        fetch, atob, crypto: webcrypto, AbortController, setTimeout, clearTimeout,
        initHome: async () => {}, TypeError,
    };
    vm.runInNewContext(source, context);
    return { get, storage, open: () => { get('createAccountButton').events.click(); if (!get('creationInitialBalance').value) get('creationInitialBalance').value = '12345.67'; },
        submit: () => get('createAccountForm').events.submit({ preventDefault() {} }) };
}
const success = () => ({ ok: true, status: 201, json: async () => ({ accountId: 12, bankName: '신한은행', accountName: '입출금통장', maskedAccountNumber: '****1234' }) });

test('double submit sends one request and leaves completion button disabled', async () => {
    let finish;
    let calls = 0;
    const p = page(() => { calls++; return new Promise(resolve => finish = resolve); });
    p.open();
    const first = p.submit();
    await p.submit();
    assert.equal(calls, 1);
    finish(success());
    await first;
    await p.submit();
    assert.equal(calls, 1);
    assert.equal(p.get('createAccountForm').hidden, true);
    assert.equal(p.get('creationComplete').hidden, false);
    assert.equal(p.get('submitCreateAccount').attributes['aria-busy'], 'false');
    p.get('closeCreationComplete').events.click();
    assert.equal(p.get('createAccountDialog').open, false);
    assert.match(p.get('accountCreationStatus').textContent, /계좌가 생성/);
});

test('response loss and page reload reuse identical key and body', async () => {
    let original;
    const p = page(async (_, options) => { original = options; throw new TypeError('network lost'); });
    p.open();
    await p.submit();
    let retried;
    const reloaded = page(async (_, options) => { retried = options; return success(); }, p.storage);
    reloaded.open();
    assert.equal(reloaded.get('creationBankCode').disabled, true);
    await reloaded.submit();
    assert.equal(retried.headers['Idempotency-Key'], original.headers['Idempotency-Key']);
    assert.equal(retried.body, original.body);
});

test('409 does not automatically generate another key or account', async () => {
    let calls = 0;
    const p = page(async () => { calls++; return { ok: false, status: 409, json: async () => ({ message: '다른 조건' }) }; });
    p.open();
    await p.submit();
    const attempt = p.storage.get('account-creation:42');
    await p.submit();
    assert.equal(calls, 1);
    assert.equal(p.storage.get('account-creation:42'), attempt);
    assert.equal(p.get('submitCreateAccount').disabled, true);
});

test('explicit new form after completion uses a new key', async () => {
    const keys = [];
    const p = page(async (_, options) => { keys.push(options.headers['Idempotency-Key']); return success(); });
    p.open(); await p.submit();
    p.get('createAccountDialog').close();
    p.open(); await p.submit();
    assert.equal(keys.length, 2);
    assert.notEqual(keys[0], keys[1]);
});

test('custom bank and exact decimal initial balance are submitted', async () => {
    let body;
    const p = page(async (_, options) => { body = JSON.parse(options.body); return success(); });
    p.open();
    p.get('creationBankCode').value = 'CUSTOM';
    p.get('creationBankCode').events.change();
    assert.equal(p.get('customBankFields').hidden, false);
    p.get('creationBankName').value = ' 테스트은행 ';
    p.get('creationInitialBalance').value = '123456.78';
    await p.submit();
    assert.deepEqual(body, { bankCode: 'CUSTOM', bankName: '테스트은행', accountName: '입출금통장', initialBalance: '123456.78' });
});

test('invalid initial balance is rejected before sending', async () => {
    let calls = 0;
    const p = page(async () => { calls++; return success(); });
    p.open();
    for (const value of ['', '-1', '0.001', '100000000000000000']) {
        p.get('creationInitialBalance').value = value;
        await p.submit();
    }
    assert.equal(calls, 0);
    assert.equal(p.get('submitCreateAccount').attributes['aria-busy'], 'false');
});
