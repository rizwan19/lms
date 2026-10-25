import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { routes } from '../../app/routes'
import { mockApi, renderRoutes } from '../../test-utils'

const teacher = { id: 't1', username: 'teacher', displayName: 'Teacher', email: 't@example.test', role: 'TEACHER', emailVerified: true, teacherStatus: 'APPROVED', capabilities: { canAdministerWorkspace: true, canPublishCourses: true, canLearn: true } }
const common = {
  'GET /auth/me': { body: teacher },
  'GET /workspace': { body: { name: 'School', timezone: 'UTC', logoAssetId: null, revision: 0 } },
  'GET /notifications': { body: { items: [], page: 0, size: 20, total: 0 } },
}

test('teachers land on an empty course list with guidance', async () => {
  mockApi({ ...common, 'GET /courses': { body: { items: [], page: 0, size: 25, total: 0 } } })
  renderRoutes(routes, '/')
  expect(await screen.findByRole('heading', { name: 'Courses' })).toBeInTheDocument()
  expect(await screen.findByText(/No courses yet/)).toBeInTheDocument()
})

test('lists courses with their latest edition and archives on request', async () => {
  let archived = false
  mockApi({
    ...common,
    'GET /courses': (url) => ({
      body: {
        items: url.includes('ARCHIVED') === archived ? [{ id: 'c1', title: 'বীজগণিত', status: archived ? 'ARCHIVED' : 'ACTIVE', latestEdition: 2, draftRevision: null, updatedAt: '2026-10-18T10:00:00Z' }] : [],
        page: 0,
        size: 25,
        total: 1,
      },
    }),
    'POST /courses/c1/archive': () => {
      archived = true
      return { status: 204 }
    },
  })
  renderRoutes(routes, '/teacher/courses')
  expect(await screen.findByRole('link', { name: 'বীজগণিত' })).toBeInTheDocument()
  expect(screen.getByRole('cell', { name: '2' })).toBeInTheDocument()
  await userEvent.click(screen.getByRole('button', { name: 'Archive' }))
  expect(await screen.findByText('No courses yet. Create your first course above.')).toBeInTheDocument()
})
