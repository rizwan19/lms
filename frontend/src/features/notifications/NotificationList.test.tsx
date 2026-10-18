import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter } from 'react-router'
import { RouterProvider } from 'react-router/dom'
import { mockApi } from '../../test-utils'
import { NotificationList, type Notification } from './NotificationList'

function renderList() {
  const router = createMemoryRouter([{ path: '/', element: <NotificationList /> }])
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
}

const unread: Notification = { id: 'n1', type: 'enrollment', title: 'Enrolled in Algebra', body: 'Welcome', link: null, createdAt: '2026-10-18T10:00:00Z', readAt: null }

test('shows unread notifications and marks them read', async () => {
  let items = [unread]
  mockApi({
    'GET /notifications': () => ({ body: { items, page: 0, size: 20, total: items.length } }),
    'PUT /notifications/n1/read': () => {
      items = [{ ...unread, readAt: '2026-10-18T11:00:00Z' }]
      return { status: 204 }
    },
  })
  renderList()
  expect(await screen.findByLabelText('Notifications, 1 unread')).toBeInTheDocument()
  await userEvent.click(screen.getByText('Notifications'))
  await userEvent.click(screen.getByRole('button', { name: 'Mark "Enrolled in Algebra" read' }))
  expect(await screen.findByLabelText('Notifications, 0 unread')).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: /Mark/ })).not.toBeInTheDocument()
})

test('shows the empty state', async () => {
  mockApi({ 'GET /notifications': { body: { items: [], page: 0, size: 20, total: 0 } } })
  renderList()
  expect(await screen.findByText('No notifications.')).toBeInTheDocument()
})

test('shows an error when notifications cannot load', async () => {
  mockApi({ 'GET /notifications': { status: 500, body: { code: 'INTERNAL_ERROR', detail: 'Unexpected server error' } } })
  renderList()
  expect(await screen.findByRole('alert')).toHaveTextContent('Unexpected server error')
})
