export const STATUS_LABELS = { PENDING: '待确认', CONFIRMED: '待就诊', COMPLETED: '已完成', CANCELLED: '已取消', NO_SHOW: '未到诊' };
export const statusLabel = status => STATUS_LABELS[status] || status || '未知状态';

function chinaDate(value) {
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? null : new Date(parsed.getTime() + 8 * 60 * 60 * 1000);
}
const pad = value => String(value).padStart(2, '0');

export function dateText(value, full = false) {
  const date = chinaDate(value);
  if (!date) return '—';
  return `${full ? `${date.getUTCFullYear()}年` : ''}${date.getUTCMonth() + 1}月${date.getUTCDate()}日`;
}

export function timeText(value) {
  const date = chinaDate(value);
  return date ? `${pad(date.getUTCHours())}:${pad(date.getUTCMinutes())}` : '—';
}

export const timeRange = record => `${timeText(record?.startTime)}–${timeText(record?.endTime)}`;

export function dateKey(value) {
  const date = chinaDate(value);
  return date ? `${date.getUTCFullYear()}-${pad(date.getUTCMonth() + 1)}-${pad(date.getUTCDate())}` : '';
}

export function nextDates(count = 14) {
  const days = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'];
  return Array.from({ length: count }, (_, index) => {
    const value = new Date(Date.now() + index * 86400000);
    const date = chinaDate(value);
    return { key: dateKey(value), day: date.getUTCDate(), month: date.getUTCMonth() + 1, label: index === 0 ? '今天' : index === 1 ? '明天' : days[date.getUTCDay()] };
  });
}

export const canCancelStatus = status => ['PENDING', 'CONFIRMED'].includes(status);

export function profilePayload(profile) {
  return { realName: profile.realName.trim(), phone: profile.phone.trim(), gender: Number(profile.gender || 0), birthDate: profile.birthDate || null, remark: (profile.remark || '').trim() };
}

export function validateProfile(profile) {
  if (!profile.realName?.trim() || profile.realName.trim().length > 80) return '请填写 1–80 字的就诊人姓名';
  if (!/^1[3-9]\d{9}$/.test(profile.phone?.trim())) return '请填写有效的 11 位手机号码';
  if (profile.birthDate && profile.birthDate > dateKey(new Date())) return '出生日期不能晚于今天';
  if ((profile.remark || '').length > 255) return '备注最多填写 255 字';
  return '';
}
