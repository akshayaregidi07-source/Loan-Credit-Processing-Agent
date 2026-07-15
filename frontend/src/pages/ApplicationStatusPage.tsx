import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { useApi } from '../contexts/ApiContext'
import { getStatus } from '../services/applicationService'
import type { ApplicationStatusResponse } from '../services/applicationService'
import LoadingSpinner from '../components/common/LoadingSpinner'
import ErrorMessage from '../components/common/ErrorMessage'
import type { AxiosError } from 'axios'

/**
 * Application status page — Task 17.5.
 *
 * Shows the current pipeline status for the applicant's own application.
 * When status is DECISION_RECORDED, displays the decision value and timestamp.
 * Never shows credit score, fairness details, or underwriter justification
 * (Requirement 10.5, 11.5).
 */
export default function ApplicationStatusPage() {
  const { id } = useParams<{ id: string }>()
  const api = useApi()
  const [data, setData] = useState<ApplicationStatusResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<number | string | null>(null)

  useEffect(() => {
    if (!id) return
    let cancelled = false
    setLoading(true)
    getStatus(api, id)
      .then((res) => { if (!cancelled) { setData(res); setLoading(false) } })
      .catch((err: AxiosError) => {
        if (!cancelled) {
          setError(err.response?.status ?? 'Failed to load status.')
          setLoading(false)
        }
      })
    return () => { cancelled = true }
  }, [api, id])

  if (loading) return <LoadingSpinner label="Loading application status…" />
  if (error) return <div className="p-8"><ErrorMessage error={error} /></div>
  if (!data) return null

  const statusLabels: Record<string, string> = {
    SUBMITTED: 'Submitted — awaiting document validation',
    DOCUMENT_INCOMPLETE: 'Documents incomplete — please upload missing documents',
    DOCUMENT_INVALID: 'Document invalid — please re-upload a valid file',
    DOCUMENT_INCONSISTENT: 'Documents inconsistent — applicant name mismatch detected',
    DOCUMENTS_VERIFIED: 'Documents verified — credit assessment in progress',
    PROCESSING: 'Processing — credit score being calculated',
    SCORING_ERROR: 'Scoring error — please contact support',
    AWAITING_UNDERWRITER_REVIEW: 'Under review by an underwriter',
    DECISION_RECORDED: 'Decision recorded',
  }

  const decisionLabels: Record<string, string> = {
    APPROVED: 'Approved',
    DECLINED: 'Declined',
    REFERRED_FOR_FURTHER_REVIEW: 'Referred for further review',
  }

  return (
    <div className="mx-auto max-w-lg px-4 py-8">
      <h1 className="mb-6 text-2xl font-bold text-purple-700">Application Status</h1>

      <dl className="divide-y divide-gray-100 rounded-md border border-gray-200 bg-white">
        <Row label="Application ID" value={data.applicationId} />
        <Row label="Status" value={statusLabels[data.status] ?? data.status} />
        <Row
          label="Last updated"
          value={new Date(data.lastUpdatedAt).toLocaleString()}
        />
        {data.status === 'DECISION_RECORDED' && data.decisionValue && (
          <>
            <Row
              label="Decision"
              value={decisionLabels[data.decisionValue] ?? data.decisionValue}
              highlight={data.decisionValue === 'APPROVED' ? 'green' : 'red'}
            />
            {data.decisionTimestamp && (
              <Row
                label="Decision date"
                value={new Date(data.decisionTimestamp).toLocaleString()}
              />
            )}
          </>
        )}
      </dl>
    </div>
  )
}

interface RowProps {
  readonly label: string
  readonly value: string
  readonly highlight?: 'green' | 'red'
}

function Row({ label, value, highlight }: RowProps) {
  const valueClass = highlight === 'green'
    ? 'font-semibold text-green-700'
    : highlight === 'red'
    ? 'font-semibold text-red-700'
    : 'text-gray-800'

  return (
    <div className="flex justify-between px-4 py-3">
      <dt className="text-sm font-medium text-gray-500">{label}</dt>
      <dd className={`text-sm ${valueClass}`}>{value}</dd>
    </div>
  )
}
