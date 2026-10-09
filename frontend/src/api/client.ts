import axios, { AxiosError, type AxiosInstance } from 'axios'

import type { ApiError } from '../types/api'

/**
 * The API base URL is configuration, never a hard coded host.
 *
 * In development the value is `/api`, which the Vite dev server proxies to the backend. That keeps
 * the browser on a single origin, so the session cookie and the CSRF header behave exactly as they
 * do in production.
 */
export const API_BASE_URL: string = import.meta.env.VITE_API_BASE_URL ?? '/api'

export const api: AxiosInstance = axios.create({
  baseURL: API_BASE_URL,
  withCredentials: true,
  timeout: 30_000,
  headers: { Accept: 'application/json' },
})

/**
 * Reads the CSRF cookie written by the backend and returns it for the `X-XSRF-TOKEN` header.
 *
 * Spring Security's cookie repository deliberately marks this cookie readable by scripts; the
 * session cookie itself stays HttpOnly.
 */
function readCsrfToken(): string | null {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/)
  return match?.[1] ? decodeURIComponent(match[1]) : null
}

api.interceptors.request.use((config) => {
  const method = (config.method ?? 'get').toUpperCase()
  if (method !== 'GET' && method !== 'HEAD' && method !== 'OPTIONS') {
    const token = readCsrfToken()
    if (token) {
      config.headers.set('X-XSRF-TOKEN', token)
    }
  }
  return config
})

/** A failed request, already reduced to what the UI needs to show a staff member. */
export class ApiRequestError extends Error {
  readonly code: string
  readonly status: number
  readonly details: string[]

  constructor(status: number, body: ApiError | null, fallback: string) {
    super(body?.message ?? fallback)
    this.name = 'ApiRequestError'
    this.status = status
    this.code = body?.code ?? 'NETWORK_ERROR'
    this.details = body?.details ?? []
  }

  get isUnauthorized(): boolean {
    return this.status === 401
  }

  get isForbidden(): boolean {
    return this.status === 403
  }

  get isRateLimited(): boolean {
    return this.status === 429
  }
}

/**
 * Fetches a CSRF token once, before the first write of a session.
 *
 * Without this the first login would be rejected, because no cookie exists yet for the interceptor
 * to echo back.
 */
export async function primeCsrfCookie(): Promise<void> {
  try {
    await api.get('/auth/csrf')
  } catch {
    // A failure here is not fatal: the login attempt itself will surface a clear error.
  }
}

export function toApiError(error: unknown): ApiRequestError {
  if (error instanceof ApiRequestError) {
    return error
  }
  if (error instanceof AxiosError) {
    const status = error.response?.status ?? 0
    const body = error.response?.data as ApiError | undefined
    if (status === 0) {
      return new ApiRequestError(
        0,
        null,
        'Could not reach the server. Check your connection and try again.',
      )
    }
    return new ApiRequestError(status, body ?? null, 'The request could not be completed.')
  }
  return new ApiRequestError(0, null, 'Something went wrong. Please try again.')
}