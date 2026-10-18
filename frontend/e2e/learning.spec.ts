import { expect, test } from '@playwright/test'
import { api, publishCourse } from './api'
import { activateAccount, createAccount, PASSWORD, signIn, signInAsAdmin, signOut, unique } from './support'

const PDF = Buffer.from('%PDF-1.4\n%%EOF\n')

test('student studies, completes lessons and keeps progress; files stay private', async ({ page, browser }) => {
  await signInAsAdmin(page)
  const teacher = unique('teacher')
  const teacherTemp = await createAccount(page, { username: teacher, displayName: 'Teacher', email: `${teacher}@example.test`, roles: ['TEACHER'] })
  const student = unique('learner')
  const studentTemp = await createAccount(page, { username: student, displayName: 'নুসরাত' })
  const outsider = unique('outsider')
  const outsiderTemp = await createAccount(page, { username: outsider, displayName: 'Outsider' })
  await signOut(page)
  await activateAccount(page, outsider, outsiderTemp)
  await signOut(page)
  await activateAccount(page, teacher, teacherTemp)

  const upload = await page.request.post(`/api/v1/media?purpose=DOCUMENT&filename=worksheet.pdf`, {
    data: PDF,
    headers: {
      'Content-Type': 'application/pdf',
      'X-XSRF-TOKEN': decodeURIComponent((await page.context().cookies()).find((cookie) => cookie.name === 'XSRF-TOKEN')?.value ?? ''),
    },
  })
  const pdf = ((await upload.json()) as { id: string }).id
  const course = unique('Botany')
  const edition = await publishCourse(page, course, [
    { title: 'Seeds', bodyHtml: '<p>Seeds grow into plants.</p>' },
    { title: 'Worksheet', bodyHtml: '<p>Fill in the worksheet.</p>', resourceAssetIds: [pdf] },
    { title: 'Bonus', bodyHtml: '<p>Optional reading.</p>', required: false },
  ])
  const created = await api<{ id: string }>(page, 'POST', '/classes', { courseVersionId: edition.versionId, name: course })
  await api(page, 'POST', `/classes/${created.id}/activate`)
  await api(page, 'POST', `/classes/${created.id}/enrollments`, { username: student })
  await signOut(page)

  await activateAccount(page, student, studentTemp)
  await expect(page.getByRole('heading', { name: 'My learning' })).toBeVisible()
  await expect(page.getByText('0 of 2 required lessons (0%)')).toBeVisible()
  await page.getByRole('link', { name: `Continue ${course}` }).click()

  await expect(page.getByText('Seeds grow into plants.')).toBeVisible()
  await page.getByRole('button', { name: 'Mark as completed' }).click()
  await expect(page.getByRole('status').filter({ hasText: 'Lesson completed.' })).toBeVisible()
  await expect(page.getByText('1 of 2 required lessons (50%)')).toBeVisible()

  await page.getByRole('button', { name: /Worksheet/ }).click()
  await page.getByRole('button', { name: 'Open worksheet.pdf' }).click()
  await expect(page.locator('iframe[title="worksheet.pdf"]')).toBeVisible()

  await page.reload()
  await expect(page.getByText('1 of 2 required lessons (50%)')).toBeVisible()
  await expect(page.getByRole('button', { name: /Completed Seeds/ })).toBeVisible()

  await page.setViewportSize({ width: 390, height: 844 })
  await expect(page.getByRole('navigation', { name: 'Lessons' })).toBeVisible()
  const pageWidth = await page.evaluate(() => document.documentElement.scrollWidth)
  expect(pageWidth).toBeLessThanOrEqual(390)

  const other = await browser.newContext({ baseURL: 'http://localhost:5173' })
  const otherPage = await other.newPage()
  await signIn(otherPage, outsider, PASSWORD)
  await expect(otherPage.getByRole('heading', { name: 'My learning' })).toBeVisible()
  expect((await otherPage.request.get(`/api/v1/media/${pdf}/content`)).status()).toBe(404)
  await other.close()
})
