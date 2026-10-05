import axios from 'axios';
import { ElMessage } from 'element-plus';
import type { R } from '../types';

export const TOKEN_KEY = 'dental.admin.token';
const http = axios.create({ baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1', timeout: 15000 });
let unauthorized: (() => void) | undefined;
export function onUnauthorized(callback: () => void): void { unauthorized = callback; }

http.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY);
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

export class ApiError extends Error {
  constructor(message: string, public readonly code: string, public readonly status?: number) {
    super(message);
    this.name = 'ApiError';
  }
}

/** JSON 写请求始终包装为 R.data；查询和删除通过路径/查询参数传递。 */
export async function request<T>(method: string, url: string, data?: unknown, params?: object): Promise<T> {
  try {
    const response = await http.request<R<T>>({ method, url, params, data: data === undefined ? undefined : { data } });
    const envelope = response.data;
    if (!envelope || envelope.code !== 'OK') {
      throw new ApiError(envelope?.message || '服务返回了无法识别的结果', envelope?.code || 'INVALID_RESPONSE');
    }
    return envelope.data;
  } catch (cause) {
    let error: ApiError;
    if (cause instanceof ApiError) {
      error = cause;
    } else if (axios.isAxiosError(cause)) {
      const body = cause.response?.data as Partial<R<unknown>> | undefined;
      const status = cause.response?.status;
      error = new ApiError(body?.message || (cause.code === 'ECONNABORTED'
        ? '请求超时，请稍后重试' : status === 403 ? '没有权限执行此操作'
          : status === 401 ? '登录已过期，请重新登录' : '无法连接服务，请确认后端已经启动'), body?.code || 'NETWORK_ERROR', status);
      if (status === 401) {
        localStorage.removeItem(TOKEN_KEY);
        unauthorized?.();
      }
    } else {
      error = new ApiError(cause instanceof Error ? cause.message : '操作失败，请重试', 'UNKNOWN_ERROR');
    }
    ElMessage.error(error.message);
    throw error;
  }
}

export const api = {
  get: <T>(url: string, params?: object) => request<T>('GET', url, undefined, params),
  post: <T>(url: string, data: unknown) => request<T>('POST', url, data),
  put: <T>(url: string, data: unknown) => request<T>('PUT', url, data),
  patch: <T>(url: string, data: unknown) => request<T>('PATCH', url, data),
  delete: <T>(url: string) => request<T>('DELETE', url),
};
