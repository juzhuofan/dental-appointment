import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
const chatSource = await readFile(new URL('../../miniapp/src/utils/ai-chat.js', import.meta.url), 'utf8');
const { createAiChatController } = await import(`data:text/javascript;base64,${Buffer.from(chatSource).toString('base64')}`);

function harness(overrides = {}) {
  let account = 1;
  const calls = { sends: [], create: 0, reads: [], deletes: [] };
  const api = {
    aiConversations: async () => ({ records: [], total: 0 }),
    aiMessages: async (id, before) => { calls.reads.push([id, before]); return { records: [], hasMore: false, nextBeforeId: null }; },
    createAiConversation: async () => { calls.create++; return { id: 8, title: '新会话' }; },
    sendAiMessage: async (id, data) => { calls.sends.push({ id, ...data }); return {
      conversation: { id: 8, title: data.content }, userMessage: { id: 1, role: 'USER', content: data.content },
      assistantMessage: { id: 2, role: 'ASSISTANT', content: '回答', answer: { candidates: [] } } }; },
    deleteAiConversation: async id => { calls.deletes.push(id); }, ...overrides
  };
  const controller = createAiChatController({ api, getUserId: () => account });
  return { controller, state: controller.state, calls, setAccount: id => { account = id; controller.synchronizeAccount(); } };
}

test('guest does not fetch account history or send requests', async () => {
  const h = harness(); h.setAccount(null); h.state.input = '咨询';
  await h.controller.activate(); await h.controller.send();
  assert.equal(h.calls.create, 0); assert.equal(h.calls.reads.length, 0);
});

test('first send lazily creates a session and displays the saved pair', async () => {
  const h = harness(); await h.controller.activate(); h.state.input = '诊所地址';
  await h.controller.send();
  assert.equal(h.calls.create, 1); assert.equal(h.calls.sends.length, 1);
  assert.deepEqual(h.state.messages.map(message => message.role), ['USER', 'ASSISTANT']);
  assert.equal(h.state.input, ''); assert.equal(h.state.pending, null);
});

test('uncertain response retries the same request and original content', async () => {
  const sends = [];
  const h = harness({ sendAiMessage: async (id, data) => {
    sends.push({ id, ...data });
    if (sends.length === 1) throw new Error('网络结果不确定');
    return { conversation: { id: 8, title: '地址' }, userMessage: { id: 1, role: 'USER', content: data.content },
      assistantMessage: { id: 2, role: 'ASSISTANT', content: '地址回答' } };
  } });
  await h.controller.activate(); h.state.input = '地址';
  await h.controller.send(); assert.equal(h.state.error, '网络结果不确定');
  h.state.input = '修改后的内容';
  await h.controller.retry();
  assert.deepEqual(sends[0], sends[1]); assert.equal(h.calls.create, 1);
  assert.equal(h.state.messages.length, 2);
});

test('concurrent clicks submit a single paid request', async () => {
  let release;
  const h = harness({ sendAiMessage: async () => new Promise(resolve => { release = resolve; }) });
  await h.controller.activate(); h.state.input = '营业时间';
  const first = h.controller.send(); const second = h.controller.send();
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(h.calls.create, 1);
  release({ conversation: { id: 8 }, userMessage: { id: 1 }, assistantMessage: { id: 2 } });
  await Promise.all([first, second]); assert.equal(h.state.messages.length, 2);
});

test('account change clears sensitive messages and ignores an old response', async () => {
  let release;
  const h = harness({ sendAiMessage: async () => new Promise(resolve => { release = resolve; }) });
  await h.controller.activate(); h.state.input = '我的牙齿问题';
  const pending = h.controller.send(); await new Promise(resolve => setImmediate(resolve));
  h.setAccount(2);
  release({ conversation: { id: 8 }, userMessage: { id: 1, content: '旧账号' }, assistantMessage: { id: 2 } });
  await pending;
  assert.deepEqual(h.state.messages, []); assert.equal(h.state.currentId, null);
  assert.equal(h.state.input, ''); assert.equal(h.state.sending, false);
});

test('temporary login revalidation does not leave the send button stuck', async () => {
  let identity = 1;
  let release;
  const controller = createAiChatController({ getUserId: () => identity, api: {
    aiConversations: async () => ({ records: [], total: 0 }),
    createAiConversation: async () => ({ id: 8, title: '新会话' }),
    sendAiMessage: async () => new Promise(resolve => { release = resolve; })
  } });
  await controller.activate(); controller.state.input = '地址';
  const send = controller.send(); await new Promise(resolve => setImmediate(resolve));
  identity = null;
  release({ conversation: { id: 8 }, userMessage: { id: 1 }, assistantMessage: { id: 2 } });
  await send;
  identity = 1;
  assert.equal(controller.state.sending, false);
  assert.ok(controller.state.pending);
  assert.deepEqual(controller.state.messages, []);
});

test('history pages merge in order and keep the latest pending state clear', async () => {
  const h = harness({ aiConversations: async () => ({ records: [{ id: 8, title: '已有会话' }], total: 1 }),
    aiMessages: async (_id, before) => before
      ? { records: [{ id: 1 }, { id: 2 }], hasMore: false, nextBeforeId: 1 }
      : { records: [{ id: 3 }, { id: 4 }], hasMore: true, nextBeforeId: 3 } });
  await h.controller.activate(); await h.controller.loadOlder();
  assert.deepEqual(h.state.messages.map(message => message.id), [1, 2, 3, 4]);
  assert.equal(h.state.hasMore, false);
});

test('deleting the current session resets its displayed messages', async () => {
  const h = harness(); await h.controller.activate(); h.state.input = '地址'; await h.controller.send();
  await h.controller.removeConversation(8);
  assert.deepEqual(h.calls.deletes, [8]); assert.equal(h.state.currentId, null);
  assert.deepEqual(h.state.messages, []); assert.deepEqual(h.state.conversations, []);
});

test('navigation registers the AI page as a tab and login redirects through switchTab', async () => {
  const { readFile } = await import('node:fs/promises');
  const pages = JSON.parse(await readFile(new URL('../../miniapp/src/pages.json', import.meta.url), 'utf8'));
  assert.ok(pages.tabBar.list.some(page => page.pagePath === 'pages/ai/index'));
  const session = await readFile(new URL('../../miniapp/src/utils/session.js', import.meta.url), 'utf8');
  assert.match(session, /TAB_PAGES.*\/pages\/ai\/index/);
});

test('retry reloads the selected conversation after a history failure without losing the draft', async () => {
  const reads = [];
  let failed = false;
  const h = harness({
    aiConversations: async () => ({ records: [{ id: 8 }, { id: 9 }], total: 2 }),
    aiMessages: async id => {
      reads.push(id);
      if (id === 9 && !failed) { failed = true; throw new Error('读取历史失败'); }
      return { records: [{ id: id * 10, content: `会话${id}` }], hasMore: false, nextBeforeId: null };
    },
  });
  await h.controller.activate();
  await h.controller.openConversation(9);
  h.state.input = '尚未发送的草稿';
  assert.equal(h.state.errorAction, 'messages');
  await h.controller.retry();
  assert.deepEqual(reads, [8, 9, 9]);
  assert.equal(h.state.currentId, 9);
  assert.equal(h.state.messages[0].content, '会话9');
  assert.equal(h.state.input, '尚未发送的草稿');
  assert.equal(h.state.error, '');
  assert.equal(h.calls.create, 0);
  assert.deepEqual(h.calls.sends, []);
});

test('retry recovers the initial message-read stage without restarting account history', async () => {
  let lists = 0; let reads = 0;
  const h = harness({
    aiConversations: async () => { lists++; return { records: [{ id: 8 }], total: 1 }; },
    aiMessages: async () => {
      if (++reads === 1) throw new Error('首次消息读取失败');
      return { records: [{ id: 1 }], hasMore: false, nextBeforeId: null };
    },
  });
  await h.controller.activate();
  assert.equal(h.state.currentId, 8);
  await h.controller.retry();
  assert.equal(lists, 1);
  assert.equal(reads, 2);
  assert.equal(h.state.messages.length, 1);
});

test('deleting current or other history refreshes offset pagination without missing records', async () => {
  for (const deletedId of [1, 2]) {
    let rows = Array.from({ length: 40 }, (_, index) => ({ id: index + 1 }));
    const pages = [];
    const h = harness({
      aiConversations: async page => { pages.push(page); return { records: rows.slice((page - 1) * 20, page * 20), total: rows.length }; },
      deleteAiConversation: async id => { rows = rows.filter(row => row.id !== id); },
    });
    await h.controller.activate();
    await h.controller.removeConversation(deletedId);
    await h.controller.loadMoreConversations();
    assert.deepEqual(h.state.conversations.map(row => row.id), rows.map(row => row.id));
    assert.equal(h.state.conversationHasMore, false);
    assert.equal(h.state.currentId, deletedId === 1 ? null : 1);
    assert.deepEqual(pages, [1, 1, 2]);
  }
});

test('a page already loading when deletion starts cannot overwrite the refreshed prefix', async () => {
  let rows = Array.from({ length: 40 }, (_, index) => ({ id: index + 1 }));
  let releaseOldPage; let deferPage = true;
  const h = harness({
    aiConversations: async page => {
      const snapshot = { records: rows.slice((page - 1) * 20, page * 20), total: rows.length };
      if (page === 2 && deferPage) { deferPage = false; return new Promise(resolve => { releaseOldPage = () => resolve(snapshot); }); }
      return snapshot;
    },
    deleteAiConversation: async id => { rows = rows.filter(row => row.id !== id); },
  });
  await h.controller.activate();
  const oldPage = h.controller.loadMoreConversations();
  await h.controller.removeConversation(2);
  releaseOldPage(); await oldPage;
  assert.equal(h.state.conversationPage, 1);
  assert.equal(h.state.conversations.at(-1).id, 21);
  await h.controller.loadMoreConversations();
  assert.deepEqual(h.state.conversations.map(row => row.id), rows.map(row => row.id));
});

test('failed deletion refresh keeps a retryable list and never appends from an obsolete offset', async () => {
  let rows = Array.from({ length: 40 }, (_, index) => ({ id: index + 1 }));
  let failRefresh = false; const pages = [];
  const h = harness({
    aiConversations: async page => {
      pages.push(page);
      if (failRefresh) { failRefresh = false; throw new Error('刷新断网'); }
      return { records: rows.slice((page - 1) * 20, page * 20), total: rows.length };
    },
    deleteAiConversation: async id => { rows = rows.filter(row => row.id !== id); failRefresh = true; },
  });
  await h.controller.activate();
  await h.controller.removeConversation(2);
  assert.equal(h.state.conversationsNeedRefresh, true);
  assert.equal(h.state.conversations.length, 19);
  assert.match(h.state.error, /会话已删除/);
  await h.controller.loadMoreConversations();
  assert.deepEqual(pages, [1, 1, 1]);
  assert.equal(h.state.conversationsNeedRefresh, false);
  await h.controller.loadMoreConversations();
  assert.deepEqual(h.state.conversations.map(row => row.id), rows.map(row => row.id));
});

test('refreshing several loaded pages is atomic and retries the full prefix after a later-page failure', async () => {
  let rows = Array.from({ length: 60 }, (_, index) => ({ id: index + 1 }));
  let failPageTwo = false;
  const h = harness({
    aiConversations: async page => {
      if (page === 2 && failPageTwo) { failPageTwo = false; throw new Error('第二页暂不可用'); }
      return { records: rows.slice((page - 1) * 20, page * 20), total: rows.length };
    },
    deleteAiConversation: async id => { rows = rows.filter(row => row.id !== id); failPageTwo = true; },
  });
  await h.controller.activate(); await h.controller.loadMoreConversations();
  await h.controller.removeConversation(25);
  assert.equal(h.state.conversations.length, 39);
  assert.equal(h.state.conversations.at(-1).id, 40);
  assert.equal(h.state.conversationsNeedRefresh, true);
  await h.controller.retry();
  assert.equal(h.state.conversations.length, 40);
  assert.equal(h.state.conversations.at(-1).id, 41);
  await h.controller.loadMoreConversations();
  assert.deepEqual(h.state.conversations.map(row => row.id), rows.map(row => row.id));
});

test('an account switch ignores an old post-delete refresh and clears its retry state', async () => {
  let listCalls = 0; let release;
  const h = harness({
    aiConversations: async () => {
      if (++listCalls > 1) return new Promise(resolve => { release = resolve; });
      return { records: [{ id: 1 }, { id: 2 }], total: 2 };
    },
  });
  await h.controller.activate();
  const deletion = h.controller.removeConversation(2);
  await new Promise(resolve => setImmediate(resolve));
  h.setAccount(2);
  release({ records: [{ id: 1, title: '旧账号会话' }], total: 1 });
  await deletion;
  assert.deepEqual(h.state.conversations, []);
  assert.equal(h.state.currentId, null);
  assert.equal(h.state.conversationsNeedRefresh, false);
  assert.equal(h.state.loading, false);
});

test('retrying history with a pending message refreshes only history and preserves its send identifier', async () => {
  let rows = [{ id: 8 }, { id: 9 }]; let failRefresh = false; let sends = 0;
  const h = harness({
    aiConversations: async () => {
      if (failRefresh) { failRefresh = false; throw new Error('历史列表断网'); }
      return { records: rows, total: rows.length };
    },
    sendAiMessage: async () => { sends++; throw new Error('发送结果不确定'); },
    deleteAiConversation: async id => { rows = rows.filter(row => row.id !== id); failRefresh = true; },
  });
  await h.controller.activate(); h.state.input = '保留这次请求';
  await h.controller.send();
  const pending = { ...h.state.pending };
  await h.controller.removeConversation(9);
  assert.equal(h.state.conversationsNeedRefresh, true);
  await h.controller.retry();
  assert.equal(h.state.conversationsNeedRefresh, false);
  assert.deepEqual(h.state.pending, pending);
  assert.equal(sends, 1);
  assert.deepEqual(h.state.conversations.map(row => row.id), [8]);
});

test('an uncertain delete of the current conversation exits it and cannot resend its pending message', async () => {
  let rows = [{ id: 8 }]; let sends = 0;
  const h = harness({
    aiConversations: async () => ({ records: rows, total: rows.length }),
    sendAiMessage: async () => { sends++; throw new Error('发送结果不确定'); },
    deleteAiConversation: async id => {
      rows = rows.filter(row => row.id !== id);
      throw new Error('删除响应丢失');
    },
  });
  await h.controller.activate(); h.state.input = '即将删除的草稿'; await h.controller.send();
  assert.ok(h.state.pending);
  await h.controller.removeConversation(8);
  assert.equal(h.state.currentId, null);
  assert.equal(h.state.pending, null);
  assert.equal(h.state.input, '');
  assert.equal(h.state.conversationsNeedRefresh, true);
  await h.controller.retry();
  assert.equal(sends, 1);
  assert.equal(h.state.currentId, null);
  assert.equal(h.state.conversationsNeedRefresh, false);
  assert.deepEqual(h.state.conversations, []);
});
