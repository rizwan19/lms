import { expect, test } from '@playwright/test'
import { api, publishCourse } from './api'
import { activateAccount, createAccount, signInAsAdmin, signOut, unique } from './support'

test('teacher reads the progress matrix with the keyboard and downloads a CSV export', async ({ page }) => {
  await signInAsAdmin(page)
  const teacher = unique('reporter')
  const teacherTemp = await createAccount(page, { username: teacher, displayName: 'Reporter', email: `${teacher}@example.test`, roles: ['TEACHER'] })
  const student = unique('learner')
  await createAccount(page, { username: student, displayName: 'তাসনিম রহমান' })
  await signOut(page)
  await activateAccount(page, teacher, teacherTemp)

  const course = await publishCourse(page, unique('Biology'), [{ title: 'Cells', bodyHtml: '<p>Cells</p>' }])
  const created = await api<{ id: string }>(page, 'POST', '/classes', { courseVersionId: course.versionId, name: 'Biology A' })
  await api(page, 'POST', `/classes/${created.id}/activate`)
  await api(page, 'POST', `/classes/${created.id}/enrollments`, { username: student })

  await page.goto(`/teacher/classes/${created.id}`)
  await page.getByRole('link', { name: 'Reports' }).focus()
  await page.keyboard.press('Enter')
  await expect(page.getByRole('rowheader', { name: /তাসনিম রহমান/ })).toBeVisible()
  await expect(page.getByRole('cell', { name: '0/1 (0%)' })).toBeVisible()
  await page.getByRole('button', { name: 'Scores', exact: true }).focus()
  await page.keyboard.press('Enter')
  await expect(page.getByRole('button', { name: 'Scores', exact: true })).toHaveAttribute('aria-pressed', 'true')

  await page.getByRole('button', { name: 'Export progress' }).click()
  const link = page.getByRole('link', { name: 'Download progress CSV' })
  await expect(link).toBeVisible({ timeout: 20_000 })
  const csv = await page.request.get((await link.getAttribute('href'))!)
  expect(await csv.text()).toContain('তাসনিম রহমান')
})
