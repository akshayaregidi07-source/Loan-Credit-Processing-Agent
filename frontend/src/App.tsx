import { useEffect } from 'react'
import { BrowserRouter, Navigate, Outlet, Route, Routes, useNavigate } from 'react-router-dom'
import { AuthProvider, useAuth } from './contexts/AuthContext'
import { ApiProvider } from './contexts/ApiContext'
import ProtectedRoute from './components/common/ProtectedRoute'
import Navbar from './components/common/Navbar'

// Task 17 pages — applicant flow
import LoginPage from './pages/LoginPage'
import DashboardPage from './pages/DashboardPage'
import SubmitApplicationPage from './pages/SubmitApplicationPage'
import ApplicationStatusPage from './pages/ApplicationStatusPage'

// Task 18 pages — underwriter flow
import WorklistPage from './pages/WorklistPage'
import ReviewPage from './pages/ReviewPage'

// ---------------------------------------------------------------------------
// NavigateBridge — wires react-router navigate into AuthContext (Req 11.2)
// ---------------------------------------------------------------------------

function NavigateBridge() {
  const navigate = useNavigate()
  const { setNavigate } = useAuth()
  useEffect(() => {
    setNavigate(navigate)
  }, [navigate, setNavigate])
  return null
}

// ---------------------------------------------------------------------------
// AuthenticatedLayout — shared shell with Navbar for protected pages
// ---------------------------------------------------------------------------

function AuthenticatedLayout() {
  return (
    <>
      <Navbar />
      <main id="main-content">
        <Outlet />
      </main>
    </>
  )
}

// ---------------------------------------------------------------------------
// Router tree
// ---------------------------------------------------------------------------

function AppRoutes() {
  return (
    <>
      <NavigateBridge />
      <Routes>
        {/* Public */}
        <Route path="/login" element={<LoginPage />} />

        {/* Authenticated shell */}
        <Route element={<AuthenticatedLayout />}>
          {/* Applicant routes (task 17) */}
          <Route element={<ProtectedRoute allowedRoles={['ROLE_APPLICANT']} />}>
            <Route path="/applicant/dashboard" element={<DashboardPage />} />
            <Route path="/applicant/apply" element={<SubmitApplicationPage />} />
            <Route path="/applicant/status/:id" element={<ApplicationStatusPage />} />
          </Route>

          {/* Underwriter / Admin routes (task 18 stubs) */}
          <Route element={<ProtectedRoute allowedRoles={['ROLE_UNDERWRITER', 'ROLE_ADMIN']} />}>
            <Route path="/underwriter/worklist" element={<WorklistPage />} />
            <Route path="/underwriter/review/:id" element={<ReviewPage />} />
          </Route>
        </Route>

        {/* Default redirect */}
        <Route path="/" element={<Navigate to="/login" replace />} />
        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    </>
  )
}

// ---------------------------------------------------------------------------
// Root — provider order: BrowserRouter → AuthProvider → ApiProvider
// ---------------------------------------------------------------------------

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <ApiProvider>
          <AppRoutes />
        </ApiProvider>
      </AuthProvider>
    </BrowserRouter>
  )
}
