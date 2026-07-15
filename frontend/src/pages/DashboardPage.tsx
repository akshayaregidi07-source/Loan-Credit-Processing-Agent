import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useApi } from '../contexts/ApiContext'
import { listApplications } from '../services/applicationService'
import type { ApplicationSummaryResponse } from '../services/applicationService'
import LoadingSpinner from '../components/common/LoadingSpinner'
import ErrorMessage from '../components/common/ErrorMessage'
import type { AxiosError } from 'axios'

/**
 * Applicant dashboard — Task 17.5.
 *
 * Shows the applicant's own submitted applications with links to the status
 * page for each. Requirements: 11.5
 */
export default function DashboardPage() {
  const api = useApi()
  const [apps, setApps] = useState<ApplicationSummaryResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<number | string | null>(null)

  useEffect(() => {
    let cancelled = false
    listApplications(api, 0, 50)
      .then((page) => { if (!cancelled) { setApps(page.content); setLoading(false) } })
      .catch((err: AxiosError) => {
        if (!cancelled) {
          setError(err.response?.status ?? 'Failed to load applications.')
          setLoading(false)
        }
      })
    return () => { cancelled = true }
  }, [api])

  if (loading) return <LoadingSpinner label="Loading your applications…" />

  return (
    <div className="mx-auto max-w-3xl px-4 py-8">
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-2xl font-bold text-purple-700">My Applications</h1>
        <Link
          to="/applicant/apply"
          aria-label="Start a new loan application"
          className="rounded-md bg-purple-600 px-4 py-2 text-sm font-semibold text-white hover:bg-purple-700 focus:outline-none focus:ring-2 focus:ring-purple-500 focus:ring-offset-2"
        >
          New Application
        </Link>
      </div>

      {error && <ErrorMessage error={error} />}

      {!error && apps.length === 0 && (
        <p className="text-sm text-gray-500">
          You have not submitted any applications yet.{' '}
          <Link to="/applicant/apply" className="text-purple-600 underline hover:text-purple-800">
            Start one now
          </Link>
          .
        </p>
      )}

      {apps.length > 0 && (
        <ul className="space-y-3" aria-label="Application list">
          {apps.map((app) => (
            <li
              key={app.applicationId}
              className="flex items-center justify-between rounded-md border border-gray-200 bg-white px-4 py-3 shadow-sm"
            >
              <div>
                <p className="text-sm font-medium text-gray-800 truncate max-w-xs">
                  {app.loanPurpose}
                </p>
                <p className="text-xs text-gray-500 mt-0.5">
                  £{app.requestedAmount.toLocaleString()} ·{' '}
                  {new Date(app.createdAt).toLocaleDateString()}
                </p>
              </div>
              <div className="flex items-center gap-3">
                <span className="rounded-full bg-gray-100 px-2.5 py-0.5 text-xs font-medium text-gray-700">
                  {app.status}
                </span>
                <Link
                  to={`/applicant/status/${app.applicationId}`}
                  aria-label={`View status for application ${app.applicationId}`}
                  className="text-sm text-purple-600 hover:underline focus:outline-none focus:underline"
                >
                  View
                </Link>
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
