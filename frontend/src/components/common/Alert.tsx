import { AlertCircle, CheckCircle2, Info, TriangleAlert } from 'lucide-react'
import type { ReactNode } from 'react'

type Tone = 'info' | 'success' | 'warning' | 'error'

const TONES: Record<Tone, { wrapper: string; icon: typeof Info }> = {
  info: { wrapper: 'border-brand-200 bg-brand-50 text-brand-900', icon: Info },
  success: { wrapper: 'border-emerald-200 bg-emerald-50 text-emerald-900', icon: CheckCircle2 },
  warning: { wrapper: 'border-amber-300 bg-amber-50 text-amber-900', icon: TriangleAlert },
  error: { wrapper: 'border-red-300 bg-red-50 text-red-900', icon: AlertCircle },
}

interface AlertProps {
  tone?: Tone
  title?: string
  children: ReactNode
  role?: 'status' | 'alert'
}

/**
 * A message block. Errors use role="alert" so a screen reader announces them immediately;
 * everything else uses role="status".
 */
export function Alert({ tone = 'info', title, children, role }: AlertProps) {
  const config = TONES[tone]
  const Icon = config.icon
  return (
    <div
      role={role ?? (tone === 'error' ? 'alert' : 'status')}
      className={`flex gap-3 rounded-lg border px-4 py-3 text-sm ${config.wrapper}`}
    >
      <Icon aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
      <div className="min-w-0">
        {title && <p className="font-semibold">{title}</p>}
        <div className={title ? 'mt-0.5' : ''}>{children}</div>
      </div>
    </div>
  )
}
