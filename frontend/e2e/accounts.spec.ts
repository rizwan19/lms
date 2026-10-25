import { expect, test } from '@playwright/test'
import { activateAccount, createAccount, PASSWORD, signIn, signInAsAdmin, signOut, unique } from './support'

test('administrator creates staff and students; teachers manage their own students', async ({ page, context }) => {
  await signInAsAdmin(page)

  const cookies = await context.cookies()
  const session = cookies.find((cookie) => cookie.name === 'LEARNING_SESSION')
  expect(session?.httpOnly).toBe(true)
  expect(session?.sameSite).toBe('Lax')

  const teacher = unique('teacher')
  const teacherTemp = await createAccount(page, { username: teacher, displayName: 'Nasrin Akter', email: `${teacher}@example.test`, roles: ['TEACHER'] })
  await signOut(page)

  await activateAccount(page, teacher, teacherTemp)
  await expect(page.getByRole('heading', { name: 'Courses' })).toBeVisible()
  await page.getByRole('link', { name: 'Students' }).click()

  const student = unique('student')
  const studentTemp = await createAccount(page, { username: student, displayName: 'রহিম উদ্দিন' })
  await expect(page.getByRole('cell', { name: 'রহিম উদ্দিন' })).toBeVisible()
  await signOut(page)

  await activateAccount(page, student, studentTemp)
  await signOut(page)

  // Assisted reset by the teacher who created the student.
  await signIn(page, teacher, PASSWORD)
  await page.getByRole('link', { name: 'Students' }).click()
  await page.getByRole('button', { name: `Reset password for ${student}` }).click()
  const resetPassword = await page.getByTestId('temporary-password').innerText()
  await signOut(page)
  await activateAccount(page, student, resetPassword)
  await signOut(page)

  // Disabling the teacher blocks sign-in.
  await signInAsAdmin(page)
  await page.getByLabel('Search').fill(teacher)
  await page.getByRole('button', { name: `Disable ${teacher}` }).click()
  await expect(page.getByRole('button', { name: `Enable ${teacher}` })).toBeVisible()
  await signOut(page)
  await signIn(page, teacher, PASSWORD)
  await expect(page.getByRole('alert')).toHaveText('Invalid username or password')
})

test('administrator updates workspace settings', async ({ page }) => {
  await signInAsAdmin(page)
  await page.getByRole('link', { name: 'Settings' }).click()
  const name = unique('Shapla Tutoring')
  await page.getByLabel('Workspace name').fill(name)
  await page.getByRole('button', { name: 'Save settings' }).click()
  await expect(page.getByRole('status')).toHaveText('Saved.')
  await expect(page.locator('.brand')).toHaveText(name)
})
