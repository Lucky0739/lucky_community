import { normalizeIdFieldsInBody, normalizeIds, parseJsonPreservingIds } from '@/utils/id'

async function readJson(response) {
  const text = await response.text()
  if (!text) {
    return null
  }
  try {
    return parseJsonPreservingIds(text)
  } catch {
    return null
  }
}

const JSON_HEADERS = { 'Content-Type': 'application/json' }

/**
 * 走 Vite 代理到网关。credentials 带上 refresh Cookie（路径 /user）。
 */
export async function request(path, { method = 'GET', body, token } = {}) {
  const headers = { ...JSON_HEADERS }
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }
  const response = await fetch(path, {
    method,
    headers,
    credentials: 'include',
    body: body == null ? undefined : JSON.stringify(normalizeIdFieldsInBody(body)),
  })
  const payload = await readJson(response)
  if (!response.ok || !payload || payload.success !== true) {
    const message = payload?.errorMsg || `请求失败（${response.status}）`
    throw new Error(message)
  }
  return normalizeIds(payload.data)
}

/**
 * 搜索服务直接返回 PageResponse，没有 Result.success 外壳。
 */
export async function searchPosts(keyword, pageNo = 1) {
  const response = await fetch('/search/post', {
    method: 'POST',
    headers: JSON_HEADERS,
    credentials: 'include',
    body: JSON.stringify({ keyword, pageNo }),
  })
  const payload = await readJson(response)
  if (!response.ok || !payload || payload.success === false) {
    throw new Error(payload?.errorMsg || `搜索失败（${response.status}）`)
  }
  return {
    records: normalizeIds(payload.records) ?? [],
    total: payload.total ?? 0,
    pageNo: payload.pageNo ?? pageNo,
    pageSize: payload.pageSize ?? 10,
  }
}

/** 返回 data 与分页 total（Result.success(data, total)） */
export async function requestPage(path, { method = 'GET', body, token } = {}) {
  const headers = { ...JSON_HEADERS }
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }
  const response = await fetch(path, {
    method,
    headers,
    credentials: 'include',
    body: body == null ? undefined : JSON.stringify(normalizeIdFieldsInBody(body)),
  })
  const payload = await readJson(response)
  if (!response.ok || !payload || payload.success !== true) {
    const message = payload?.errorMsg || `请求失败（${response.status}）`
    throw new Error(message)
  }
  return { records: normalizeIds(payload.data) ?? [], total: payload.total ?? 0 }
}

/** 上传图片。不要自己设 Content-Type，让浏览器带上 multipart 边界。 */
export async function uploadFile(path, file, token) {
  const body = new FormData()
  body.append('file', file)
  const headers = {}
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }
  const response = await fetch(path, {
    method: 'POST',
    headers,
    credentials: 'include',
    body,
  })
  const payload = await readJson(response)
  if (!response.ok || !payload || payload.success !== true) {
    const message = payload?.errorMsg || `上传失败（${response.status}）`
    throw new Error(message)
  }
  return payload.data
}
