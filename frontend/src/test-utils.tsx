import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import { createMemoryRouter, type RouteObject } from 'react-router'
import { RouterProvider } from 'react-router/dom'
import { vi } from 'vitest'
import { SessionProvider } from './app/session'

type Handler = (url: string, init?: RequestInit) => { status?: number; body?: unknown }

/** Routes fetch calls to handlers keyed by "METHOD /path" (path relative to /api/v1, without query). */
export function mockApi(handlers: Record<string, Handler | { status?: number; body?: unknown }>) {
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input)
    const path = url.replace(/^.*\/api\/v1/, '').split('?')[0]
    const key = `${init?.method ?? 'GET'} ${path}`
    const handler = handlers[key]
    const result = typeof handler === 'function' ? handler(url, init) : (handler ?? { status: 404, body: { code: 'NOT_FOUND', detail: 'Not mocked: ' + key } })
    const status = result.status ?? 200
    return new Response(status === 204 ? null : JSON.stringify(result.body ?? {}), { status })
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

export function renderRoutes(routes: RouteObject[], path: string) {
  const router = createMemoryRouter(routes, { initialEntries: [path] })
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <SessionProvider>
        <RouterProvider router={router} />
      </SessionProvider>
    </QueryClientProvider>,
  )
}
