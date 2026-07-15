import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
  type ReactNode,
} from 'react'

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

/** Roles mirror Spring Security's ROLE_ prefix convention. */
export type UserRole = 'ROLE_APPLICANT' | 'ROLE_UNDERWRITER' | 'ROLE_ADMIN'

interface AuthState {
  token: string | null
  role: UserRole | null
  isAuthenticated: boolean
}

interface AuthContextValue extends AuthState {
  login: (token: string, role: string) => void
  logout: () => void
  /** Called by App.tsx after the Router is mounted so the context can navigate. */
  setNavigate: (fn: (path: string) => void) => void
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function getExpMs(token: string): number {
  try {
    const payload = token.split('.')[1]
    const decoded = JSON.parse(atob(payload.replace(/-/g, '+').replace(/_/g, '/'))) as {
      exp?: number
    }
    return decoded.exp != null ? decoded.exp * 1000 : 0
  } catch {
    return 0
  }
}

// ---------------------------------------------------------------------------
// Context
// ---------------------------------------------------------------------------

const AuthContext = createContext<AuthContextValue | null>(null)

/**
 * Provides JWT state stored in React memory only — never localStorage/sessionStorage
 * (Requirement 11.1).
 *
 * Must be rendered *inside* BrowserRouter so that setNavigate can hand off
 * the react-router navigate function (Requirement 11.2).
 */
export function AuthProvider({ children }: { readonly children: ReactNode }) {
  const [state, setState] = useState<AuthState>({
    token: null,
    role: null,
    isAuthenticated: false,
  })

  const navigateFnRef = useRef<((path: string) => void) | null>(null)
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  const setNavigate = useCallback((fn: (path: string) => void) => {
    navigateFnRef.current = fn
  }, [])

  const doLogout = useCallback(() => {
    if (timerRef.current) clearTimeout(timerRef.current)
    setState({ token: null, role: null, isAuthenticated: false })
    navigateFnRef.current?.('/login')
  }, [])

  const scheduleExpiry = useCallback(
    (token: string) => {
      if (timerRef.current) clearTimeout(timerRef.current)
      const ms = getExpMs(token) - Date.now()
      if (ms <= 0) {
        doLogout()
        return
      }
      timerRef.current = setTimeout(doLogout, ms)
    },
    [doLogout],
  )

  const login = useCallback(
    (token: string, role: string) => {
      setState({ token, role: role as UserRole, isAuthenticated: true })
      scheduleExpiry(token)
    },
    [scheduleExpiry],
  )

  useEffect(() => {
    return () => {
      if (timerRef.current) clearTimeout(timerRef.current)
    }
  }, [])

  return (
    <AuthContext.Provider value={{ ...state, login, logout: doLogout, setNavigate }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
