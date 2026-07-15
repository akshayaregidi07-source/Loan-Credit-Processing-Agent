import type { FairnessResultResponse } from '../../services/applicationService'

interface FairnessResultPanelProps {
  readonly fairnessResult: FairnessResultResponse
}

/**
 * Displays the Fairness Agent evaluation result — Task 18.2.
 *
 * When FAIRNESS_FLAG is present, renders a highlighted alert with
 * role="alert" so screen readers announce it immediately (Req 11.7).
 */
export default function FairnessResultPanel({ fairnessResult: fr }: FairnessResultPanelProps) {
  const isFlagged = fr.fairnessOutcome === 'FAIRNESS_FLAG'

  return (
    <section aria-labelledby="fairness-heading" className="rounded-md border border-gray-200 bg-white p-5">
      <h2 id="fairness-heading" className="mb-3 text-sm font-semibold uppercase tracking-wide text-gray-500">
        Fairness Evaluation
      </h2>

      {isFlagged ? (
        <div
          role="alert"
          aria-live="assertive"
          className="rounded-md border border-orange-300 bg-orange-50 px-4 py-3"
        >
          <p className="font-semibold text-orange-800">⚠ Potential Bias Detected</p>
          {fr.flagReason && (
            <p className="mt-1 text-sm text-orange-700">{fr.flagReason}</p>
          )}
        </div>
      ) : (
        <div className="rounded-md border border-green-200 bg-green-50 px-4 py-3">
          <p className="font-semibold text-green-800">✓ Fairness Check Passed</p>
        </div>
      )}

      <dl className="mt-4 grid grid-cols-3 gap-3 text-sm">
        <div>
          <dt className="text-xs text-gray-500">Original score</dt>
          <dd className="font-medium text-gray-800">{fr.originalCreditScore}</dd>
        </div>
        <div>
          <dt className="text-xs text-gray-500">Anonymised score</dt>
          <dd className="font-medium text-gray-800">{fr.anonymisedCreditScore}</dd>
        </div>
        <div>
          <dt className="text-xs text-gray-500">Delta</dt>
          <dd className={`font-medium ${fr.fairnessDelta >= 50 ? 'text-orange-700' : 'text-gray-800'}`}>
            {fr.fairnessDelta}
          </dd>
        </div>
      </dl>

      <p className="mt-3 text-xs text-gray-400">
        Evaluated {new Date(fr.evaluatedAt).toLocaleString()}
      </p>
    </section>
  )
}
