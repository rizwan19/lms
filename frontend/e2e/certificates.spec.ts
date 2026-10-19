import { expect, test } from '@playwright/test'
import { api, publishCourse } from './api'
import { activateAccount, createAccount, PASSWORD, signIn, signInAsAdmin, signOut, unique } from './support'

test('completing a course issues a downloadable certificate that teachers can revoke', async ({ page }) => {
  await signInAsAdmin(page)
  const teacher = unique('certifier')
  const teacherTemp = await createAccount(page, { username: teacher, displayName: 'Certifier', email: `${teacher}@example.test`, roles: ['TEACHER'] })
  const student = unique('graduate')
  const studentTemp = await createAccount(page, { username: student, displayName: 'Farhan Ahmed' })
  await signOut(page)

  await activateAccount(page, teacher, teacherTemp)
  const course = await publishCourse(page, unique('Chemistry'), [{ title: 'Atoms', bodyHtml: '<p>Atoms</p>' }])
  const created = await api<{ id: string }>(page, 'POST', '/classes', { courseVersionId: course.versionId, name: 'Chemistry A' })
  await api(page, 'POST', `/classes/${created.id}/activate`)
  const enrollment = await api<{ enrollmentId: string }>(page, 'POST', `/classes/${created.id}/enrollments`, { username: student })
  await signOut(page)

  await activateAccount(page, student, studentTemp)
  await page.goto(`/student/enrollments/${enrollment.enrollmentId}`)
  await page.getByRole('button', { name: 'Mark as completed' }).click()
  await expect(page.getByRole('status').filter({ hasText: 'Lesson completed.' })).toBeVisible()

  await page.getByRole('link', { name: 'Certificates' }).click()
  await expect(async () => {
    await page.reload()
    await expect(page.getByRole('link', { name: 'Download PDF' })).toBeVisible({ timeout: 1000 })
  }).toPass({ timeout: 30_000 })
  const href = await page.getByRole('link', { name: 'Download PDF' }).getAttribute('href')
  const pdf = await page.request.get(href!)
  expect(pdf.headers()['content-type']).toBe('application/pdf')
  expect((await pdf.body()).subarray(0, 5).toString()).toBe('%PDF-')
  await signOut(page)

  await signIn(page, teacher, PASSWORD)
  await expect(page.getByRole('heading', { name: 'Courses' })).toBeVisible()
  await page.goto(`/teacher/classes/${created.id}`)
  const certificates = page.getByRole('region', { name: 'Certificates' })
  await expect(certificates.getByRole('cell', { name: 'Farhan Ahmed' })).toBeVisible()
  await certificates.getByLabel('Reason for Farhan Ahmed').fill('Issued to the wrong class')
  await certificates.getByRole('button', { name: 'Revoke' }).click()
  await expect(certificates.getByText('Issued to the wrong class')).toBeVisible()
  await signOut(page)

  await signIn(page, student, PASSWORD)
  await page.getByRole('link', { name: 'Certificates' }).click()
  await expect(page.getByText('Revoked')).toBeVisible()
  await expect(page.getByRole('link', { name: 'Download PDF' })).toHaveCount(0)
})

test('the public verification page reveals nothing for unknown codes', async ({ page }) => {
  await page.goto('/verify/not-a-real-code')
  await expect(page.getByRole('status')).toHaveText('No certificate can be verified with this link.')
})
