import type { CreditScoreBreakdown } from '../../services/applicationService'

interface CreditScoreCardProps {
  readonly creditScore: CreditScoreBreakdown
}

/**
 * Displays the overall Credit Score and the three sub-score factors with
 * their weights and contributions — Task 18.2.
 *
 * Uses a definition-list structure for screen-reader accessibility (Req 11.7).
 */
export default function CreditScoreCard({ creditScore: cs }: CreditScoreCardProps) {
  const factors = [
    {
      name: 'DTI Ratio',
      subScore: cs.dtiSubScore,
      weight: cs.dtiWeight,
      contribution: +(cs.dtiSubScore * cs.dtiWeight * 10).toFixed(2),
    },
    {
      name: 'Income Stability',
      subScore: cs.incomeStabilityScore,
      weight: cs.incomeStabilityWeight,
      contribution: +(cs.incomeStabilityScore * cs.incomeStabilityWeight * 10).toFixed(2),
    },
    {
      name: 'Credit History',
      subScore: cs.creditHistoryScore,
      weight: cs.creditHistoryWeight,
      contribution: +(cs.creditHistoryScore * cs.creditHistoryWeight * 10).toFixed(2),
    },
  ]

  const scoreColor =
    cs.creditScore >= 700
      ? 'text-green-700'
      : cs.creditScore >= 500
      ? 'text-yellow-700'
      : 'text-red-700'

  return (
    <section aria-labelledby="credit-score-heading" className="rounded-md border border-gray-200 bg-white p-5">
      <h2 id="credit-score-heading" className="mb-1 text-sm font-semibold uppercase tracking-wide text-gray-500">
        Credit Score
      </h2>
      <p className={`mb-4 text-4xl font-bold ${scoreColor}`} aria-label={`Credit score ${cs.creditScore}`}>
        {cs.creditScore}
        <span className="ml-1 text-base font-normal text-gray-400">/ 1000</span>
      </p>

      <table className="w-full text-sm" aria-label="Credit score factor breakdown">
        <thead>
          <tr className="border-b border-gray-100 text-left text-xs text-gray-500">
            <th scope="col" className="pb-1 font-medium">Factor</th>
            <th scope="col" className="pb-1 font-medium text-right">Sub-score</th>
            <th scope="col" className="pb-1 font-medium text-right">Weight</th>
            <th scope="col" className="pb-1 font-medium text-right">Contribution</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-gray-50">
          {factors.map((f) => (
            <tr key={f.name}>
              <td className="py-1.5 text-gray-700">{f.name}</td>
              <td className="py-1.5 text-right text-gray-700">{f.subScore}</td>
              <td className="py-1.5 text-right text-gray-500">{(f.weight * 100).toFixed(0)}%</td>
              <td className="py-1.5 text-right font-medium text-gray-800">{f.contribution}</td>
            </tr>
          ))}
        </tbody>
      </table>

      <p className="mt-3 text-xs text-gray-400">
        Computed {new Date(cs.computedAt).toLocaleString()} · Policy&nbsp;
        <span className="font-mono">{cs.policyThresholdId.slice(0, 8)}…</span>
      </p>
    </section>
  )
}
