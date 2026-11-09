const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const template = fs.readFileSync(path.join(__dirname, '../../main/resources/templates/settings.html'), 'utf8');
const script = template.match(/<script>([\s\S]*?)<\/script>/)[1];

const setup = (response) => {
    const handlers = {};
    const updates = [];
    const timers = [];
    const elements = new Map();
    const context = {
        document: {
            getElementById(id) {
                if (!elements.has(id)) elements.set(id, {
                    addEventListener: (event, handler) => { handlers[`${id}:${event}`] = handler; },
                });
                return elements.get(id);
            },
        },
        window: { PrismToast: { progress: () => ({ update: (...args) => updates.push(args) }) } },
        confirm: () => true,
        fetch: async (url) => url === '/api/settings'
            ? { json: async () => ({ privacyLog: [] }) }
            : { ok: response.ok, status: response.status, json: async () => response.body },
        setTimeout: (fn) => timers.push(fn),
    };
    vm.runInNewContext(script, context);
    return { handlers, updates, timers };
};

test('failed deletion displays the server error without scheduling a success redirect', async () => {
    const { handlers, updates, timers } = setup({ ok: false, status: 500, body: { error: 'storage unavailable' } });
    await handlers['delete-total-btn:click']();
    assert.equal(updates.at(-1)[1], 'error');
    assert.match(updates.at(-1)[0], /storage unavailable/);
    assert.equal(timers.length, 0);
});

test('successful deletion displays success and schedules the redirect', async () => {
    const { handlers, updates, timers } = setup({ ok: true, status: 200, body: { success: true } });
    await handlers['delete-total-btn:click']();
    assert.equal(updates.at(-1)[1], 'success');
    assert.equal(timers.length, 1);
});
