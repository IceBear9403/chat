/**
 * 静态资源 URL 前缀处理。
 *
 * 后端上传接口（LocalStorageService）返回的资源地址是硬编码的 `/uploads/xxx`，
 * 它不知道应用被部署在哪个子路径下。当应用部署在 /chat 下时，浏览器直接请求
 * `/uploads/xxx` 会打到域名根，因此需要统一补上部署前缀。
 *
 * 因为前端所有图片地址（头像、动态图、聊天图）都来自接口响应，没有任何地方
 * 直接书写 `/uploads`，所以在 http 响应拦截器里做一次就地递归即可覆盖全部渲染点，
 * 同时天然兼容库里存量的老数据。
 */

const UPLOAD_PREFIX = '/uploads/'

/**
 * 就地把对象/数组里所有以 `/uploads/` 开头的字符串补上 `import.meta.env.BASE_URL`。
 *
 * 就地修改而非返回新对象：接口响应里的列表项会被 store 与组件共享引用，
 * 深拷贝会打断引用关系。
 */
export function prefixUploadsInPlace(value: unknown): void {
  if (Array.isArray(value)) {
    value.forEach(prefixUploadsInPlace)
    return
  }
  if (value !== null && typeof value === 'object') {
    const record = value as Record<string, unknown>
    for (const key of Object.keys(record)) {
      const item = record[key]
      if (typeof item === 'string' && item.startsWith(UPLOAD_PREFIX)) {
        // BASE_URL 形如 '/chat/'，带尾斜杠，直接拼上去即可
        record[key] = import.meta.env.BASE_URL + item.slice(1)
      } else {
        prefixUploadsInPlace(item)
      }
    }
  }
}
