import { expect, test, type Page } from '@playwright/test'
import { publishCourse } from './api'
import { activateAccount, createAccount, signInAsAdmin, signOut, unique } from './support'

async function createClass(page: Page, course: string, name: string) {
  await page.getByRole('link', { name: 'Classes' }).click()
  await page.getByRole('button', { name: 'New class' }).click()
  await page.getByLabel('Course', { exact: true }).selectOption({ label: course })
  await expect(page.getByLabel('Edition')).toBeVisible()
  await expect(page.getByLabel('Class name')).toHaveValue(course)
  await page.getByLabel('Class name').fill(name)
  await page.getByRole('button', { name: 'Create class' }).click()
  await expect(page.getByRole('heading', { name })).toBeVisible()
  await page.getByRole('button', { name: 'Activate class' }).click()
  await expect(page.getByRole('button', { name: 'Activate class' })).toBeHidden()
}

async function createStudentInClass(page: Page, username: string, displayName: string) {
  await page.getByRole('button', { name: 'Create new student' }).click()
  await page.getByLabel('Username', { exact: true }).fill(username)
  await page.getByLabel('Display name').fill(displayName)
  await page.getByRole('button', { name: 'Create account' }).click()
  await expect(page.getByTestId('temporary-password')).toBeVisible()
  await expect(page.getByRole('cell', { name: displayName })).toBeVisible()
  await page.getByRole('button', { name: 'Done' }).click()
}

test('one course edition serves two classes with isolated rosters', async ({ page }) => {
  await signInAsAdmin(page)
  const teacher = unique('tutor')
  const temporary = await createAccount(page, { username: teacher, displayName: 'Tutor', email: `${teacher}@example.test`, roles: ['TEACHER'] })
  await signOut(page)
  await activateAccount(page, teacher, temporary)

  const course = unique('Geometry')
  await publishCourse(page, course, [{ title: 'Angles', bodyHtml: '<p>Angles</p>' }])

  const morning = unique('Morning')
  await createClass(page, course, morning)
  const first = unique('student')
  await createStudentInClass(page, first, 'সাকিব হাসান')

  const evening = unique('Evening')
  await createClass(page, course, evening)
  const second = unique('student')
  await createStudentInClass(page, second, 'Mitu Akter')
  await expect(page.getByRole('cell', { name: 'সাকিব হাসান' })).toHaveCount(0)

  await page.getByRole('link', { name: 'Classes' }).click()
  await page.getByRole('link', { name: morning }).click()
  await expect(page.getByRole('cell', { name: 'সাকিব হাসান' })).toBeVisible()
  await expect(page.getByRole('cell', { name: 'Mitu Akter' })).toHaveCount(0)

  await page.getByLabel(`Status of ${first}`).selectOption('SUSPENDED')
  await expect(page.getByLabel(`Status of ${first}`)).toHaveValue('SUSPENDED')
  await page.getByLabel('Show').selectOption('ACTIVE')
  await expect(page.getByText('No students on this roster.')).toBeVisible()
})
