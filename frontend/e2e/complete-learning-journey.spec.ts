import { expect, test } from '@playwright/test'
import { api, publishExam } from './api'
import { activateAccount, createAccount, PASSWORD, signIn, signInAsAdmin, signOut, unique } from './support'

test('publish, enroll, study, take a mixed exam, grade, publish, certify, correct and withdraw', async ({ page }) => {
  await signInAsAdmin(page)
  const teacher = unique('journey-teacher')
  const teacherTemp = await createAccount(page, { username: teacher, displayName: 'Journey Teacher', email: `${teacher}@example.test`, roles: ['TEACHER'] })
  const student = unique('journey-student')
  const studentTemp = await createAccount(page, { username: student, displayName: 'Julia Khan' })
  await signOut(page)
  await activateAccount(page, student, studentTemp)
  await signOut(page)

  // Teacher: course with a required lesson and a required mixed exam; class and enrollment through the UI.
  await activateAccount(page, teacher, teacherTemp)
  const title = unique('Journey course')
  const draftCourse = await api<{ id: string }>(page, 'POST', '/courses', { title })
  const exam = await publishExam(page, draftCourse.id, 'Final exam', [
    { type: 'TRUE_FALSE', prompt: 'Water boils at 100°C at sea level.', points: '1', options: [{ label: 'True', correct: true }, { label: 'False', correct: false }] },
    { type: 'WRITTEN', prompt: 'Describe evaporation.', points: '4', options: [] },
  ])
  const draft = await api<{ revision: number }>(page, 'GET', `/courses/${draftCourse.id}/draft`)
  const saved = await api<{ revision: number }>(page, 'PUT', `/courses/${draftCourse.id}/draft`, {
    expectedRevision: draft.revision,
    title,
    description: '',
    requiredExamVersionIds: [exam.versionId],
    sections: [{ title: 'Unit 1', lessons: [{ title: 'States of matter', bodyHtml: '<p>Solids, liquids, gases.</p>', required: true, resourceAssetIds: [] }] }],
  })
  await api(page, 'POST', `/courses/${draftCourse.id}/publish`, { expectedRevision: saved.revision })

  await page.getByRole('link', { name: 'Classes' }).click()
  await page.getByRole('button', { name: 'New class' }).click()
  await page.getByLabel('Course', { exact: true }).selectOption({ label: title })
  await expect(page.getByLabel('Edition')).toBeVisible()
  await page.getByRole('button', { name: 'Create class' }).click()
  await page.getByRole('button', { name: 'Activate class' }).click()
  await page.getByLabel('Student username').fill(student)
  await page.getByRole('button', { name: 'Enroll', exact: true }).click()
  await expect(page.getByRole('cell', { name: 'Julia Khan' })).toBeVisible()
  const classUrl = page.url()
  await signOut(page)

  // Student: study and take the exam.
  await signIn(page, student, PASSWORD)
  await page.getByRole('link', { name: `Continue ${title}` }).click()
  await page.getByRole('button', { name: 'Mark as completed' }).click()
  await expect(page.getByRole('status').filter({ hasText: 'Lesson completed.' })).toBeVisible()
  await page.getByRole('link', { name: 'Open Final exam' }).click()
  await page.getByRole('button', { name: 'Start exam' }).click()
  await page.getByRole('group', { name: /Question/ }).filter({ hasText: 'Water boils' }).getByLabel('True').check()
  const essay = page.getByRole('group', { name: /Question/ }).filter({ hasText: 'Describe evaporation.' })
  await essay.getByRole('textbox').fill('Liquid turns into vapour when heated.')
  await expect(essay.getByText('Saved', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Finish exam' }).click()
  await page.getByRole('button', { name: 'Submit answers' }).click()
  await expect(page.getByRole('status')).toContainText('grade the written answers')
  await signOut(page)

  // Teacher: grade and publish.
  await signIn(page, teacher, PASSWORD)
  await expect(page.getByRole('heading', { name: 'Courses' })).toBeVisible()
  await page.getByRole('link', { name: 'Grading' }).click()
  await page.getByLabel('Class').selectOption({ label: title })
  await page.getByRole('link', { name: 'Julia Khan' }).click()
  const essayCard = page.getByRole('listitem').filter({ hasText: 'Describe evaporation.' })
  await essayCard.getByLabel('Points (of 4)').fill('3')
  await essayCard.getByRole('button', { name: 'Save grade' }).click()
  await expect(essayCard.getByText('Not graded yet')).toHaveCount(0)
  await page.getByRole('button', { name: 'Finalize grades' }).click()
  await page.getByRole('button', { name: 'Publish result' }).click()
  await expect(page.getByRole('status').first()).toContainText('published')
  await signOut(page)

  // Student: result and certificate (completion and rendering run as background jobs).
  await signIn(page, student, PASSWORD)
  await page.getByRole('link', { name: 'Results' }).click()
  await expect(page.getByText('4 / 5')).toBeVisible()
  await page.getByRole('link', { name: 'Certificates' }).click()
  await expect(async () => {
    await page.reload()
    await expect(page.getByRole('link', { name: 'Download PDF' })).toBeVisible({ timeout: 1000 })
  }).toPass({ timeout: 30_000 })
  await signOut(page)

  // Teacher: a failing correction revokes the certificate; withdrawal keeps the student's history.
  await signIn(page, teacher, PASSWORD)
  await expect(page.getByRole('heading', { name: 'Courses' })).toBeVisible()
  await page.getByRole('link', { name: 'Grading' }).click()
  await page.getByLabel('Class').selectOption({ label: title })
  await page.getByLabel('Show').selectOption('PUBLISHED')
  await page.getByRole('link', { name: 'Julia Khan' }).click()
  await page.getByLabel('Regrade reason').fill('Essay misread')
  await page.getByRole('button', { name: 'Start regrade' }).click()
  await page.getByRole('listitem').filter({ hasText: 'Describe evaporation.' }).getByLabel('Points (of 4)').fill('0')
  await page.getByRole('listitem').filter({ hasText: 'Describe evaporation.' }).getByRole('button', { name: 'Save grade' }).click()
  await page.getByRole('button', { name: 'Finalize grades' }).click()
  await page.getByRole('button', { name: 'Publish result' }).click()
  await expect(page.getByRole('status').first()).toContainText('published')
  await page.goto(classUrl)
  await page.getByLabel(`Status of ${student}`).selectOption('WITHDRAWN')
  await expect(page.getByLabel(`Status of ${student}`)).toHaveValue('WITHDRAWN')
  await signOut(page)

  await signIn(page, student, PASSWORD)
  await page.getByRole('link', { name: 'Certificates' }).click()
  await expect(async () => {
    await page.reload()
    await expect(page.getByText('Revoked')).toBeVisible({ timeout: 1000 })
  }).toPass({ timeout: 30_000 })
  await page.getByRole('link', { name: 'Results' }).click()
  await expect(page.getByText('1 / 5')).toBeVisible()
  await page.getByRole('link', { name: 'My learning' }).click()
  await expect(page.getByText('You have left this class.')).toBeVisible()
})
