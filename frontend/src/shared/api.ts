/** Same-origin JSON client for /api/v1 with session cookies and the SPA CSRF header. */

export type FieldErrors = Record<string, string>

export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly fieldErrors: FieldErrors
  readonly body: unknown

  constructor(status: number, code: string, detail: string, fieldErrors: FieldErrors = {}, body?: unknown) {
    super(detail)
    this.status = status
    this.code = code
    this.fieldErrors = fieldErrors
    this.body = body
  }
}

export type Page<T> = { items: T[]; page: number; size: number; total: number }

const BASE = '/api/v1'

function readCookie(name: string): string | undefined {
  return document.cookie
    .split('; ')
    .find((part) => part.startsWith(`${name}=`))
    ?.slice(name.length + 1)
}

async function csrfToken(): Promise<string | undefined> {
  const existing = readCookie('XSRF-TOKEN')
  if (existing) return decodeURIComponent(existing)
  await fetch(`${BASE}/auth/csrf`, { credentials: 'same-origin' })
  const issued = readCookie('XSRF-TOKEN')
  return issued && decodeURIComponent(issued)
}

type RequestOptions = { method?: string; body?: unknown; rawBody?: BodyInit; headers?: Record<string, string> }

export async function request<T>(path: string, { method = 'GET', body, rawBody, headers = {} }: RequestOptions = {}): Promise<T> {
  const finalHeaders: Record<string, string> = { Accept: 'application/json', ...headers }
  if (method !== 'GET') {
    const token = await csrfToken()
    if (token) finalHeaders['X-XSRF-TOKEN'] = token
  }
  if (body !== undefined) finalHeaders['Content-Type'] = 'application/json'
  const response = await fetch(`${BASE}${path}`, {
    method,
    credentials: 'same-origin',
    headers: finalHeaders,
    body: rawBody ?? (body === undefined ? undefined : JSON.stringify(body)),
  })
  if (response.status === 204) return undefined as T
  const text = await response.text()
  const json: unknown = text ? JSON.parse(text) : undefined
  if (!response.ok) {
    const problem = (json ?? {}) as { code?: string; detail?: string; fieldErrors?: FieldErrors }
    throw new ApiError(response.status, problem.code ?? 'HTTP_' + response.status, problem.detail ?? response.statusText, problem.fieldErrors ?? {}, json)
  }
  return json as T
}

export const api = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body?: unknown) => request<T>(path, { method: 'POST', body: body ?? {} }),
  put: <T>(path: string, body?: unknown) => request<T>(path, { method: 'PUT', body: body ?? {} }),
  delete: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
}

export function query(params: Record<string, string | number | undefined | null>): string {
  const search = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') search.set(key, String(value))
  })
  const text = search.toString()
  return text ? `?${text}` : ''
}
