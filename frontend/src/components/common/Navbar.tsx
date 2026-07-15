import { Link } from 'react-router-dom'
import { useAuth } from '../../contexts/AuthContext'

/**
 * Top navigation bar — shown on all authenticated pages (Req 11.9).
 * Keyboard-navigable; role-appropriate links; logout button.
 */
export default function Navbar() {
  const { role, logout } = useAuth()

  return (
    <nav
      role="navigation"
      aria-label="Main navigation"
      className="flex items-center justify-between bg-white px-6 py-3 shadow-sm"
    >
      <span className="text-lg font-semibold text-purple-700">TechVest AI — Loan Portal</span>

      <ul className="flex items-center gap-6 list-none m-0 p-0">
        {(role === 'ROLE_APPLICANT') && (
          <>
            <li>
              <Link
                to="/applicant/dashboard"
                className="text-sm text-gray-700 hover:text-purple-700 focus:outline-none focus:underline"
              >
                My Applications
              </Link>
            </li>
            <li>
              <Link
                to="/applicant/apply"
                className="text-sm text-gray-700 hover:text-purple-700 focus:outline-none focus:underline"
              >
                New Application
              </Link>
            </li>
          </>
        )}

        {(role === 'ROLE_UNDERWRITER' || role === 'ROLE_ADMIN') && (
          <li>
            <Link
              to="/underwriter/worklist"
              className="text-sm text-gray-700 hover:text-purple-700 focus:outline-none focus:underline"
            >
              Worklist
            </Link>
          </li>
        )}

        <li>
          <button
            type="button"
            onClick={logout}
            aria-label="Log out"
            className="rounded-md bg-purple-600 px-4 py-1.5 text-sm font-medium text-white hover:bg-purple-700 focus:outline-none focus:ring-2 focus:ring-purple-500 focus:ring-offset-2"
          >
            Log out
          </button>
        </li>
      </ul>
    </nav>
  )
}
