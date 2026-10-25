import { expect, type Page } from '@playwright/test'

export const ADMIN = { username: 'admin', password: 'correct horse battery' }
export const PASSWORD = 'correct horse battery'

/** Unique suffix so specs can rerun against a reused backend database. */
export const unique = (prefix: string) => `${prefix}-${Date.now().toString(36)}${Math.floor(Math.random() * 1000)}`

export async function signIn(page: Page, username: string, password: string) {
  await page.goto('/login')
  await page.getByLabel('Username or email').fill(username)
  await page.getByLabel('Password').fill(password)
  await page.getByRole('button', { name: 'Sign in' }).click()
}

export async function signOut(page: Page) {
  await page.locator('.account-menu summary').click()
  await page.getByRole('button', { name: 'Sign out' }).click()
  await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible()
}

/** Runs first-time setup on a fresh installation, otherwise signs in as the administrator. */
export async function signInAsAdmin(page: Page) {
  const setup = (await (await page.request.get('/api/v1/setup')).json()) as { required: boolean }
  if (setup.required) {
    await page.goto('/setup')
    await page.getByLabel('Workspace name').fill('Shapla Tutoring')
    await page.getByLabel('Timezone').selectOption('Asia/Dhaka')
    await page.getByLabel('Administrator username').fill(ADMIN.username)
    await page.getByLabel('Display name').fill('Administrator')
    await page.getByLabel('Email').fill('admin@example.test')
    await page.getByLabel('Password').fill(ADMIN.password)
    await page.getByRole('button', { name: 'Create administrator' }).click()
  } else {
    await signIn(page, ADMIN.username, ADMIN.password)
  }
  await expect(page.getByRole('heading', { name: 'Accounts' })).toBeVisible()
}

/** Creates an account from the accounts page and returns its one-time temporary password. */
export async function createAccount(page: Page, account: { username: string; displayName: string; email?: string; roles?: string[] }) {
  await page.goto('/admin/users')
  await page.getByRole('button', { name: 'New account' }).click()
  await page.getByLabel('Username', { exact: true }).fill(account.username)
  await page.getByLabel('Display name').fill(account.displayName)
  if (account.email) await page.getByLabel('Email', { exact: true }).fill(account.email)
  for (const role of account.roles ?? []) {
    await page.getByLabel(role.toLowerCase(), { exact: true }).check()
  }
  if (account.roles && !account.roles.includes('STUDENT')) await page.getByLabel('student', { exact: true }).uncheck()
  await page.getByRole('button', { name: 'Create account' }).click()
  const password = await page.getByTestId('temporary-password').innerText()
  await page.getByRole('button', { name: 'Done' }).click()
  return password
}

/** Signs in with a temporary password and completes the forced change to {@link PASSWORD}. */
export async function activateAccount(page: Page, username: string, temporaryPassword: string) {
  await signIn(page, username, temporaryPassword)
  await expect(page.getByRole('heading', { name: 'Change password' })).toBeVisible()
  await page.getByLabel('Current password').fill(temporaryPassword)
  await page.getByLabel('New password', { exact: true }).fill(PASSWORD)
  await page.getByLabel('Confirm new password').fill(PASSWORD)
  await page.getByRole('button', { name: 'Change password' }).click()
  await expect(page.getByRole('heading', { name: 'Change password' })).toBeHidden()
}
