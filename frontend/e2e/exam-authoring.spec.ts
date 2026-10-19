import { expect, test } from '@playwright/test'
import { api } from './api'
import { activateAccount, createAccount, signInAsAdmin, signOut, unique } from './support'

test('teacher authors a mixed exam, requires it in a course edition and schedules it for a class', async ({ page }) => {
  await signInAsAdmin(page)
  const teacher = unique('examiner')
  const temporary = await createAccount(page, { username: teacher, displayName: 'Examiner', email: `${teacher}@example.test`, roles: ['TEACHER'] })
  await signOut(page)
  await activateAccount(page, teacher, temporary)

  const title = unique('Statistics')
  const course = await api<{ id: string }>(page, 'POST', '/courses', { title })
  const draft = await api<{ revision: number }>(page, 'GET', `/courses/${course.id}/draft`)
  await api(page, 'PUT', `/courses/${course.id}/draft`, {
    expectedRevision: draft.revision,
    title,
    description: '',
    sections: [{ title: 'Unit 1', lessons: [{ title: 'Mean', bodyHtml: '<p>Mean</p>', required: true, resourceAssetIds: [] }] }],
  })

  await page.goto(`/teacher/courses/${course.id}/edit`)
  await page.getByLabel('New exam title').fill('Midterm')
  await page.getByRole('button', { name: 'Create exam' }).click()
  await expect(page.getByLabel('Exam title')).toHaveValue('Midterm')

  await page.getByRole('button', { name: 'Add question' }).click()
  const first = page.getByRole('group', { name: 'Question 1' })
  await first.getByLabel('Question', { exact: true }).fill('What is the mean of 2 and 4?')
  await first.getByLabel('Answer 1', { exact: true }).fill('3')
  await first.getByLabel('Answer 2', { exact: true }).fill('6')

  await page.getByRole('button', { name: 'Add question' }).click()
  const second = page.getByRole('group', { name: 'Question 2' })
  await second.getByLabel('Question type').selectOption('TRUE_FALSE')
  await second.getByLabel('Question', { exact: true }).fill('The median is always the mean.')
  await second.getByLabel('Answer 2 is correct').check()

  await page.getByRole('button', { name: 'Add question' }).click()
  const third = page.getByRole('group', { name: 'Question 3' })
  await third.getByLabel('Question type').selectOption('WRITTEN')
  await third.getByLabel('Question', { exact: true }).fill('Explain when the median is better.')
  await third.getByLabel('Points').fill('5')
  await third.getByLabel('Grading notes (teachers only)').fill('Mentions outliers')

  await page.getByRole('button', { name: 'Save exam draft' }).click()
  await expect(page.getByRole('status').filter({ hasText: 'Exam draft saved.' })).toBeVisible()
  await page.getByRole('button', { name: 'Publish exam' }).click()
  await expect(page.getByRole('status').filter({ hasText: 'Published exam edition 1.' })).toBeVisible()

  await page.getByRole('link', { name: 'Back to course' }).click()
  await page.getByLabel('Midterm (edition 1)').check()
  await page.getByRole('button', { name: 'Save draft' }).click()
  await expect(page.getByRole('status').filter({ hasText: 'Draft saved.' })).toBeVisible()
  await page.getByRole('button', { name: 'Publish edition' }).click()
  await expect(page.getByRole('status').filter({ hasText: 'Published edition 1.' })).toBeVisible()

  const detail = await api<{ editions: { versionId: string }[] }>(page, 'GET', `/courses/${course.id}`)
  const created = await api<{ id: string }>(page, 'POST', '/classes', { courseVersionId: detail.editions[0].versionId, name: title })
  await api(page, 'POST', `/classes/${created.id}/activate`)

  await page.goto(`/teacher/classes/${created.id}`)
  const schedule = page.getByRole('region', { name: 'Exams' })
  await expect(schedule.getByText('Midterm', { exact: true })).toBeVisible()
  await expect(schedule.getByText(/required/)).toBeVisible()
  await expect(schedule.getByText(/60 minutes/)).toBeVisible()

  await schedule.getByRole('button', { name: 'Edit schedule for Midterm' }).click()
  await schedule.getByLabel('Exam opens (Asia/Dhaka)').fill('2030-11-01T09:00')
  await schedule.getByLabel('Exam closes (Asia/Dhaka)').fill('2030-11-01T09:00')
  await schedule.getByRole('button', { name: 'Save exam schedule' }).click()
  await expect(schedule.getByText('The closing time must be after the opening time')).toBeVisible()
  await schedule.getByLabel('Exam closes (Asia/Dhaka)').fill('2030-11-01T11:00')
  await schedule.getByRole('button', { name: 'Save exam schedule' }).click()
  await expect(schedule.getByText(/Opens Nov 1, 2030, 9:00/)).toBeVisible()

  const assignments = await api<{ schedule: { opensAt: string } }[]>(page, 'GET', `/exam-assignments?classId=${created.id}`)
  expect(assignments[0].schedule.opensAt).toBe('2030-11-01T03:00:00Z')
})
