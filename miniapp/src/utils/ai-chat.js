/** 聊天状态与网络协调：账号切换清除展示，重试保持 requestId，不在客户端保存健康对话。 */
export function createAiChatController({ api, getUserId, state = {} }) {
  let ownerId = null;
  let generation = 0;
  let initialized = false;
  let refreshPageCount = 1;

  function reset() {
    generation++;
    initialized = false;
    refreshPageCount = 1;
    Object.assign(state, { conversations: [], conversationPage: 1, conversationHasMore: false,
      currentId: null, messages: [], hasMore: false, beforeId: null, input: '', pending: null,
      sending: false, loading: false, loadingOlder: false, loadingConversations: false,
      conversationsNeedRefresh: false, error: '', errorAction: '' });
  }
  reset();

  function synchronizeAccount() {
    const id = getUserId();
    if (id !== ownerId) { ownerId = id; reset(); }
    return ownerId;
  }

  function current(epoch, id) { return epoch === generation && id === getUserId(); }
  function mergeMessages(values) {
    const merged = new Map([...state.messages, ...values].map(message => [message.id, message]));
    state.messages = [...merged.values()].sort((a, b) => Number(a.id) - Number(b.id));
  }
  function clearCurrentConversation() {
    Object.assign(state, { currentId: null, messages: [], pending: null, input: '', hasMore: false, beforeId: null });
  }

  async function activate() {
    const owner = synchronizeAccount();
    if (!owner || initialized || state.loading) return;
    const epoch = generation;
    state.loading = true; state.error = ''; state.errorAction = '';
    let stage = 'activate';
    try {
      const result = await api.aiConversations(1);
      if (!current(epoch, owner)) return;
      state.conversations = result.records;
      state.conversationPage = 1;
      state.conversationHasMore = result.records.length < result.total;
      initialized = true;
      if (result.records.length) {
        const id = result.records[0].id;
        state.currentId = id;
        stage = 'messages';
        const history = await api.aiMessages(id);
        if (!current(epoch, owner)) return;
        state.currentId = id; state.messages = history.records;
        state.hasMore = history.hasMore; state.beforeId = history.nextBeforeId;
      }
    } catch (error) { if (current(epoch, owner)) { state.error = error.message; state.errorAction = stage; } }
    finally { if (epoch === generation) state.loading = false; }
  }

  async function openConversation(id, { preserveDraft = false } = {}) {
    const owner = synchronizeAccount();
    if (!owner || state.sending || state.loading) return;
    const epoch = ++generation;
    state.currentId = id; state.messages = []; state.pending = null;
    if (!preserveDraft) state.input = '';
    state.hasMore = false; state.beforeId = null; state.loading = true; state.error = ''; state.errorAction = '';
    state.loadingOlder = false; state.loadingConversations = false;
    try {
      const result = await api.aiMessages(id);
      if (!current(epoch, owner)) return;
      state.messages = result.records; state.hasMore = result.hasMore; state.beforeId = result.nextBeforeId;
    } catch (error) { if (current(epoch, owner)) { state.error = error.message; state.errorAction = 'messages'; } }
    finally { if (epoch === generation) state.loading = false; }
  }

  function startNew() {
    if (state.sending || state.loading) return;
    generation++; initialized = true;
    Object.assign(state, { currentId: null, messages: [], hasMore: false, beforeId: null,
      input: '', pending: null, error: '', errorAction: '', loadingOlder: false, loadingConversations: false });
  }

  async function send() {
    const owner = synchronizeAccount();
    if (!owner || state.sending || state.loading) return;
    const content = state.pending?.content || state.input.trim();
    if (!content || content.length > 300) { state.error = '请输入 1 至 300 字的消息'; return; }
    const epoch = generation;
    state.sending = true; state.error = ''; state.errorAction = '';
    try {
      if (!state.currentId) {
        const conversation = await api.createAiConversation();
        if (!current(epoch, owner)) return;
        state.currentId = conversation.id;
        state.conversations = [conversation, ...state.conversations];
      }
      if (!state.pending) {
        state.pending = { requestId: `chat-${Date.now()}-${Math.random().toString(36).slice(2, 14)}`,
          content, conversationId: state.currentId };
      }
      const attempt = state.pending;
      const response = await api.sendAiMessage(attempt.conversationId,
        { requestId: attempt.requestId, content: attempt.content });
      if (!current(epoch, owner)) return;
      mergeMessages([response.userMessage, response.assistantMessage]);
      state.conversations = [response.conversation, ...state.conversations.filter(item => item.id !== response.conversation.id)];
      state.pending = null; state.input = ''; initialized = true;
    } catch (error) { if (current(epoch, owner)) { state.error = error.message || '消息发送未完成，请重试'; state.errorAction = 'send'; } }
    finally { if (epoch === generation) state.sending = false; }
  }

  async function loadOlder() {
    const owner = synchronizeAccount();
    if (!owner || !state.currentId || !state.hasMore || state.loadingOlder || state.sending || state.loading) return;
    const epoch = generation;
    state.loadingOlder = true; state.error = ''; state.errorAction = '';
    try {
      const result = await api.aiMessages(state.currentId, state.beforeId);
      if (!current(epoch, owner)) return;
      mergeMessages(result.records); state.hasMore = result.hasMore; state.beforeId = result.nextBeforeId;
    } catch (error) { if (current(epoch, owner)) { state.error = error.message; state.errorAction = 'older'; } }
    finally { if (epoch === generation) state.loadingOlder = false; }
  }

  async function loadMoreConversations() {
    const owner = synchronizeAccount();
    if (!owner || state.loadingConversations || state.loading || state.sending) return;
    if (state.conversationsNeedRefresh) return refreshConversations();
    if (!state.conversationHasMore) return;
    const epoch = generation;
    state.loadingConversations = true; state.error = ''; state.errorAction = '';
    try {
      const result = await api.aiConversations(state.conversationPage + 1);
      if (!current(epoch, owner)) return;
      const records = new Map([...state.conversations, ...result.records].map(item => [item.id, item]));
      state.conversations = [...records.values()]; state.conversationPage++;
      state.conversationHasMore = state.conversationPage * 20 < result.total;
    } catch (error) { if (current(epoch, owner)) { state.error = error.message; state.errorAction = 'moreConversations'; } }
    finally { if (epoch === generation) state.loadingConversations = false; }
  }

  // Re-read the loaded prefix after deletion: an offset from the old list would skip shifted records.
  // Commit the complete prefix only after every page succeeds, retaining the previous list on failure.
  async function reloadConversationPages(owner, epoch) {
    const records = new Map();
    let lastPage = 1;
    let total = 0;
    for (let page = 1; page <= refreshPageCount; page++) {
      const result = await api.aiConversations(page);
      if (!current(epoch, owner)) return;
      result.records.forEach(item => records.set(item.id, item));
      lastPage = page;
      total = result.total;
      if (result.records.length === 0 || page * 20 >= total) break;
    }
    state.conversations = [...records.values()];
    state.conversationPage = lastPage;
    state.conversationHasMore = lastPage * 20 < total;
    state.conversationsNeedRefresh = false;
    state.error = ''; state.errorAction = '';
    initialized = true;
  }

  async function refreshConversations() {
    const owner = synchronizeAccount();
    if (!owner || state.loading || state.sending) return;
    const epoch = ++generation;
    refreshPageCount = Math.max(refreshPageCount, state.conversationPage);
    state.loading = true; state.loadingOlder = false; state.loadingConversations = false;
    state.conversationsNeedRefresh = true; state.error = ''; state.errorAction = '';
    try { await reloadConversationPages(owner, epoch); }
    catch (error) { if (current(epoch, owner)) {
      state.error = `历史列表刷新失败，请重试。${error.message || ''}`;
      state.errorAction = 'conversations';
    } }
    finally { if (epoch === generation) state.loading = false; }
  }

  async function removeConversation(id) {
    const owner = synchronizeAccount();
    if (!owner || state.sending || state.loading) return;
    const epoch = ++generation;
    refreshPageCount = Math.max(refreshPageCount, state.conversationPage);
    state.loading = true; state.loadingOlder = false; state.loadingConversations = false;
    state.error = ''; state.errorAction = ''; state.conversationsNeedRefresh = true;
    let deleted = false;
    try {
      await api.deleteAiConversation(id);
      if (!current(epoch, owner)) return;
      deleted = true;
      state.conversations = state.conversations.filter(item => item.id !== id);
      if (state.currentId === id) clearCurrentConversation();
      await reloadConversationPages(owner, epoch);
    } catch (error) { if (current(epoch, owner)) {
      // The user explicitly left this conversation; an unknown DELETE result must not allow resending to its ID.
      if (state.currentId === id) clearCurrentConversation();
      state.error = `${deleted ? '会话已删除，但历史列表刷新失败' : '删除结果未确认，请刷新历史列表'}。${error.message || ''}`;
      state.errorAction = 'conversations';
    } }
    finally { if (epoch === generation) state.loading = false; }
  }

  async function retry() {
    if (state.conversationsNeedRefresh) return refreshConversations();
    if (state.pending) return send();
    if (state.errorAction === 'messages' && state.currentId) return openConversation(state.currentId, { preserveDraft: true });
    if (state.errorAction === 'older') return loadOlder();
    if (state.errorAction === 'moreConversations') return loadMoreConversations();
    if (state.errorAction === 'send') return send();
    initialized = false;
    return activate();
  }

  return { state, synchronizeAccount, activate, openConversation, startNew, send, loadOlder,
    loadMoreConversations, removeConversation, retry };
}
