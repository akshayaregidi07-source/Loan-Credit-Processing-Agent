import axios, {
  type AxiosInstance,
  type InternalAxiosRequestConfig,
  type AxiosResponse,
  type AxiosError,
} from 'axios'
import {
  createContext,
  useContext,
  useEffect,
  useRef,
  type ReactNode,
} from 'react'
import { useAuth } from './AuthContext'

// ---------------------------------------------------------------------------
// Context value type
// ---------------------------------------------------------------------------

interface ApiContextValue {
  api: AxiosInstance
}

const ApiContext = createContext<ApiContextValue | null>(null)

// ---------------------------------------------------------------------------
// Provider
// ---------------------------------------------------------------------------

/** Creates one shared Axios instance per session and wires JWT + 401 handling. */
export function ApiProvider({ children }: { readonly children: ReactNode }) {
  const { token, logout } = useAuth()

  // Keep a stable instance across re-renders; interceptors are updated via refs.
  const apiRef = useRef<AxiosInstance>(
    axios.create({
      baseURL: '/api/v1',
      headers: { 'Content-Type': 'application/json' },
    }),
  )

  // Interceptor ids so we can eject and re-register when token changes
  const reqInterceptorId = useRef<number | null>(null)
  const resInterceptorId = useRef<number | null>(null)

  useEffect(() => {
    const api = apiRef.current

    // Eject previous interceptors before adding new ones
    if (reqInterceptorId.current !== null)
      api.interceptors.request.eject(reqInterceptorId.current)
    if (resInterceptorId.current !== null)
      api.interceptors.response.eject(resInterceptorId.current)

    // Request interceptor — inject Authorization header (Req 11.1)
    reqInterceptorId.current = api.interceptors.request.use(
      (config: InternalAxiosRequestConfig) => {
        if (token) {
          config.headers = config.headers ?? {}
          config.headers['Authorization'] = `Bearer ${token}`
        }
        return config
      },
    )

    // Response interceptor — catch 401 and trigger logout, but not on the
    // login endpoint itself (a 401 there is just wrong credentials).
    resInterceptorId.current = api.interceptors.response.use(
      (response: AxiosResponse) => response,
      (error: AxiosError) => {
        const url = error.config?.url ?? ''
        const isLoginEndpoint = url.includes('/auth/login')
        if (error.response?.status === 401 && !isLoginEndpoint) {
          logout()
        }
        return Promise.reject(error)
      },
    )

    return () => {
      api.interceptors.request.eject(reqInterceptorId.current!)
      api.interceptors.response.eject(resInterceptorId.current!)
    }
  }, [token, logout])

  return (
    <ApiContext.Provider value={{ api: apiRef.current }}>
      {children}
    </ApiContext.Provider>
  )
}

// ---------------------------------------------------------------------------
// Hook
// ---------------------------------------------------------------------------

export function useApi(): AxiosInstance {
  const ctx = useContext(ApiContext)
  if (!ctx) throw new Error('useApi must be used within ApiProvider')
  return ctx.api
}
