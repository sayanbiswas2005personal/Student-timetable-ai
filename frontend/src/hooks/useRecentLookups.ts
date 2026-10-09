import { useState } from 'react'

import type { LookupResponse } from '../types/api'

const STORAGE_KEY = 'timetable.recent-lookups'
const MAX_ENTRIES = 8

/**
 * Recent lookups, kept in this browser only.
 *
 * Deliberately not sent to the server: a list of who staff have been asking about is student data,
 * and the backend has no reason to hold it. Clearing the browser clears the history.
 */
export interface RecentLookup {
  registrationNumber: string
  label: string
  lookedUpAt: string
}

export function readRecentLookups(): RecentLookup[] {
  try {
    const raw = window.sessionStorage.getItem(STORAGE_KEY)
    if (!raw) {
      return []
    }
    const parsed: unknown = JSON.parse(raw)
    return Array.isArray(parsed) ? (parsed as RecentLookup[]) : []
  } catch {
    return []
  }
}

export function rememberLookup(response: LookupResponse): RecentLookup[] {
  if (!response.student) {
    return readRecentLookups()
  }
  const entry: RecentLookup = {
    registrationNumber: response.student.registrationNumber,
    label: `${response.student.programCode} sem ${response.student.semesterNumber} section ${response.student.sectionName}`,
    lookedUpAt: new Date().toISOString(),
  }
  const withoutDuplicate = readRecentLookups().filter(
    (item) => item.registrationNumber !== entry.registrationNumber,
  )
  const next = [entry, ...withoutDuplicate].slice(0, MAX_ENTRIES)
  try {
    window.sessionStorage.setItem(STORAGE_KEY, JSON.stringify(next))
  } catch {
    // Private browsing can refuse storage; the feature simply stays empty.
  }
  return next
}

export function clearRecentLookups(): void {
  try {
    window.sessionStorage.removeItem(STORAGE_KEY)
  } catch {
    // Nothing to do: the history is already unavailable.
  }
}

/**
 * Convenience hook so components do not touch session storage directly.
 *
 * Returns the entries, a function to record one after a lookup, and a function to clear them.
 */
export function useRecentLookups(): {
  entries: RecentLookup[]
  remember: (response: LookupResponse) => void
  clear: () => void
} {
  const [entries, setEntries] = useState<RecentLookup[]>(() => readRecentLookups())

  return {
    entries,
    remember: (response) => setEntries(rememberLookup(response)),
    clear: () => {
      clearRecentLookups()
      setEntries([])
    },
  }
}