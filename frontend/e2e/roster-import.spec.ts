import { expect, test } from '@playwright/test'
import { publishCourse } from './api'
import { activateAccount, createAccount, signInAsAdmin, signOut, unique } from './support'

test('teacher previews, fixes and imports a roster with and without email addresses', async ({ page }) => {
  await signInAsAdmin(page)
  const teacher = unique('importer')
  const temporary = await createAccount(page, { username: teacher, displayName: 'Importer', email: `${teacher}@example.test`, roles: ['TEACHER'] })
  await signOut(page)
  await activateAccount(page, teacher, temporary)

  const course = unique('History')
  await publishCourse(page, course, [{ title: 'Ancient', bodyHtml: '<p>Ancient</p>' }])
  await page.getByRole('link', { name: 'Classes' }).click()
  await page.getByRole('button', { name: 'New class' }).click()
  await page.getByLabel('Course', { exact: true }).selectOption({ label: course })
  await expect(page.getByLabel('Edition')).toBeVisible()
  await page.getByRole('button', { name: 'Create class' }).click()
  await page.getByRole('button', { name: 'Activate class' }).click()

  const withEmail = unique('mina')
  const withoutEmail = unique('rafi')
  await page.getByRole('button', { name: 'Import CSV' }).click()
  const dialog = page.getByRole('region', { name: 'Import students' })
  const csv = (rows: string[]) => ({ name: 'roster.csv', mimeType: 'text/csv', buffer: Buffer.from(['﻿username,display_name,email', ...rows].join('\n')) })

  await dialog.getByLabel('CSV file').setInputFiles(csv([`${withEmail},মিনা,${withEmail}@example.test`, `${withoutEmail},রাফি,`, `${withoutEmail},Duplicate,`]))
  await expect(dialog.getByText('Duplicate username (also on line 3)')).toBeVisible()
  await expect(dialog.getByRole('button', { name: 'Import 3 students' })).toBeDisabled()

  await dialog.getByLabel('CSV file').setInputFiles(csv([`${withEmail},মিনা,${withEmail}@example.test`, `${withoutEmail},রাফি,`]))
  await expect(dialog.getByText('2 new, 0 existing, 0 to fix.')).toBeVisible()
  await dialog.getByRole('button', { name: 'Import 2 students' }).click()

  // This environment has no SMTP, so both students receive one-time temporary passwords.
  const result = page.getByRole('region', { name: 'Import result' })
  await expect(result.getByRole('status').first()).toContainText('Enrolled 2 students')
  const rafiPassword = await result.getByRole('row', { name: new RegExp(withoutEmail) }).getByTestId('temporary-password').innerText()
  await result.getByRole('button', { name: 'Done' }).click()
  await expect(page.getByRole('cell', { name: 'মিনা' })).toBeVisible()
  await expect(page.getByRole('cell', { name: 'রাফি' })).toBeVisible()

  await signOut(page)
  await activateAccount(page, withoutEmail, rafiPassword)
})
