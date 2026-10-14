import { getCurrentScope, onScopeDispose, ref, watch } from 'vue';
import type { Appointment } from '../types';

interface DetailContext { id: number; selection: number }

/** 详情只接受当前抽屉及最新请求的结果；关闭、切换和重开都会使旧上下文失效。 */
export function useAppointmentDetail(fetchDetail: (id: number) => Promise<Appointment>) {
  const drawer = ref(false);
  const detail = ref<Appointment | null>(null);
  const detailBusy = ref(false);
  const detailError = ref('');
  let selectedId: number | null = null;
  let selection = 0;
  let request = 0;

  function isCurrent(context: DetailContext | null): context is DetailContext {
    return context !== null && drawer.value && context.id === selectedId && context.selection === selection;
  }

  function capture(id: number): DetailContext | null {
    return drawer.value && selectedId === id ? { id, selection } : null;
  }

  function invalidate(): void {
    selection++;
    request++;
    selectedId = null;
    detailBusy.value = false;
    detailError.value = '';
  }

  watch(drawer, (visible) => { if (!visible) invalidate(); }, { flush: 'sync' });
  if (getCurrentScope()) onScopeDispose(invalidate);

  async function refresh(context: DetailContext | null): Promise<void> {
    if (!isCurrent(context)) return;
    const currentRequest = ++request;
    detailBusy.value = true;
    detailError.value = '';
    try {
      const result = await fetchDetail(context.id);
      if (isCurrent(context) && request === currentRequest) detail.value = result;
    } catch (cause) {
      if (isCurrent(context) && request === currentRequest) {
        detailError.value = cause instanceof Error ? cause.message : '加载失败，请重试';
      }
    } finally {
      if (isCurrent(context) && request === currentRequest) detailBusy.value = false;
    }
  }

  async function open(id: number): Promise<void> {
    selection++;
    selectedId = id;
    drawer.value = true;
    detail.value = null;
    await refresh(capture(id));
  }

  function reportError(context: DetailContext | null, cause: unknown): void {
    if (isCurrent(context)) detailError.value = cause instanceof Error ? cause.message : '操作失败，请重试';
  }

  function close(context: DetailContext | null): void {
    if (isCurrent(context)) drawer.value = false;
  }

  return { drawer, detail, detailBusy, detailError, open, refresh, capture, reportError, close };
}
