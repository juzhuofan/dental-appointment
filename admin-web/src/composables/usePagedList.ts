import { reactive, ref } from 'vue';
import { api } from '../api/http';
import type { PageResult } from '../types';
import { errorText } from '../utils/format';

export function usePagedList<T>(endpoint: string, extra: () => object = () => ({})) {
  const records = ref<T[]>([]);
  const paging = reactive({ page: 1, size: 10, total: 0 });
  const loading = ref(false);
  const error = ref('');
  let sequence = 0;
  async function load(reset = false): Promise<void> {
    if (reset) paging.page = 1;
    const current = ++sequence;
    loading.value = true;
    error.value = '';
    try {
      const result = await api.get<PageResult<T>>(endpoint, { ...extra(), page: paging.page, size: paging.size });
      if (current !== sequence) return;
      records.value = result.records;
      paging.total = result.total;
      // 删除末页最后一行后，回到仍然存在的最后一页。
      if (paging.page > 1 && records.value.length === 0 && result.total > 0) {
        paging.page = Math.ceil(result.total / paging.size);
        await load();
      }
    } catch (cause) {
      if (current === sequence) error.value = errorText(cause);
    } finally {
      if (current === sequence) loading.value = false;
    }
  }
  return { records, paging, loading, error, load };
}
