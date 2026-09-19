interface CsrfTokenResponse {
  headerName: string
  parameterName: string
  token: string
}

interface ErrorEnvelope {
  error?: {
    code?: string
    message?: string
  }
}

/** 包含后端稳定错误码，页面可据此决定是否回到登录页或展示表单提示。 */
export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

let csrfToken: CsrfTokenResponse | null = null

/**
 * 统一发送 API 请求：始终携带 Session Cookie，并为写请求自动附加 CSRF 令牌。
 */
export async function apiRequest<T>(path: string, init: RequestInit = {}): Promise<T> {
  const method = (init.method ?? 'GET').toUpperCase()
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')

  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    const token = csrfToken ?? (await refreshCsrfToken())
    headers.set(token.headerName, token.token)
  }

  const response = await fetch(path, {
    ...init,
    method,
    headers,
    credentials: 'same-origin',
  })

  if (!response.ok) {
    throw await toApiError(response)
  }
  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}

/** 登录会轮换服务端令牌，因此登录成功后必须强制刷新本地缓存。 */
export async function refreshCsrfToken(): Promise<CsrfTokenResponse> {
  const response = await fetch('/api/auth/csrf', {
    headers: { Accept: 'application/json' },
    credentials: 'same-origin',
  })
  if (!response.ok) {
    throw await toApiError(response)
  }
  csrfToken = (await response.json()) as CsrfTokenResponse
  return csrfToken
}

export function clearCsrfToken(): void {
  csrfToken = null
}

async function toApiError(response: Response): Promise<ApiError> {
  let payload: ErrorEnvelope = {}
  try {
    payload = (await response.json()) as ErrorEnvelope
  } catch {
    // 代理或网络层可能返回非 JSON；仍给用户稳定、可理解的提示。
  }

  return new ApiError(
    response.status,
    payload.error?.code ?? 'REQUEST_FAILED',
    payload.error?.message ?? '请求失败，请稍后重试',
  )
}
