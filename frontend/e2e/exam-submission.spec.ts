import { expect, test } from '@playwright/test'
import { scheduledExam } from './api'
import { activateAccount, createAccount, signInAsAdmin, signOut, unique } from './support'

test('student answers with autosave, survives a reload without extending the deadline, then submits', async ({ page }) => {
  await signInAsAdmin(page)
  const teacher = unique('proctor')
  const teacherTemp = await createAccount(page, { username: teacher, displayName: 'Teacher', email: `${teacher}@example.test`, roles: ['TEACHER'] })
  const student = unique('candidate')
  const studentTemp = await createAccount(page, { username: student, displayName: 'Candidate' })
  await signOut(page)
  await activateAccount(page, teacher, teacherTemp)
  const { enrollmentId } = await scheduledExam(page, student, [
    { type: 'SINGLE_CHOICE', prompt: 'Which planet is largest?', points: '1', options: [{ label: 'Jupiter', correct: true }, { label: 'Mars', correct: false }] },
    { type: 'WRITTEN', prompt: 'Describe the water cycle.', points: '4', options: [] },
  ])
  await signOut(page)

  await activateAccount(page, student, studentTemp)
  await page.goto(`/student/enrollments/${enrollmentId}`)
  await page.getByRole('link', { name: 'Open Checkpoint' }).click()
  await page.getByRole('button', { name: 'Start exam' }).click()

  const planet = page.getByRole('group', { name: /Question/ }).filter({ hasText: 'Which planet is largest?' })
  await planet.getByLabel('Jupiter').check()
  await expect(planet.getByText('Saved', { exact: true })).toBeVisible()
  const essay = page.getByRole('group', { name: /Question/ }).filter({ hasText: 'Describe the water cycle.' })
  await essay.getByRole('textbox').fill('Evaporation, condensation, precipitation.')
  await expect(essay.getByText('Saved', { exact: true })).toBeVisible()

  const before = await page.locator('.timer').innerText()
  await page.reload()
  await expect(page.getByRole('group', { name: /Question/ }).filter({ hasText: 'Which planet is largest?' }).getByLabel('Jupiter')).toBeChecked()
  await expect(page.getByRole('textbox', { name: /Answer to question/ })).toHaveValue('Evaporation, condensation, precipitation.')
  const toSeconds = (time: string) => time.split(':').reduce((minutes, seconds) => Number(minutes) * 60 + Number(seconds), 0)
  expect(toSeconds(await page.locator('.timer').innerText())).toBeLessThanOrEqual(toSeconds(before))

  await page.getByRole('button', { name: 'Finish exam' }).click()
  await expect(page.getByRole('alertdialog')).toContainText('All answers are saved.')
  await page.getByRole('button', { name: 'Submit answers' }).click()
  await expect(page.getByRole('status')).toContainText('Your teacher will grade the written answers.')

  await page.goto(`/student/enrollments/${enrollmentId}`)
  await expect(page.getByRole('link', { name: /Checkpoint/ })).toHaveCount(0)
})
