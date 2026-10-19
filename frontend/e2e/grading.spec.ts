import { expect, test } from '@playwright/test'
import { api, scheduledExam } from './api'
import { activateAccount, createAccount, PASSWORD, signIn, signInAsAdmin, signOut, unique } from './support'

test('written answers wait as pending until the teacher grades and publishes them', async ({ page }) => {
  await signInAsAdmin(page)
  const teacher = unique('marker')
  const teacherTemp = await createAccount(page, { username: teacher, displayName: 'Marker', email: `${teacher}@example.test`, roles: ['TEACHER'] })
  const student = unique('writer')
  const studentTemp = await createAccount(page, { username: student, displayName: 'আরিফ' })
  await signOut(page)
  await activateAccount(page, teacher, teacherTemp)
  const { classId, assignmentId, exam } = await scheduledExam(page, student, [
    { type: 'SINGLE_CHOICE', prompt: 'Two plus two?', points: '1', options: [{ label: 'Four', correct: true }, { label: 'Five', correct: false }] },
    { type: 'WRITTEN', prompt: 'Why is the sky blue?', points: '4', options: [] },
  ])
  await signOut(page)

  await activateAccount(page, student, studentTemp)
  const attempt = await api<{ id: string }>(page, 'POST', `/exam-assignments/${assignmentId}/attempts`)
  const single = exam.questions.find((question) => question.prompt === 'Two plus two?')!
  const essay = exam.questions.find((question) => question.prompt === 'Why is the sky blue?')!
  await api(page, 'PUT', `/attempts/${attempt.id}/answers/${single.id}`, { expectedRevision: 0, selectedOptionIds: [single.options.find((option) => option.label === 'Four')!.id] })
  await api(page, 'PUT', `/attempts/${attempt.id}/answers/${essay.id}`, { expectedRevision: 0, writtenText: 'Rayleigh scattering.' })
  await api(page, 'POST', `/attempts/${attempt.id}/submit`)
  await page.getByRole('link', { name: 'Results' }).click()
  await expect(page.getByText('Waiting for your teacher to publish the result.')).toBeVisible()
  await signOut(page)

  await signIn(page, teacher, PASSWORD)
  await page.getByRole('link', { name: 'Grading' }).click()
  await page.getByLabel('Class').selectOption(classId)
  await page.getByRole('link', { name: 'আরিফ' }).click()
  await expect(page.getByText('Rayleigh scattering.')).toBeVisible()
  await page.getByRole('button', { name: 'Finalize grades' }).click()
  await expect(page.getByRole('alert')).toContainText('Grade every written answer')

  const essayCard = page.getByRole('listitem').filter({ hasText: 'Why is the sky blue?' })
  await essayCard.getByLabel('Points (of 4)').fill('3.5')
  await essayCard.getByLabel('Feedback').fill('Good, mention wavelength.')
  await essayCard.getByRole('button', { name: 'Save grade' }).click()
  await expect(essayCard.getByText('Not graded yet')).toHaveCount(0)
  await page.getByRole('button', { name: 'Finalize grades' }).click()
  await expect(page.getByRole('status').first()).toContainText('finalized')
  await page.getByRole('button', { name: 'Publish result' }).click()
  await expect(page.getByRole('status').first()).toContainText('published')
  await signOut(page)

  await signIn(page, student, PASSWORD)
  await page.getByRole('link', { name: 'Results' }).click()
  await expect(page.getByText('4.5 / 5')).toBeVisible()
  await page.getByRole('button', { name: 'Show details for Checkpoint' }).click()
  await expect(page.getByText('Feedback: Good, mention wavelength.')).toBeVisible()
})
