import { Navigate, type RouteObject } from 'react-router'
import { AppShell } from './AppShell'
import { RequireAuth, homePath, useSession } from './session'
import { LoginPage } from '../features/auth/LoginPage'
import { SetupPage } from '../features/auth/SetupPage'
import { ChangePasswordPage } from '../features/auth/ChangePasswordPage'
import { ForgotPasswordPage } from '../features/auth/ForgotPasswordPage'
import { TokenPasswordPage } from '../features/auth/TokenPasswordPage'
import { AccountListPage } from '../features/accounts/AccountListPage'
import { InstitutionSettingsPage } from '../features/settings/InstitutionSettingsPage'

function Home() {
  const { activeRole } = useSession()
  return <Navigate to={homePath(activeRole)} replace />
}

export const routes: RouteObject[] = [
  {
    element: <AppShell />,
    children: [
      { path: '/', element: <RequireAuth><Home /></RequireAuth> },
      { path: '/login', element: <LoginPage /> },
      { path: '/setup', element: <SetupPage /> },
      { path: '/forgot-password', element: <ForgotPasswordPage /> },
      { path: '/reset-password', element: <TokenPasswordPage mode="reset" /> },
      { path: '/accept-invitation', element: <TokenPasswordPage mode="invitation" /> },
      { path: '/change-password', element: <RequireAuth><ChangePasswordPage /></RequireAuth> },
      { path: '/admin/users', element: <RequireAuth roles={['ADMINISTRATOR', 'TEACHER']}><AccountListPage /></RequireAuth> },
      { path: '/admin/settings', element: <RequireAuth roles={['ADMINISTRATOR']}><InstitutionSettingsPage /></RequireAuth> },
      { path: '*', element: <p>Page not found.</p> },
    ],
  },
]
