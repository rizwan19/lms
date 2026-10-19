import { expect, test } from '@playwright/test'
import { scheduledExam } from './api'
import { activateAccount, createAccount, signInAsAdmin, signOut, unique } from './support'

test('a disconnected browser shows unsaved answers, recovers on retry and never extends the deadline', async ({ page, context }) => {
  await signInAsAdmin(page)
  const teacher = unique('recovery-teacher')
  const teacherTemp = await createAccount(page, { username: teacher, displayName: 'Teacher', email: `${teacher}@example.test`, roles: ['TEACHER'] })
  const student = unique('recovery-student')
  const studentTemp = await createAccount(page, { username: student, displayName: 'Student' })
  await signOut(page)
  await activateAccount(page, teacher, teacherTemp)
  const { enrollmentId } = await scheduledExam(page, student, [{ type: 'WRITTEN', prompt: 'Explain photosynthesis.', points: '5', options: [] }])
  await signOut(page)

  await activateAccount(page, student, studentTemp)
  await page.goto(`/student/enrollments/${enrollmentId}`)
  await page.getByRole('link', { name: 'Open Checkpoint' }).click()
  await page.getByRole('button', { name: 'Start exam' }).click()
  const answer = page.getByRole('textbox', { name: 'Answer to question 1' })
  await answer.fill('Plants use light')
  await expect(page.getByText('Saved', { exact: true })).toBeVisible()

  await context.setOffline(true)
  await answer.fill('Plants use light to make sugar')
  await expect(page.getByText('Not saved — check your connection')).toBeVisible()
  await page.getByRole('button', { name: 'Finish exam' }).click()
  await expect(page.getByRole('alertdialog')).toContainText('1 answer(s) are not saved yet')
  await page.getByRole('button', { name: 'Keep working' }).click()

  await context.setOffline(false)
  await page.getByRole('button', { name: 'Retry' }).click()
  await expect(page.getByText('Saved', { exact: true })).toBeVisible()
  const before = await page.locator('.timer').innerText()
  await page.reload()
  await expect(page.getByRole('textbox', { name: 'Answer to question 1' })).toHaveValue('Plants use light to make sugar')
  const toSeconds = (time: string) => time.split(':').reduce((minutes, seconds) => Number(minutes) * 60 + Number(seconds), 0)
  expect(toSeconds(await page.locator('.timer').innerText())).toBeLessThanOrEqual(toSeconds(before))
})
