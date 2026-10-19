import { Navigate, type RouteObject } from 'react-router'
import type { Role } from '../shared/types'
import { AppShell } from './AppShell'
import { RequireAuth, homePath, useSession } from './session'
import { LoginPage } from '../features/auth/LoginPage'
import { SetupPage } from '../features/auth/SetupPage'
import { ChangePasswordPage } from '../features/auth/ChangePasswordPage'
import { ForgotPasswordPage } from '../features/auth/ForgotPasswordPage'
import { TokenPasswordPage } from '../features/auth/TokenPasswordPage'
import { AccountListPage } from '../features/accounts/AccountListPage'
import { InstitutionSettingsPage } from '../features/settings/InstitutionSettingsPage'
import { CourseListPage } from '../features/courses/CourseListPage'
import { CourseEditorPage } from '../features/courses/CourseEditorPage'
import { CoursePreviewPage } from '../features/courses/CoursePreviewPage'
import { ClassListPage } from '../features/classrooms/ClassListPage'
import { ClassDetailPage } from '../features/classrooms/ClassDetailPage'
import { StudentDashboardPage } from '../features/learning/StudentDashboardPage'
import { CoursePlayerPage } from '../features/learning/CoursePlayerPage'
import { ExamEditorPage } from '../features/assessments/ExamEditorPage'
import { ExamInstructionsPage } from '../features/assessments/ExamInstructionsPage'
import { ExamAttemptPage } from '../features/assessments/ExamAttemptPage'
import { GradingQueuePage } from '../features/grading/GradingQueuePage'
import { GradeAttemptPage } from '../features/grading/GradeAttemptPage'
import { StudentResultsPage } from '../features/results/StudentResultsPage'
import { StudentCertificatesPage } from '../features/certificates/StudentCertificatesPage'
import { VerifyCertificatePage } from '../features/certificates/VerifyCertificatePage'

function Home() {
  const { activeRole } = useSession()
  return <Navigate to={homePath(activeRole)} replace />
}

const STAFF: Role[] = ['ADMINISTRATOR', 'TEACHER']

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
      { path: '/admin/users', element: <RequireAuth roles={STAFF}><AccountListPage /></RequireAuth> },
      { path: '/admin/settings', element: <RequireAuth roles={['ADMINISTRATOR']}><InstitutionSettingsPage /></RequireAuth> },
      { path: '/teacher/courses', element: <RequireAuth roles={STAFF}><CourseListPage /></RequireAuth> },
      { path: '/teacher/courses/:id/edit', element: <RequireAuth roles={STAFF}><CourseEditorPage /></RequireAuth> },
      { path: '/teacher/courses/:id/preview', element: <RequireAuth roles={STAFF}><CoursePreviewPage /></RequireAuth> },
      { path: '/teacher/exams/:id/edit', element: <RequireAuth roles={STAFF}><ExamEditorPage /></RequireAuth> },
      { path: '/teacher/classes', element: <RequireAuth roles={STAFF}><ClassListPage /></RequireAuth> },
      { path: '/teacher/classes/:id', element: <RequireAuth roles={STAFF}><ClassDetailPage /></RequireAuth> },
      { path: '/student', element: <RequireAuth roles={['STUDENT']}><StudentDashboardPage /></RequireAuth> },
      { path: '/student/enrollments/:id', element: <RequireAuth roles={['STUDENT']}><CoursePlayerPage /></RequireAuth> },
      { path: '/student/enrollments/:enrollmentId/exams/:assignmentId', element: <RequireAuth roles={['STUDENT']}><ExamInstructionsPage /></RequireAuth> },
      { path: '/student/attempts/:id', element: <RequireAuth roles={['STUDENT']}><ExamAttemptPage /></RequireAuth> },
      { path: '/teacher/grading', element: <RequireAuth roles={STAFF}><GradingQueuePage /></RequireAuth> },
      { path: '/teacher/grading/:attemptId', element: <RequireAuth roles={STAFF}><GradeAttemptPage /></RequireAuth> },
      { path: '/student/results', element: <RequireAuth roles={['STUDENT']}><StudentResultsPage /></RequireAuth> },
      { path: '/student/certificates', element: <RequireAuth roles={['STUDENT']}><StudentCertificatesPage /></RequireAuth> },
      { path: '/verify/:token', element: <VerifyCertificatePage /> },
      { path: '*', element: <p>Page not found.</p> },
    ],
  },
]
