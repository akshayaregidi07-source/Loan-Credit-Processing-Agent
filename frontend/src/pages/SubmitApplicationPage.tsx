import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useApi } from '../contexts/ApiContext'
import { submitApplication } from '../services/applicationService'
import type { ApplicationSubmitRequest } from '../services/applicationService'
import ApplicationForm from '../components/applicant/ApplicationForm'
import ErrorMessage from '../components/common/ErrorMessage'
import type { AxiosError } from 'axios'

/**
 * Submit-application page — Task 17.2.
 *
 * Renders ApplicationForm, calls the API on submit, and on success
 * redirects the applicant to their new application's status page.
 *
 * Requirements: 11.3, 11.9
 */
export default function SubmitApplicationPage() {
  const api = useApi()
  const navigate = useNavigate()
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<number | string | null>(null)

  async function handleSubmit(data: ApplicationSubmitRequest) {
    setError(null)
    setSubmitting(true)
    try {
      const applicationId = await submitApplication(api, data)
      navigate(`/applicant/status/${applicationId}`, { replace: true })
    } catch (err) {
      const axiosErr = err as AxiosError
      setError(axiosErr.response?.status ?? 'Submission failed. Please try again.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="mx-auto max-w-lg px-4 py-8">
      <h1 className="mb-6 text-2xl font-bold text-purple-700">New Loan Application</h1>
      {error && (
        <div className="mb-4">
          <ErrorMessage error={error} />
        </div>
      )}
      <ApplicationForm onSubmit={handleSubmit} submitting={submitting} />
    </div>
  )
}
