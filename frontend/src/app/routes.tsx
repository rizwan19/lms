import { Navigate, type RouteObject } from 'react-router'
import { AppShell } from './AppShell'
import { RequireAuth, homePath, useSession } from './session'
import { LoginPage } from '../features/auth/LoginPage'
import { SetupPage } from '../features/auth/SetupPage'
import { ChangePasswordPage } from '../features/auth/ChangePasswordPage'
import { ForgotPasswordPage } from '../features/auth/ForgotPasswordPage'
import { TokenPasswordPage } from '../features/auth/TokenPasswordPage'
import { VerifyEmailPage } from '../features/auth/VerifyEmailPage'
import { AccountListPage } from '../features/accounts/AccountListPage'
import { WorkspaceSettingsPage } from '../features/settings/WorkspaceSettingsPage'
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
import { ClassReportsPage } from '../features/reports/ClassReportsPage'

function Home() {
  const { me } = useSession()
  return <Navigate to={homePath(me)} replace />
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
      { path: '/verify-email', element: <VerifyEmailPage /> },
      { path: '/change-password', element: <RequireAuth><ChangePasswordPage /></RequireAuth> },
      { path: '/admin/users', element: <RequireAuth role="TEACHER"><AccountListPage /></RequireAuth> },
      { path: '/admin/settings', element: <RequireAuth administrator><WorkspaceSettingsPage /></RequireAuth> },
      { path: '/teacher/courses', element: <RequireAuth role="TEACHER"><CourseListPage /></RequireAuth> },
      { path: '/teacher/courses/:id/edit', element: <RequireAuth role="TEACHER"><CourseEditorPage /></RequireAuth> },
      { path: '/teacher/courses/:id/preview', element: <RequireAuth role="TEACHER"><CoursePreviewPage /></RequireAuth> },
      { path: '/teacher/exams/:id/edit', element: <RequireAuth role="TEACHER"><ExamEditorPage /></RequireAuth> },
      { path: '/teacher/classes', element: <RequireAuth role="TEACHER"><ClassListPage /></RequireAuth> },
      { path: '/teacher/classes/:id/reports', element: <RequireAuth role="TEACHER"><ClassReportsPage /></RequireAuth> },
      { path: '/teacher/classes/:id', element: <RequireAuth role="TEACHER"><ClassDetailPage /></RequireAuth> },
      { path: '/student', element: <RequireAuth role="STUDENT"><StudentDashboardPage /></RequireAuth> },
      { path: '/student/enrollments/:id', element: <RequireAuth role="STUDENT"><CoursePlayerPage /></RequireAuth> },
      { path: '/student/enrollments/:enrollmentId/exams/:assignmentId', element: <RequireAuth role="STUDENT"><ExamInstructionsPage /></RequireAuth> },
      { path: '/student/attempts/:id', element: <RequireAuth role="STUDENT"><ExamAttemptPage /></RequireAuth> },
      { path: '/teacher/grading', element: <RequireAuth role="TEACHER"><GradingQueuePage /></RequireAuth> },
      { path: '/teacher/grading/:attemptId', element: <RequireAuth role="TEACHER"><GradeAttemptPage /></RequireAuth> },
      { path: '/student/results', element: <RequireAuth role="STUDENT"><StudentResultsPage /></RequireAuth> },
      { path: '/student/certificates', element: <RequireAuth role="STUDENT"><StudentCertificatesPage /></RequireAuth> },
      { path: '/verify/:token', element: <VerifyCertificatePage /> },
      { path: '*', element: <p>Page not found.</p> },
    ],
  },
]
