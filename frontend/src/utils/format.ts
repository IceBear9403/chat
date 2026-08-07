/** 后端时间格式 "yyyy-MM-dd HH:mm:ss"，转为本地 Date（后端已按 GMT+8 返回） */
export function parseDate(iso: string): Date {
  return new Date(iso.replace(' ', 'T'))
}

/** 朋友圈时间显示：刚刚 / X分钟前 / X小时前 / 昨天 HH:mm / MM-dd HH:mm / yyyy-MM-dd */
export function formatTime(iso: string): string {
  const date = parseDate(iso)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  const minute = 60_000
  const hour = 60 * minute
  const day = 24 * hour

  if (diff < minute) return '刚刚'
  if (diff < hour) return `${Math.floor(diff / minute)}分钟前`
  if (diff < day) return `${Math.floor(diff / hour)}小时前`

  const yesterday = new Date(now.getTime() - day)
  if (date.getFullYear() === yesterday.getFullYear() && date.getMonth() === yesterday.getMonth() && date.getDate() === yesterday.getDate()) {
    return `昨天 ${pad(date.getHours())}:${pad(date.getMinutes())}`
  }
  const hm = `${pad(date.getHours())}:${pad(date.getMinutes())}`
  if (date.getFullYear() === now.getFullYear()) {
    return `${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${hm}`
  }
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

function pad(n: number): string {
  return n < 10 ? `0${n}` : `${n}`
}

/** 可见范围文案 */
export function visibilityText(v: number): string {
  return v === 1 ? '仅好友' : v === 2 ? '私密' : '公开'
}
