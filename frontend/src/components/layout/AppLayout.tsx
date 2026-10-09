import { CalendarClock, GraduationCap, LogOut, ShieldCheck, Upload, Users } from 'lucide-react'
import { NavLink, Outlet } from 'react-router-dom'

import { useAuth } from '../../api/useAuth'
import { useCollegeClock } from '../../hooks/useCollegeClock'
import { Badge } from '../common/Badge'

const STAFF_LINKS = [
  { to: '/', label: 'Dashboard', icon: GraduationCap, end: true },
  { to: '/lookup', label: 'Student lookup', icon: Users, end: false },
]

const ADMIN_LINKS = [
  { to: '/admin/students', label: 'Students', icon: Users, end: false },
  { to: '/admin/timetables', label: 'Timetables', icon: CalendarClock, end: false },
  { to: '/admin/imports', label: 'PDF import', icon: Upload, end: false },
  { to: '/admin/audit', label: 'Audit log', icon: ShieldCheck, end: false },
]

/**
 * Application shell: a skip link, the navigation and a live clock.
 *
 * The clock matters here: staff use this to answer "what is happening now", and a device that is
 * minutes out would quietly give the wrong answer for the whole session.
 */
export default function AppLayout() {
  const { user, isAdmin, logout } = useAuth()
  const clock = useCollegeClock()

  const links = isAdmin ? [...STAFF_LINKS, ...ADMIN_LINKS] : STAFF_LINKS

  return (
    <div className="min-h-screen">
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-50
                   focus:rounded-lg focus:bg-white focus:px-4 focus:py-2 focus:shadow"
      >
        Skip to main content
      </a>

      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-7xl flex-wrap items-center justify-between gap-3 px-4 py-3 sm:px-6">
          <div className="flex items-center gap-3">
            <span
              aria-hidden="true"
              className="flex size-9 items-center justify-center rounded-lg bg-brand-600 text-white"
            >
              <CalendarClock className="size-5" />
            </span>
            <div>
              <p className="text-sm font-semibold leading-tight text-slate-900">
                College Timetable Lookup
              </p>
              <p className="text-xs leading-tight text-slate-500">
                Expected schedule, not attendance
              </p>
            </div>
          </div>

          <div className="flex items-center gap-3">
            <CollegeClock clock={clock} />
            <div className="hidden items-center gap-2 sm:flex">
              <span className="text-sm text-slate-700">{user?.displayName ?? user?.username}</span>
              <Badge tone={isAdmin ? 'brand' : 'neutral'}>{user?.role}</Badge>
            </div>
            <button type="button" onClick={() => void logout()} className="btn-secondary px-3 py-2">
              <LogOut aria-hidden="true" className="size-4" />
              <span className="hidden sm:inline">Sign out</span>
              <span className="sr-only sm:hidden">Sign out</span>
            </button>
          </div>
        </div>

        <nav aria-label="Main" className="border-t border-slate-200">
          <ul className="mx-auto flex max-w-7xl gap-1 overflow-x-auto px-2 sm:px-4">
            {links.map(({ to, label, icon: Icon, end }) => (
              <li key={to}>
                <NavLink
                  to={to}
                  end={end}
                  className={({ isActive }) =>
                    [
                      'flex items-center gap-2 whitespace-nowrap border-b-2 px-3 py-3 text-sm font-medium transition-colors duration-150',
                      isActive
                        ? 'border-brand-600 text-brand-700'
                        : 'border-transparent text-slate-600 hover:border-slate-300 hover:text-slate-900',
                    ].join(' ')
                  }
                >
                  <Icon aria-hidden="true" className="size-4" />
                  {label}
                </NavLink>
              </li>
            ))}
          </ul>
        </nav>
      </header>

      <main id="main" className="mx-auto max-w-7xl px-4 py-6 sm:px-6">
        <Outlet />
      </main>
    </div>
  )
}

function CollegeClock({ clock }: { clock: { date: string; time: string; dayName: string } | null }) {
  return (
    <div
      className="rounded-lg border border-slate-200 bg-slate-50 px-3 py-1.5 text-right"
      aria-live="off"
    >
      <p className="text-sm font-semibold tabular-nums text-slate-900">
        {clock ? `${clock.dayName.slice(0, 3)} ${clock.date}` : '--:--'}
      </p>
      <p className="text-xs tabular-nums text-slate-600">
        {clock ? clock.time : 'Connecting'}
        <span className="ml-1 text-slate-400">college time</span>
      </p>
    </div>
  )
}