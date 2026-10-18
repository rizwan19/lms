import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { routes } from '../../app/routes'
import { mockApi, renderRoutes } from '../../test-utils'

const teacher = { id: 't1', username: 'teacher', displayName: 'টিচার', email: 't@example.test', roles: ['TEACHER'], passwordChangeRequired: false }
const institution = { body: { name: 'School', timezone: 'UTC', revision: 0 } }

test('shows the empty state for a teacher without students', async () => {
  mockApi({ 'GET /auth/me': { body: teacher }, 'GET /institution': institution, 'GET /users': { body: { items: [], page: 0, size: 25, total: 0 } } })
  renderRoutes(routes, '/admin/users')
  expect(await screen.findByRole('heading', { name: 'Students' })).toBeInTheDocument()
  expect(await screen.findByText('No accounts match.')).toBeInTheDocument()
})

test('shows a created temporary password once', async () => {
  mockApi({
    'GET /auth/me': { body: teacher },
    'GET /institution': institution,
    'GET /users': { body: { items: [], page: 0, size: 25, total: 0 } },
    'POST /users': {
      status: 201,
      body: {
        account: { id: 's1', username: 'rahim', displayName: 'রহিম', email: null, roles: ['STUDENT'], status: 'ACTIVE', createdAt: '2026-10-18T00:00:00Z', revision: 0 },
        temporaryCredential: { username: 'rahim', temporaryPassword: 'Temp1234Pass5678', expiresAt: '2026-10-19T00:00:00Z' },
        invitationExpiresAt: null,
      },
    },
  })
  renderRoutes(routes, '/admin/users')
  await userEvent.click(await screen.findByRole('button', { name: 'New account' }))
  expect(screen.queryByRole('group', { name: 'Roles' })).not.toBeInTheDocument()
  await userEvent.type(screen.getByLabelText('Username'), 'rahim')
  await userEvent.type(screen.getByLabelText('Display name'), 'রহিম')
  await userEvent.click(screen.getByRole('button', { name: 'Create account' }))
  expect(await screen.findByTestId('temporary-password')).toHaveTextContent('Temp1234Pass5678')
  await userEvent.click(screen.getByRole('button', { name: 'Done' }))
  expect(screen.queryByTestId('temporary-password')).not.toBeInTheDocument()
})
