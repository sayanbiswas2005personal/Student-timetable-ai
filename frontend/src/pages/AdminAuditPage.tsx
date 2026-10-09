import { useState } from 'react'

import { adminApi } from '../api/endpoints'
import { useAsync } from '../hooks/useAsync'
import { Alert } from '../components/common/Alert'
import { Badge } from '../components/common/Badge'
import { Button } from '../components/common/Button'
import { EmptyState } from '../components/common/EmptyState'
import { Field } from '../components/common/Field'
import { Spinner } from '../components/common/Spinner'

/**
 * Audit trail and account administration.
 *
 * Everything an administrator changes is recorded here, including failed sign in attempts. That is
 * the reason the page exists: when someone asks who changed a timetable, the answer should not be
 * a guess.
 */
export default function AdminAuditPage() {
  const logs = useAsync(() => adminApi.auditLogs(200), [])
  const users = useAsync(() => adminApi.users(), [])
  const [notice, setNotice] = useState<string | null>(null)
  const [filter, setFilter] = useState('')

  const rows = (logs.data ?? []).filter((log) => {
    if (!filter.trim()) {
      return true
    }
    const needle = filter.toLowerCase()
    return (
      log.action.toLowerCase().includes(needle) ||
      (log.username ?? '').toLowerCase().includes(needle) ||
      (log.entityType ?? '').toLowerCase().includes(needle)
    )
  })

  return (
    <div className="space-y-6">
      <header>
        <h1 className="text-xl font-semibold text-slate-900">Audit and accounts</h1>
        <p className="mt-1 max-w-2xl text-sm text-slate-600">
          Administrative changes and sign in attempts, newest first.
        </p>
      </header>

      {notice && <Alert tone="success">{notice}</Alert>}

      <section className="card p-5">
        <div className="flex flex-wrap items-end justify-between gap-3">
          <Field label="Filter" hint="Matches action, username or entity.">
            {({ id }) => (
              <input
                id={id}
                type="search"
                value={filter}
                onChange={(event) => setFilter(event.target.value)}
                className="field-input"
                placeholder="TIMETABLE_PUBLISH"
              />
            )}
          </Field>
          <Button
            variant="secondary"
            onClick={() => {
              logs.reload()
              users.reload()
            }}
          >
            Refresh
          </Button>
        </div>
      </section>

      {logs.loading && <Spinner label="Loading the audit trail" />}
      {logs.error && (
        <Alert tone="error" title="Could not load the audit trail">
          {logs.error.message}
        </Alert>
      )}
      {logs.data && rows.length === 0 && <EmptyState title="No matching activity" />}

      {rows.length > 0 && (
        <div className="card overflow-x-auto">
          <table className="min-w-full divide-y divide-slate-200 text-sm">
            <caption className="sr-only">Audit trail, newest first</caption>
            <thead className="bg-slate-50">
              <tr>
                <Th>When</Th>
                <Th>User</Th>
                <Th>Action</Th>
                <Th>Entity</Th>
                <Th>Detail</Th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {rows.map((log) => (
                <tr key={log.id}>
                  <Td className="whitespace-nowrap tabular-nums">{formatTimestamp(log.occurredAt)}</Td>
                  <Td>{log.username ?? 'anonymous'}</Td>
                  <Td>
                    <Badge tone={toneForAction(log.action)}>{log.action}</Badge>
                  </Td>
                  <Td>
                    {log.entityType ?? '-'}
                    {log.entityId ? ` #${log.entityId}` : ''}
                  </Td>
                  <Td className="text-slate-600">{log.details ?? '-'}</Td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <section>
        <h2 className="mb-3 text-base font-semibold text-slate-900">Accounts</h2>
        {users.loading && <Spinner label="Loading accounts" />}
        {users.data && users.data.length === 0 && <EmptyState title="No accounts" />}
        <ul className="space-y-2">
          {(users.data ?? []).map((user) => (
            <li
              key={user.id}
              className="card flex flex-wrap items-center justify-between gap-3 p-4"
            >
              <div>
                <p className="font-medium text-slate-900">{user.username}</p>
                <p className="text-sm text-slate-600">
                  {user.displayName ?? 'No display name'}
                  {user.lastLoginAt ? ` - last signed in ${formatTimestamp(user.lastLoginAt)}` : ''}
                </p>
              </div>
              <div className="flex items-center gap-2">
                <Badge tone={user.role === 'ADMIN' ? 'brand' : 'neutral'}>{user.role}</Badge>
                <Badge tone={user.active ? 'success' : 'warning'}>
                  {user.active ? 'Active' : 'Inactive'}
                </Badge>
                <Button
                  variant="secondary"
                  className="px-3 py-1.5 text-xs"
                  onClick={() => {
                    void adminApi
                      .setUserStatus(user.id, !user.active)
                      .then(() => {
                        users.reload()
                        setNotice(`${user.username} is now ${user.active ? 'inactive' : 'active'}.`)
                      })
                      .catch((cause: unknown) =>
                        setNotice(cause instanceof Error ? cause.message : 'Could not update.'),
                      )
                  }}
                >
                  {user.active ? 'Deactivate' : 'Activate'}
                </Button>
              </div>
            </li>
          ))}
        </ul>
      </section>
    </div>
  )
}

function formatTimestamp(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return date.toLocaleString()
}

function toneForAction(action: string) {
  if (action.includes('FAILURE') || action.includes('BLOCKED')) {
    return 'danger' as const
  }
  if (action.includes('PUBLISH') || action.includes('APPROVE')) {
    return 'success' as const
  }
  if (action.includes('DELETE') || action.includes('DEACTIVATE')) {
    return 'warning' as const
  }
  return 'neutral' as const
}

function Th({ children }: { children: React.ReactNode }) {
  return (
    <th scope="col" className="px-4 py-3 text-left text-xs font-semibold text-slate-600 uppercase">
      {children}
    </th>
  )
}

function Td({ children, className = '' }: { children: React.ReactNode; className?: string }) {
  return <td className={`px-4 py-3 ${className}`}>{children}</td>
}