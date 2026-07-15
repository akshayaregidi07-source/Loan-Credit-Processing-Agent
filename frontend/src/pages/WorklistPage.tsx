import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useApi } from '../contexts/ApiContext'
import { listApplications } from '../services/applicationService'
import type { ApplicationSummaryResponse } from '../services/applicationService'
import LoadingSpinner from '../components/common/LoadingSpinner'
import ErrorMessage from '../components/common/ErrorMessage'
import type { AxiosError } from 'axios'

/**
 * Underwriter worklist — Task 18.1.
 *
 * Paginated table of applications in AWAITING_UNDERWRITER_REVIEW.
 * Prev/Next pagination with ARIA labels. Empty-state message.
 * Requirements: 11.6
 */
export default function WorklistPage() {
  const api = useApi()
  const [apps, setApps] = useState<ApplicationSummaryResponse[]>([])
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<number | string | null>(null)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    listApplications(api, page, 20)
      .then((p) => {
        if (!cancelled) {
          setApps(p.content)
          setTotalPages(p.totalPages)
          setLoading(false)
        }
      })
      .catch((err: AxiosError) => {
        if (!cancelled) {
          setError(err.response?.status ?? 'Failed to load worklist.')
          setLoading(false)
        }
      })
    return () => { cancelled = true }
  }, [api, page])

  return (
    <div className="mx-auto max-w-5xl px-4 py-8">
      <h1 className="mb-6 text-2xl font-bold text-purple-700">Underwriter Worklist</h1>

      {error && <ErrorMessage error={error} />}

      {loading && <LoadingSpinner label="Loading worklist…" />}

      {!loading && !error && apps.length === 0 && (
        <p className="text-sm text-gray-500">No applications are currently awaiting review.</p>
      )}

      {!loading && !error && apps.length > 0 && (
        <>
          <div className="overflow-x-auto rounded-md border border-gray-200">
            <table className="min-w-full divide-y divide-gray-100 text-sm" aria-label="Applications awaiting review">
              <thead className="bg-gray-50">
                <tr>
                  {['Application ID', 'Loan Purpose', 'Amount (£)', 'Submitted', 'Action'].map((h) => (
                    <th key={h} scope="col" className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide text-gray-500">
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-50 bg-white">
                {apps.map((app) => (
                  <tr key={app.applicationId} className="hover:bg-gray-50">
                    <td className="px-4 py-3 font-mono text-xs text-gray-600">
                      {app.applicationId.slice(0, 8)}…
                    </td>
                    <td className="px-4 py-3 max-w-xs truncate text-gray-800">{app.loanPurpose}</td>
                    <td className="px-4 py-3 text-gray-800">{app.requestedAmount.toLocaleString()}</td>
                    <td className="px-4 py-3 text-gray-500">{new Date(app.createdAt).toLocaleDateString()}</td>
                    <td className="px-4 py-3">
                      <Link
                        to={`/underwriter/review/${app.applicationId}`}
                        aria-label={`Review application ${app.applicationId}`}
                        className="font-medium text-purple-600 hover:underline focus:outline-none focus:underline"
                      >
                        Review
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {totalPages > 1 && (
            <div className="mt-4 flex items-center justify-between" role="navigation" aria-label="Pagination">
              <button
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                disabled={page === 0}
                aria-label="Previous page"
                className="rounded-md border border-gray-300 px-3 py-1.5 text-sm text-gray-600 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-40 focus:outline-none focus:ring-2 focus:ring-purple-500"
              >
                ← Previous
              </button>
              <span className="text-sm text-gray-500">
                Page {page + 1} of {totalPages}
              </span>
              <button
                onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                disabled={page >= totalPages - 1}
                aria-label="Next page"
                className="rounded-md border border-gray-300 px-3 py-1.5 text-sm text-gray-600 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-40 focus:outline-none focus:ring-2 focus:ring-purple-500"
              >
                Next →
              </button>
            </div>
          )}
        </>
      )}
    </div>
  )
}
