/**
 * 生成 v4 UUID。
 *
 * <p>{@code crypto.randomUUID} 仅在安全上下文（HTTPS / localhost）可用，
 * 移动端 WebView / 非安全域下不存在，需要降级：
 * <ol>
 *   <li>{@code crypto.randomUUID}（安全上下文首选）</li>
 *   <li>{@code crypto.getRandomValues}（非安全上下文也可用，真随机）</li>
 *   <li>{@code Math.random} 兜底（极小概率碰撞，仅限极端环境）</li>
 * </ol>
 */
export function uuid(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  if (typeof crypto !== 'undefined' && typeof crypto.getRandomValues === 'function') {
    const bytes = new Uint8Array(16)
    crypto.getRandomValues(bytes)
    bytes[6] = (bytes[6] & 0x0f) | 0x40 // version 4
    bytes[8] = (bytes[8] & 0x3f) | 0x80 // variant 10
    const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('')
    return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0
    const v = c === 'x' ? r : (r & 0x3) | 0x8
    return v.toString(16)
  })
}
