import { type FormEvent, useState } from 'react'
import type {
  ApplicationSubmitRequest,
  EmploymentStatus,
} from '../../services/applicationService'

interface ApplicationFormProps {
  readonly onSubmit: (data: ApplicationSubmitRequest) => Promise<void>
  readonly submitting: boolean
}

const EMPLOYMENT_OPTIONS: { value: EmploymentStatus; label: string }[] = [
  { value: 'EMPLOYED', label: 'Employed' },
  { value: 'SELF_EMPLOYED', label: 'Self-Employed' },
  { value: 'UNEMPLOYED', label: 'Unemployed' },
  { value: 'RETIRED', label: 'Retired' },
  { value: 'STUDENT', label: 'Student' },
]

/**
 * Controlled application submission form — Task 17.2.
 *
 * Validates required fields client-side before the parent page calls the API.
 * All inputs have aria-required, aria-invalid, and aria-describedby for
 * screen-reader compatibility (Requirement 11.9).
 */
export default function ApplicationForm({ onSubmit, submitting }: ApplicationFormProps) {
  const [requestedAmount, setRequestedAmount] = useState('')
  const [loanPurpose, setLoanPurpose] = useState('')
  const [employmentStatus, setEmploymentStatus] = useState<EmploymentStatus | ''>('')
  const [grossMonthlyIncome, setGrossMonthlyIncome] = useState('')
  const [totalMonthlyDebt, setTotalMonthlyDebt] = useState('')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

  function validate(): boolean {
    const errors: Record<string, string> = {}
    if (!requestedAmount || parseFloat(requestedAmount) < 1)
      errors['requestedAmount'] = 'Enter a requested amount of at least 1.'
    if (!loanPurpose.trim())
      errors['loanPurpose'] = 'Loan purpose is required.'
    if (!employmentStatus)
      errors['employmentStatus'] = 'Select an employment status.'
    if (!grossMonthlyIncome || parseFloat(grossMonthlyIncome) <= 0)
      errors['grossMonthlyIncome'] = 'Enter a gross monthly income greater than 0.'
    if (totalMonthlyDebt === '' || parseFloat(totalMonthlyDebt) < 0)
      errors['totalMonthlyDebt'] = 'Enter total monthly debt (0 or more).'
    setFieldErrors(errors)
    return Object.keys(errors).length === 0
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    if (!validate()) return
    await onSubmit({
      requestedAmount: parseFloat(requestedAmount),
      loanPurpose: loanPurpose.trim(),
      employmentStatus: employmentStatus as EmploymentStatus,
      grossMonthlyIncome: parseFloat(grossMonthlyIncome),
      totalMonthlyDebt: parseFloat(totalMonthlyDebt),
    })
  }

  return (
    <form onSubmit={handleSubmit} noValidate aria-label="Loan application form">
      {/* Requested Amount */}
      <Field
        id="requestedAmount"
        label="Requested Loan Amount (£)"
        error={fieldErrors['requestedAmount']}
      >
        <input
          id="requestedAmount"
          type="number"
          min="1"
          step="0.01"
          required
          aria-required="true"
          aria-invalid={!!fieldErrors['requestedAmount']}
          aria-describedby={fieldErrors['requestedAmount'] ? 'requestedAmount-err' : undefined}
          value={requestedAmount}
          onChange={(e) => setRequestedAmount(e.target.value)}
          disabled={submitting}
          className={inputClass(!!fieldErrors['requestedAmount'])}
        />
      </Field>

      {/* Loan Purpose */}
      <Field id="loanPurpose" label="Loan Purpose" error={fieldErrors['loanPurpose']}>
        <input
          id="loanPurpose"
          type="text"
          maxLength={200}
          required
          aria-required="true"
          aria-invalid={!!fieldErrors['loanPurpose']}
          aria-describedby={fieldErrors['loanPurpose'] ? 'loanPurpose-err' : undefined}
          value={loanPurpose}
          onChange={(e) => setLoanPurpose(e.target.value)}
          disabled={submitting}
          className={inputClass(!!fieldErrors['loanPurpose'])}
        />
      </Field>

      {/* Employment Status */}
      <Field
        id="employmentStatus"
        label="Employment Status"
        error={fieldErrors['employmentStatus']}
      >
        <select
          id="employmentStatus"
          required
          aria-required="true"
          aria-invalid={!!fieldErrors['employmentStatus']}
          aria-describedby={
            fieldErrors['employmentStatus'] ? 'employmentStatus-err' : undefined
          }
          value={employmentStatus}
          onChange={(e) => setEmploymentStatus(e.target.value as EmploymentStatus)}
          disabled={submitting}
          className={inputClass(!!fieldErrors['employmentStatus'])}
        >
          <option value="">Select…</option>
          {EMPLOYMENT_OPTIONS.map((opt) => (
            <option key={opt.value} value={opt.value}>
              {opt.label}
            </option>
          ))}
        </select>
      </Field>

      {/* Gross Monthly Income */}
      <Field
        id="grossMonthlyIncome"
        label="Gross Monthly Income (£)"
        error={fieldErrors['grossMonthlyIncome']}
      >
        <input
          id="grossMonthlyIncome"
          type="number"
          min="0.01"
          step="0.01"
          required
          aria-required="true"
          aria-invalid={!!fieldErrors['grossMonthlyIncome']}
          aria-describedby={
            fieldErrors['grossMonthlyIncome'] ? 'grossMonthlyIncome-err' : undefined
          }
          value={grossMonthlyIncome}
          onChange={(e) => setGrossMonthlyIncome(e.target.value)}
          disabled={submitting}
          className={inputClass(!!fieldErrors['grossMonthlyIncome'])}
        />
      </Field>

      {/* Total Monthly Debt */}
      <Field
        id="totalMonthlyDebt"
        label="Total Monthly Debt Obligations (£)"
        error={fieldErrors['totalMonthlyDebt']}
      >
        <input
          id="totalMonthlyDebt"
          type="number"
          min="0"
          step="0.01"
          required
          aria-required="true"
          aria-invalid={!!fieldErrors['totalMonthlyDebt']}
          aria-describedby={
            fieldErrors['totalMonthlyDebt'] ? 'totalMonthlyDebt-err' : undefined
          }
          value={totalMonthlyDebt}
          onChange={(e) => setTotalMonthlyDebt(e.target.value)}
          disabled={submitting}
          className={inputClass(!!fieldErrors['totalMonthlyDebt'])}
        />
      </Field>

      <button
        type="submit"
        disabled={submitting}
        aria-label="Submit application"
        className="mt-2 w-full rounded-md bg-purple-600 py-2 text-sm font-semibold text-white hover:bg-purple-700 focus:outline-none focus:ring-2 focus:ring-purple-500 focus:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
      >
        {submitting ? 'Submitting…' : 'Submit Application'}
      </button>
    </form>
  )
}

// ---------------------------------------------------------------------------
// Helper components
// ---------------------------------------------------------------------------

function inputClass(invalid: boolean) {
  return [
    'w-full rounded-md border px-3 py-2 text-sm shadow-sm',
    'focus:outline-none focus:ring-1 focus:ring-purple-500',
    invalid ? 'border-red-400 focus:border-red-500' : 'border-gray-300 focus:border-purple-500',
  ].join(' ')
}

interface FieldProps {
  readonly id: string
  readonly label: string
  readonly error?: string
  readonly children: React.ReactNode
}

function Field({ id, label, error, children }: FieldProps) {
  return (
    <div className="mb-4">
      <label htmlFor={id} className="mb-1 block text-sm font-medium text-gray-700">
        {label}
      </label>
      {children}
      {error && (
        <p id={`${id}-err`} role="alert" className="mt-1 text-xs text-red-600">
          {error}
        </p>
      )}
    </div>
  )
}
