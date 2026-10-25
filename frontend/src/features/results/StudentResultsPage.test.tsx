import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { routes } from '../../app/routes'
import { mockApi, renderRoutes } from '../../test-utils'

const student = { id: 's1', username: 'student', displayName: 'Student', email: 's@example.test', role: 'STUDENT', emailVerified: true, teacherStatus: null, capabilities: { canAdministerWorkspace: false, canPublishCourses: false, canLearn: true } }

test('pending results never show a score; published ones show feedback without keys', async () => {
  mockApi({
    'GET /auth/me': { body: student },
    'GET /workspace': { body: { name: 'School', timezone: 'UTC', logoAssetId: null, certificateVerification: false, verificationShowsStudent: false, revision: 0 } },
    'GET /notifications': { body: { items: [], page: 0, size: 20, total: 0 } },
    'GET /me/results': {
      body: [
        { attemptId: 'a1', examTitle: 'Essay test', required: true, attemptNumber: 1, finalizedAt: '2026-10-20T10:00:00Z', state: 'PENDING', earned: null, maximum: null, passed: null, publishedAt: null },
        { attemptId: 'a2', examTitle: 'Quiz', required: false, attemptNumber: 1, finalizedAt: '2026-10-20T10:00:00Z', state: 'PUBLISHED', earned: 3, maximum: 4, passed: true, publishedAt: '2026-10-20T11:00:00Z' },
      ],
    },
    'GET /me/results/a2': {
      body: { answersReleased: false, questions: [{ position: 0, prompt: 'Why?', maxPoints: 4, points: 3, feedback: 'Nearly', yourChoices: [], yourText: 'Because', correctAnswers: null, explanation: null }] },
    },
  })
  renderRoutes(routes, '/student/results')
  expect(await screen.findByText('Waiting for your teacher to publish the result.')).toBeInTheDocument()
  expect(screen.queryByText(/0 \//)).not.toBeInTheDocument()
  await userEvent.click(screen.getByRole('button', { name: 'Show details for Quiz' }))
  expect(await screen.findByText('Feedback: Nearly')).toBeInTheDocument()
  expect(screen.queryByText(/Correct answer/)).not.toBeInTheDocument()
})
