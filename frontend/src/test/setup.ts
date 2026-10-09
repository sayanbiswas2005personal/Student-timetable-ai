import '@testing-library/jest-dom/vitest'
import { afterEach, vi } from 'vitest'
import { cleanup } from '@testing-library/react'

/**
 * Shared test setup.
 *
 * Every test starts from a clean DOM and a clean set of mocks, so one test can never leave state
 * behind for the next. Nothing here touches a real network: the API module is mocked per test.
 */
afterEach(() => {
  cleanup()
  // Clear recorded calls but keep each module's mocked implementation, which every test sets
  // explicitly in its own beforeEach.
  vi.clearAllMocks()
  window.localStorage.clear()
  window.sessionStorage.clear()
})
