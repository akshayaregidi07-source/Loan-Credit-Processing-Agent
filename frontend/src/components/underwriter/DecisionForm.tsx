import { type FormEvent, useState } from 'react'
import type { DecisionValue, RecommendationValue, UnderwriterDecisionRequest } from '../../services/applicationService'

interface DecisionFormProps {
  readonly systemRecommendation: RecommendationValue | null
  readonly onSubmit: (request: UnderwriterDecisionRequest) => Promise<void>
  readonly submitting: boolean
}

const DECISION_OPTIONS: { value: DecisionValue; label: string }[] = [
  { value: 'APPROVED',                  label: 'Approve'                   },
  { value: 'DECLINED',                  label: 'Decline'                   },
  { value: 'REFERRED_FOR_FURTHER_REVIEW', label: 'Refer for further review' },
]

const REC_TO_DECISION: Partial<Record<RecommendationValue, DecisionValue>> = {
  APPROVE: 'APPROVED',
  DECLINE: 'DECLINED',
  REFER:   'REFERRED_FOR_FURTHER_REVIEW',
}

/**
 * Underwriter decision form — Task 18.3.
 *
 * Submit is disabled until justificationText ≥ 20 characters (Req 11.8).
 * Shows an optional override-reason field when decision differs from the
 * system recommendation. All fields have ARIA labels (Req 11.9).
 *
 * Property 23: submit button enabled iff justification.length ≥ 20.
 */
export default function DecisionForm({
  systemRecommendation,
  onSubmit,
  submitting,
}: DecisionFormProps) {
  const [decisionValue, setDecisionValue] = useState<DecisionValue | ''>('')
  const [justification, setJustification] = useState('')
  const [overrideReason, setOverrideReason] = useState('')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

  const systemDecision = systemRecommendation ? REC_TO_DECISION[systemRecommendation] : undefined
  const isOverride = decisionValue !== '' && decisionValue !== systemDecision

  const justificationLen = justification.length
  const canSubmit = !submitting && decisionValue !== '' && justificationLen >= 20

  function validate(): boolean {
    const errors: Record<string, string> = {}
    if (!decisionValue) errors['decisionValue'] = 'Select a decision.'
    if (justificationLen < 20)
      errors['justification'] = `Justification must be at least 20 characters (${justificationLen}/20).`
    setFieldErrors(errors)
    return Object.keys(errors).length === 0
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    if (!validate()) return
    await onSubmit({
      decisionValue: decisionValue as DecisionValue,
      justificationText: justification,
      overrideReason: isOverride && overrideReason.trim() ? overrideReason.trim() : undefined,
    })
  }

  return (
    <section aria-labelledby="decision-form-heading" className="rounded-md border border-gray-200 bg-white p-5">
      <h2 id="decision-form-heading" className="mb-4 text-sm font-semibold uppercase tracking-wide text-gray-500">
        Record Decision
      </h2>

      <form onSubmit={handleSubmit} noValidate aria-label="Underwriter decision form">
        {/* Decision value */}
        <div className="mb-4">
          <label htmlFor="decisionValue" className="mb-1 block text-sm font-medium text-gray-700">
            Decision
          </label>
          <select
            id="decisionValue"
            required
            aria-required="true"
            aria-invalid={!!fieldErrors['decisionValue']}
            aria-describedby={fieldErrors['decisionValue'] ? 'decisionValue-err' : undefined}
            value={decisionValue}
            onChange={(e) => setDecisionValue(e.target.value as DecisionValue)}
            disabled={submitting}
            className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm shadow-sm focus:border-purple-500 focus:outline-none focus:ring-1 focus:ring-purple-500"
          >
            <option value="">Select…</option>
            {DECISION_OPTIONS.map((opt) => (
              <option key={opt.value} value={opt.value}>{opt.label}</option>
            ))}
          </select>
          {fieldErrors['decisionValue'] && (
            <p id="decisionValue-err" role="alert" className="mt-1 text-xs text-red-600">
              {fieldErrors['decisionValue']}
            </p>
          )}
        </div>

        {/* Override reason — only shown when decision differs from recommendation */}
        {isOverride && (
          <div className="mb-4 rounded-md border border-orange-200 bg-orange-50 p-3">
            <p className="mb-2 text-xs font-medium text-orange-800">
              This decision differs from the system recommendation ({systemRecommendation}).
              Please provide an override reason.
            </p>
            <label htmlFor="overrideReason" className="mb-1 block text-sm font-medium text-gray-700">
              Override Reason <span className="text-gray-400">(optional)</span>
            </label>
            <textarea
              id="overrideReason"
              rows={2}
              maxLength={2000}
              aria-label="Override reason"
              value={overrideReason}
              onChange={(e) => setOverrideReason(e.target.value)}
              disabled={submitting}
              className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm shadow-sm focus:border-purple-500 focus:outline-none focus:ring-1 focus:ring-purple-500"
            />
          </div>
        )}

        {/* Justification */}
        <div className="mb-4">
          <label htmlFor="justification" className="mb-1 block text-sm font-medium text-gray-700">
            Justification
            <span className="ml-1 text-xs text-gray-400">
              ({justificationLen}/20 min)
            </span>
          </label>
          <textarea
            id="justification"
            rows={4}
            maxLength={5000}
            required
            aria-required="true"
            aria-invalid={!!fieldErrors['justification']}
            aria-describedby={fieldErrors['justification'] ? 'justification-err' : undefined}
            value={justification}
            onChange={(e) => setJustification(e.target.value)}
            disabled={submitting}
            className={[
              'w-full rounded-md border px-3 py-2 text-sm shadow-sm focus:outline-none focus:ring-1',
              fieldErrors['justification']
                ? 'border-red-400 focus:border-red-500 focus:ring-red-500'
                : 'border-gray-300 focus:border-purple-500 focus:ring-purple-500',
            ].join(' ')}
          />
          {fieldErrors['justification'] && (
            <p id="justification-err" role="alert" className="mt-1 text-xs text-red-600">
              {fieldErrors['justification']}
            </p>
          )}
        </div>

        <button
          type="submit"
          disabled={!canSubmit}
          aria-label="Submit underwriter decision"
          aria-disabled={!canSubmit}
          className="w-full rounded-md bg-purple-600 py-2 text-sm font-semibold text-white hover:bg-purple-700 focus:outline-none focus:ring-2 focus:ring-purple-500 focus:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {submitting ? 'Submitting…' : 'Record Decision'}
        </button>
      </form>
    </section>
  )
}
