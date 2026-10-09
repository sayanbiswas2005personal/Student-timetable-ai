import { useCallback, useEffect, useState } from 'react'

import { ApiRequestError, toApiError } from '../api/client'

export interface AsyncState<T> {
  data: T | null
  error: ApiRequestError | null
  loading: boolean
  reload: () => void
}

/**
 * Runs an async function and exposes loading and error state.
 *
 * Every screen in this app needs the same three things, and every one of them must also cope with
 * a request that is still in flight when the user navigates away. The cancellation flag below
 * makes sure a late response never overwrites a newer one.
 */
export function useAsync<T>(
  action: () => Promise<T>,
  deps: readonly unknown[],
  options: { enabled?: boolean } = {},
): AsyncState<T> {
  const enabled = options.enabled ?? true
  const [data, setData] = useState<T | null>(null)
  const [error, setError] = useState<ApiRequestError | null>(null)
  const [loading, setLoading] = useState(false)
  const [nonce, setNonce] = useState(0)

  const reload = useCallback(() => setNonce((value) => value + 1), [])

  useEffect(() => {
    if (!enabled) {
      return
    }
    let cancelled = false
    setLoading(true)
    setError(null)

    void action()
      .then((result) => {
        if (!cancelled) {
          setData(result)
          setLoading(false)
        }
      })
      .catch((cause: unknown) => {
        if (!cancelled) {
          setError(toApiError(cause))
          setLoading(false)
        }
      })

    return () => {
      cancelled = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, nonce, enabled])

  return { data, error, loading, reload }
}