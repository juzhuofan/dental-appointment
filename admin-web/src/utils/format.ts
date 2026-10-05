import type { AppointmentStatus, ScheduleStatus } from '../types';

export const appointmentLabels: Record<AppointmentStatus, string> = {
  PENDING: '待确认', CONFIRMED: '已确认', COMPLETED: '已接诊', NO_SHOW: '爽约', CANCELLED: '已取消',
};
export const scheduleLabels: Record<ScheduleStatus, string> = { DRAFT: '草稿', PUBLISHED: '已发布', CLOSED: '已关闭', CANCELLED: '已停诊' };
export function formatTime(value: unknown, short = false): string {
  if (!value) return '—';
  const date = new Date(String(value));
  if (Number.isNaN(date.getTime())) return String(value);
  return new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai', year: short ? undefined : 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', hour12: false,
  }).format(date);
}
/** 日期控件以诊所时区字符串编辑，提交时明确携带 +08:00。 */
export function toApiTime(value: string | null | undefined): string | null {
  if (!value) return null;
  return `${value.slice(0, 19)}+08:00`;
}
export function clinicInput(value: unknown): string | null {
  if (!value) return null;
  const date = new Date(String(value));
  if (Number.isNaN(date.getTime())) return null;
  const parts = new Intl.DateTimeFormat('sv-SE', {
    timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false,
  }).format(date);
  return parts.replace(' ', 'T');
}
export function errorText(error: unknown): string { return error instanceof Error ? error.message : '加载失败，请重试'; }
export function isDismissed(error: unknown): boolean { return error === 'cancel' || error === 'close'; }
