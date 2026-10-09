import { useEffect, useState } from 'react'

import { lookupApi } from '../api/endpoints'
import type { CollegeClock } from '../types/api'

/**
 * The college local date and time, from the server.
 *
 * Deliberately not computed from the browser clock: the answer changes with the college timezone
 * and the answer must be the same for every device in the room. Polled once a minute, which is
 * well under the shortest period in a timetable.
 */
export function useCollegeClock(): CollegeClock | null {
  const [clock, setClock] = useState<CollegeClock | null>(null)

  useEffect(() => {
    let cancelled = false

    const refresh = async () => {
      try {
        const next = await lookupApi.clock()
        if (!cancelled) {
          setClock(next)
        }
      } catch {
        // A transient failure leaves the previous reading visible rather than flashing an error.
      }
    }

    void refresh()
    const timer = window.setInterval(() => void refresh(), 60_000)
    return () => {
      cancelled = true
      window.clearInterval(timer)
    }
  }, [])

  return clock
}