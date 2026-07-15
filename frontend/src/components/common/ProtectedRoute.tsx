import { Navigate, Outlet } from 'react-router-dom'
import { useAuth, type UserRole } from '../../contexts/AuthContext'

interface ProtectedRouteProps {
  /** One or more roles that may access this route. */
  readonly allowedRoles: UserRole[]
  /** Where to redirect unauthenticated users. Defaults to /login. */
  readonly redirectTo?: string
}

/**
 * Role-based route guard (Req 11.1, 11.2).
 * Unauthenticated users are sent to /login.
 * Authenticated users without the required role are sent to their own dashboard.
 */
export default function ProtectedRoute({
  allowedRoles,
  redirectTo = '/login',
}: ProtectedRouteProps) {
  const { isAuthenticated, role } = useAuth()

  if (!isAuthenticated) {
    return <Navigate to={redirectTo} replace />
  }

  if (role && !allowedRoles.includes(role)) {
    // Redirect to the correct dashboard for the user's actual role
    const fallback = roleDashboard(role)
    return <Navigate to={fallback} replace />
  }

  return <Outlet />
}

function roleDashboard(role: UserRole): string {
  switch (role) {
    case 'ROLE_UNDERWRITER':
    case 'ROLE_ADMIN':
      return '/underwriter/worklist'
    default:
      return '/applicant/dashboard'
  }
}
