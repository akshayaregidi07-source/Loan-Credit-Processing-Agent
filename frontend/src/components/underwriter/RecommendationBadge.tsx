import type { RecommendationDetail } from '../../services/applicationService'

interface RecommendationBadgeProps {
  readonly recommendation: RecommendationDetail
}

const BADGE: Record<string, { bg: string; text: string; label: string }> = {
  APPROVE:  { bg: 'bg-green-100',  text: 'text-green-800',  label: 'Approve'  },
  REFER:    { bg: 'bg-yellow-100', text: 'text-yellow-800', label: 'Refer'    },
  DECLINE:  { bg: 'bg-red-100',   text: 'text-red-800',    label: 'Decline'  },
}

/**
 * Displays the system recommendation as an accessible colour-coded badge with
 * the policy threshold citation — Task 18.2 (Req 11.7).
 */
export default function RecommendationBadge({ recommendation: rec }: RecommendationBadgeProps) {
  const style = BADGE[rec.recommendationValue] ?? {
    bg: 'bg-gray-100', text: 'text-gray-800', label: rec.recommendationValue,
  }

  return (
    <section aria-labelledby="rec-heading" className="rounded-md border border-gray-200 bg-white p-5">
      <h2 id="rec-heading" className="mb-3 text-sm font-semibold uppercase tracking-wide text-gray-500">
        System Recommendation
      </h2>

      <span
        className={`inline-block rounded-full px-4 py-1.5 text-base font-bold ${style.bg} ${style.text}`}
        role="status"
        aria-label={`Recommendation: ${style.label}`}
      >
        {style.label}
      </span>

      <p className="mt-3 text-xs text-gray-400">
        Policy&nbsp;
        <span className="font-mono">{rec.policyThresholdId.slice(0, 8)}…</span>
        &nbsp;·&nbsp;produced {new Date(rec.producedAt).toLocaleString()}
      </p>

      {rec.explanation && (
        <details className="mt-3">
          <summary className="cursor-pointer text-xs text-purple-600 hover:underline focus:outline-none focus:underline">
            View explanation
          </summary>
          <pre className="mt-2 whitespace-pre-wrap rounded bg-gray-50 p-3 text-xs text-gray-700">
            {rec.explanation}
          </pre>
        </details>
      )}
    </section>
  )
}
