import { type FormEvent, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../contexts/AuthContext'
import { useApi } from '../contexts/ApiContext'
import { login } from '../services/authService'
import type { AxiosError } from 'axios'
import ErrorMessage from '../components/common/ErrorMessage'

/**
 * Login page — Task 17.1.
 *
 * Submits credentials, stores the JWT in AuthContext (in-memory only),
 * and redirects to the role-appropriate dashboard.
 * Fully keyboard-navigable with ARIA labels and sufficient colour contrast.
 *
 * Requirements: 11.1, 11.9, 11.10
 */
export default function LoginPage() {
  const { login: storeToken } = useAuth()
  const api = useApi()
  const navigate = useNavigate()

  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<number | string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      const response = await login(api, { username, password })
      storeToken(response.token, response.role)
      // Redirect based on role
      if (response.role === 'ROLE_APPLICANT') {
        navigate('/applicant/dashboard', { replace: true })
      } else {
        navigate('/underwriter/worklist', { replace: true })
      }
    } catch (err) {
      const axiosErr = err as AxiosError
      setError(axiosErr.response?.status ?? 'Login failed. Please try again.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-50 px-4">
      <div className="w-full max-w-md rounded-lg bg-white p-8 shadow-md">
        <h1 className="mb-6 text-center text-2xl font-bold text-purple-700">
          TechVest AI — Loan Portal
        </h1>

        <form onSubmit={handleSubmit} noValidate aria-label="Login form">
          <div className="mb-4">
            <label
              htmlFor="username"
              className="mb-1 block text-sm font-medium text-gray-700"
            >
              Username
            </label>
            <input
              id="username"
              type="text"
              autoComplete="username"
              required
              aria-required="true"
              aria-describedby={error ? 'login-error' : undefined}
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm shadow-sm focus:border-purple-500 focus:outline-none focus:ring-1 focus:ring-purple-500"
              disabled={submitting}
            />
          </div>

          <div className="mb-6">
            <label
              htmlFor="password"
              className="mb-1 block text-sm font-medium text-gray-700"
            >
              Password
            </label>
            <input
              id="password"
              type="password"
              autoComplete="current-password"
              required
              aria-required="true"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm shadow-sm focus:border-purple-500 focus:outline-none focus:ring-1 focus:ring-purple-500"
              disabled={submitting}
            />
          </div>

          {error && (
            <div id="login-error" className="mb-4">
              <ErrorMessage error={error} />
            </div>
          )}

          <button
            type="submit"
            disabled={submitting || !username || !password}
            aria-label="Sign in"
            className="w-full rounded-md bg-purple-600 py-2 text-sm font-semibold text-white hover:bg-purple-700 focus:outline-none focus:ring-2 focus:ring-purple-500 focus:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {submitting ? 'Signing in…' : 'Sign in'}
          </button>
        </form>
      </div>
    </div>
  )
}
