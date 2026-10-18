import { expect, type Page } from '@playwright/test'

/** Calls the API with the page's session cookies and CSRF token, for fast test setup. */
export async function api<T>(page: Page, method: 'GET' | 'POST' | 'PUT', path: string, data?: unknown): Promise<T> {
  await page.request.get('/api/v1/auth/csrf')
  const token = (await page.context().cookies()).find((cookie) => cookie.name === 'XSRF-TOKEN')?.value ?? ''
  const response = await page.request.fetch(`/api/v1${path}`, { method, data, headers: { 'X-XSRF-TOKEN': decodeURIComponent(token) } })
  expect(response.ok(), `${method} ${path}: ${await response.text()}`).toBeTruthy()
  return (response.status() === 204 ? undefined : await response.json()) as T
}

type Lesson = { title: string; bodyHtml: string; required?: boolean; resourceAssetIds?: string[]; videoAssetId?: string }

/** Creates and publishes a one-section course as the signed-in teacher; returns the edition. */
export async function publishCourse(page: Page, title: string, lessons: Lesson[]) {
  const course = await api<{ id: string }>(page, 'POST', '/courses', { title })
  const draft = await api<{ revision: number }>(page, 'GET', `/courses/${course.id}/draft`)
  const saved = await api<{ revision: number }>(page, 'PUT', `/courses/${course.id}/draft`, {
    expectedRevision: draft.revision,
    title,
    description: '',
    sections: [{ title: 'Unit 1', lessons: lessons.map((lesson) => ({ required: true, resourceAssetIds: [], ...lesson })) }],
  })
  return api<{ versionId: string; courseId: string; sections: { lessons: { id: string }[] }[] }>(page, 'POST', `/courses/${course.id}/publish`, {
    expectedRevision: saved.revision,
  })
}
