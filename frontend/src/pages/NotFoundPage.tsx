import { Link } from 'react-router-dom'

import { EmptyState } from '../components/common/EmptyState'

export default function NotFoundPage() {
  return (
    <div className="flex min-h-screen items-center justify-center px-4">
      <EmptyState
        title="Page not found"
        description="That address does not match any screen in this application."
        action={
          <Link to="/" className="btn-primary mt-2">
            Back to the dashboard
          </Link>
        }
      />
    </div>
  )
}
