/** 与后端雪花 ID 对齐，前端一律用 string，避免 JSON number 精度丢失 */
export const ID_KEYS = new Set([
  'barId',
  'userId',
  'postId',
  'lastId',
  'nextCursor',
  'commentId',
  'replyCommentId',
  'rootCommentId',
  'parentId',
  'rootId',
  'replyUserId',
])

export function idStr(value) {
  if (value == null || value === '') {
    return ''
  }
  return typeof value === 'string' ? value : String(value)
}

/** 递归把响应里的 barId / userId / postId 转为 string */
export function normalizeIds(value) {
  if (value == null) {
    return value
  }
  if (Array.isArray(value)) {
    return value.map(normalizeIds)
  }
  if (typeof value === 'object') {
    const out = {}
    for (const [key, val] of Object.entries(value)) {
      if (ID_KEYS.has(key) && val != null) {
        out[key] = idStr(val)
      } else {
        out[key] = normalizeIds(val)
      }
    }
    return out
  }
  return value
}

/**
 * 在 JSON.parse 之前把 16 位及以上的整数改成字符串。
 * 雪花 ID 超过 Number.MAX_SAFE_INTEGER，先按 number 解析再 String() 会得到另一个 ID。
 */
export function parseJsonPreservingIds(text) {
  if (typeof text !== 'string' || text === '') {
    return null
  }
  const safe = text.replace(/([:\[,]\s*)(-?\d{16,})(\s*[,}\]])/g, '$1"$2"$3')
  return JSON.parse(safe)
}

/** 请求体中的雪花 ID 字段保持 string（不转成 Number） */
export function normalizeIdFieldsInBody(body) {
  if (body == null || typeof body !== 'object') {
    return body
  }
  const out = { ...body }
  for (const key of ID_KEYS) {
    if (key in out && out[key] != null && out[key] !== '') {
      out[key] = idStr(out[key])
    }
  }
  return out
}
