import type { ReactNode } from 'react'

interface EmptyStateProps {
  title: string
  description?: string
  action?: ReactNode
}

/** Shown when a screen has nothing to display, with the reason spelled out. */
export function EmptyState({ title, description, action }: EmptyStateProps) {
  return (
    <div className="card flex flex-col items-center gap-3 px-6 py-12 text-center">
      <p className="text-base font-semibold text-slate-800">{title}</p>
      {description && <p className="max-w-md text-sm text-slate-600">{description}</p>}
      {action}
    </div>
  )
}
