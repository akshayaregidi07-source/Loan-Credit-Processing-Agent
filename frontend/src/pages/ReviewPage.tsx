import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useApi } from '../contexts/ApiContext'
import { getReview, submitDecision } from '../services/applicationService'
import type { ApplicationReviewResponse, UnderwriterDecisionRequest } from '../services/applicationService'
import type { AxiosError } from 'axios'
import LoadingSpinner from '../components/common/LoadingSpinner'
import ErrorMessage from '../components/common/ErrorMessage'
import CreditScoreCard from '../components/underwriter/CreditScoreCard'
import RecommendationBadge from '../components/underwriter/RecommendationBadge'
import FairnessResultPanel from '../components/underwriter/FairnessResultPanel'
import DecisionForm from '../components/underwriter/DecisionForm'

/**
 * Full application review page — Task 18.5.
 *
 * Composes CreditScoreCard, RecommendationBadge, FairnessResultPanel,
 * document metadata list, application form data (read-only), and DecisionForm.
 * Surfaces a FAIRNESS_FLAG banner when present.
 * On successful decision → redirects to worklist (Req 11.7, 11.8, 11.10).
 */
export default function ReviewPage() {
  const { id } = useParams<{ id: string }>()
  const api = useApi()
  const navigate = useNavigate()

  const [review, setReview] = useState<ApplicationReviewResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [fetchError, setFetchError] = useState<number | string | null>(null)
  const [submitError, setSubmitError] = useState<number | string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [submitted, setSubmitted] = useState(false)

  useEffect(() => {
    if (!id) return
    let cancelled = false
    getReview(api, id)
      .then((r) => { if (!cancelled) { setReview(r); setLoading(false) } })
      .catch((err: AxiosError) => {
        if (!cancelled) {
          setFetchError(err.response?.status ?? 'Failed to load application.')
          setLoading(false)
        }
      })
    return () => { cancelled = true }
  }, [api, id])

  async function handleDecision(request: UnderwriterDecisionRequest) {
    if (!id) return
    setSubmitError(null)
    setSubmitting(true)
    try {
      await submitDecision(api, id, request)
      setSubmitted(true)
      setTimeout(() => navigate('/underwriter/worklist', { replace: true }), 1500)
    } catch (err) {
      const axiosErr = err as AxiosError
      setSubmitError(axiosErr.response?.status ?? 'Decision submission failed.')
    } finally {
      setSubmitting(false)
    }
  }

  if (loading) return <LoadingSpinner label="Loading application review…" />
  if (fetchError) return <div className="p-8"><ErrorMessage error={fetchError} /></div>
  if (!review) return null

  const { formData: fd, documents, creditScore, recommendation, fairnessResult } = review

  return (
    <div className="mx-auto max-w-4xl px-4 py-8">
      {/* Header */}
      <div className="mb-6 flex items-start justify-between">
        <div>
          <h1 className="text-2xl font-bold text-purple-700">Application Review</h1>
          <p className="mt-1 font-mono text-xs text-gray-400">{review.applicationId}</p>
        </div>
        <span className="rounded-full bg-gray-100 px-3 py-1 text-xs font-semibold text-gray-700">
          {review.status}
        </span>
      </div>

      {/* Fairness flag banner */}
      {review.hasFairnessFlag && (
        <div role="alert" aria-live="assertive" className="mb-6 rounded-md border border-orange-300 bg-orange-50 px-4 py-3">
          <p className="font-semibold text-orange-800">⚠ Fairness Flag: {review.fairnessFlagReason}</p>
          <p className="mt-1 text-sm text-orange-700">
            A significant scoring difference was detected between the original and anonymised assessments.
            Review carefully before deciding.
          </p>
        </div>
      )}

      {/* Success banner */}
      {submitted && (
        <div role="status" className="mb-6 rounded-md border border-green-300 bg-green-50 px-4 py-3 text-sm text-green-800 font-semibold">
          ✓ Decision recorded. Redirecting to worklist…
        </div>
      )}

      <div className="grid grid-cols-1 gap-5 lg:grid-cols-2">
        {/* Left column */}
        <div className="space-y-5">
          {/* Application form data — read-only */}
          <section aria-labelledby="app-data-heading" className="rounded-md border border-gray-200 bg-white p-5">
            <h2 id="app-data-heading" className="mb-3 text-sm font-semibold uppercase tracking-wide text-gray-500">
              Application Details
            </h2>
            <dl className="space-y-2 text-sm">
              <Row label="Requested amount" value={`£${fd.requestedAmount.toLocaleString()}`} />
              <Row label="Loan purpose" value={fd.loanPurpose} />
              <Row label="Employment" value={fd.employmentStatus} />
              <Row label="Gross monthly income" value={`£${fd.grossMonthlyIncome.toLocaleString()}`} />
              <Row label="Monthly debt obligations" value={`£${fd.totalMonthlyDebt.toLocaleString()}`} />
            </dl>
          </section>

          {/* Documents */}
          <section aria-labelledby="docs-heading" className="rounded-md border border-gray-200 bg-white p-5">
            <h2 id="docs-heading" className="mb-3 text-sm font-semibold uppercase tracking-wide text-gray-500">
              Documents ({documents.length})
            </h2>
            <ul className="space-y-2">
              {documents.map((d) => (
                <li key={d.id} className="flex items-center justify-between text-sm">
                  <span className="text-gray-700">{d.documentType.replace(/_/g, ' ')}</span>
                  <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${
                    d.validationStatus === 'PASSED'
                      ? 'bg-green-100 text-green-700'
                      : d.validationStatus === 'FAILED'
                      ? 'bg-red-100 text-red-700'
                      : 'bg-gray-100 text-gray-600'
                  }`}>
                    {d.validationStatus}
                  </span>
                </li>
              ))}
            </ul>
          </section>

          {/* Decision form */}
          {!submitted && (
            <>
              {submitError && <ErrorMessage error={submitError} />}
              <DecisionForm
                systemRecommendation={recommendation?.recommendationValue ?? null}
                onSubmit={handleDecision}
                submitting={submitting}
              />
            </>
          )}
        </div>

        {/* Right column */}
        <div className="space-y-5">
          {creditScore && <CreditScoreCard creditScore={creditScore} />}
          {recommendation && <RecommendationBadge recommendation={recommendation} />}
          {fairnessResult && <FairnessResultPanel fairnessResult={fairnessResult} />}
        </div>
      </div>
    </div>
  )
}

function Row({ label, value }: { readonly label: string; readonly value: string }) {
  return (
    <div className="flex justify-between">
      <dt className="text-gray-500">{label}</dt>
      <dd className="font-medium text-gray-800">{value}</dd>
    </div>
  )
}
