interface SpinnerProps {
  label?: string
}

/** A single loading indicator with a readable label, used wherever data is pending. */
export function Spinner({ label = 'Loading' }: SpinnerProps) {
  return (
    <div role="status" className="flex items-center gap-2 text-sm text-slate-600">
      <span
        aria-hidden="true"
        className="size-4 animate-spin rounded-full border-2 border-slate-400 border-t-transparent"
      />
      <span>{label}</span>
    </div>
  )
}
