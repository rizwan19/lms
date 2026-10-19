import { expect, test } from '@playwright/test'
import { readFileSync } from 'node:fs'
import { api, publishCourse } from './api'
import { activateAccount, createAccount, signInAsAdmin, signOut, unique } from './support'

const VIDEO = readFileSync(new URL('../../backend/src/test/resources/media/h264-aac.mp4', import.meta.url))

/** Fresh installation: setup, sign-in, private upload through the proxy and student playback with HTTP ranges. */
test('a clean installation supports setup, upload and student video playback', async ({ page }) => {
  await signInAsAdmin(page)
  const teacher = unique('installer')
  const teacherTemp = await createAccount(page, { username: teacher, displayName: 'Install Teacher', email: `${teacher}@example.test`, roles: ['TEACHER'] })
  const student = unique('viewer')
  const studentTemp = await createAccount(page, { username: student, displayName: 'Viewer' })
  await signOut(page)
  await activateAccount(page, teacher, teacherTemp)

  const token = decodeURIComponent((await page.context().cookies()).find((cookie) => cookie.name === 'XSRF-TOKEN')?.value ?? '')
  const upload = await page.request.post('/api/v1/media?purpose=VIDEO&filename=intro.mp4', { data: VIDEO, headers: { 'Content-Type': 'video/mp4', 'X-XSRF-TOKEN': token } })
  expect(upload.status()).toBe(201)
  const video = ((await upload.json()) as { id: string }).id
  const course = await publishCourse(page, unique('Install check'), [{ title: 'Welcome video', bodyHtml: '<p>Hello</p>', videoAssetId: video }])
  const created = await api<{ id: string }>(page, 'POST', '/classes', { courseVersionId: course.versionId, name: 'Install class' })
  await api(page, 'POST', `/classes/${created.id}/activate`)
  const enrollment = await api<{ enrollmentId: string }>(page, 'POST', `/classes/${created.id}/enrollments`, { username: student })
  await signOut(page)

  await activateAccount(page, student, studentTemp)
  await page.goto(`/student/enrollments/${enrollment.enrollmentId}`)
  const player = page.getByLabel('Video: Welcome video')
  await expect(player).toBeVisible()
  await expect.poll(() => player.evaluate((element: HTMLVideoElement) => element.readyState), { timeout: 30_000 }).toBeGreaterThanOrEqual(1)
  expect(await player.evaluate((element: HTMLVideoElement) => element.duration)).toBeGreaterThan(1)

  // Local storage answers directly; S3 storage answers with a short-lived signed redirect. Either way the app's
  // own response is never cacheable, and ranges work end to end.
  const direct = await page.request.get(`/api/v1/media/${video}/content`, { headers: { Range: 'bytes=0-15' }, maxRedirects: 0 })
  expect([206, 302]).toContain(direct.status())
  expect(direct.headers()['cache-control']).toContain('no-store')
  if (direct.status() === 302) expect(direct.headers()['location']).toContain('X-Amz-Expires=60')
  const ranged = await page.request.get(`/api/v1/media/${video}/content`, { headers: { Range: 'bytes=0-15' } })
  expect(ranged.status()).toBe(206)
})
