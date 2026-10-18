import { expect, test } from '@playwright/test'
import { readFileSync } from 'node:fs'
import { activateAccount, createAccount, signInAsAdmin, signOut, unique } from './support'

const VIDEO = readFileSync(new URL('../../backend/src/test/resources/media/h264-aac.mp4', import.meta.url))
const PDF = Buffer.from('%PDF-1.4\n%%EOF\n')

test('teacher authors, previews and publishes video, PDF and text lessons, then corrects with a new edition', async ({ page }) => {
  await signInAsAdmin(page)
  const teacher = unique('author')
  const temporary = await createAccount(page, { username: teacher, displayName: 'Course Author', email: `${teacher}@example.test`, roles: ['TEACHER'] })
  await signOut(page)
  await activateAccount(page, teacher, temporary)

  await expect(page.getByRole('heading', { name: 'Courses' })).toBeVisible()
  await page.getByLabel('New course title').fill('Physics 101')
  await page.getByRole('button', { name: 'Create course' }).click()
  await expect(page.getByLabel('Course title')).toHaveValue('Physics 101')

  await page.getByRole('button', { name: 'Add section' }).click()
  await page.getByLabel('Section 1 title').fill('Motion')

  const first = page.getByRole('group', { name: 'Lesson 1.1' })
  await first.getByLabel('Lesson title').fill('Reading: speed')
  await first.getByRole('textbox', { name: 'Lesson text' }).fill('Speed is distance over time.')
  await first.getByLabel('Add PDF or text document').setInputFiles({ name: 'speed.pdf', mimeType: 'application/pdf', buffer: PDF })
  await expect(first.getByText('Uploaded speed.pdf')).toBeVisible()

  await page.getByRole('button', { name: 'Add lesson to section 1' }).click()
  const second = page.getByRole('group', { name: 'Lesson 1.2' })
  await second.getByLabel('Lesson title').fill('Watch: a moving car')
  await second.getByLabel('Video', { exact: true }).selectOption('upload')
  await second.getByLabel('Upload MP4 video').setInputFiles({ name: 'car.mp4', mimeType: 'video/mp4', buffer: VIDEO })
  await expect(second.getByText('Uploaded car.mp4')).toBeVisible()

  await page.getByRole('button', { name: 'Add lesson to section 1' }).click()
  const third = page.getByRole('group', { name: 'Lesson 1.3' })
  await third.getByLabel('Lesson title').fill('Optional: lecture')
  await third.getByLabel('Required for completion').uncheck()
  await third.getByLabel('Video', { exact: true }).selectOption('YOUTUBE')
  await third.getByLabel('YouTube video ID').fill('https://youtube.com/watch?v=dQw4w9WgXcQ')
  await page.getByRole('button', { name: 'Save draft' }).click()
  await expect(third.getByText('Enter the video ID from YouTube or Vimeo, not a link')).toBeVisible()
  await third.getByLabel('YouTube video ID').fill('dQw4w9WgXcQ')
  await page.getByRole('button', { name: 'Save draft' }).click()
  await expect(page.getByRole('status').filter({ hasText: 'Draft saved.' })).toBeVisible()

  await page.getByRole('link', { name: 'Preview' }).click()
  await expect(page.getByRole('heading', { name: /Physics 101/ })).toContainText('Draft preview')
  await expect(page.getByText('Speed is distance over time.')).toBeVisible()
  await expect(page.getByRole('link', { name: 'speed.pdf' })).toBeVisible()
  await page.getByRole('button', { name: /Watch: a moving car/ }).click()
  await expect(page.getByLabel('Video: Watch: a moving car')).toBeVisible()
  await page.getByRole('button', { name: /Optional: lecture/ }).click()
  await expect(page.locator('iframe[title="Video: Optional: lecture"]')).toHaveAttribute('src', /youtube-nocookie\.com\/embed\/dQw4w9WgXcQ/)

  await page.getByRole('link', { name: 'Back to editor' }).click()
  await page.getByRole('button', { name: 'Publish edition' }).click()
  await expect(page.getByRole('status').filter({ hasText: 'Published edition 1.' })).toBeVisible()

  await page.reload()
  await page.getByRole('button', { name: 'Start new draft from edition 1' }).click()
  const corrected = page.getByRole('group', { name: 'Lesson 1.1' })
  await corrected.getByRole('textbox', { name: 'Lesson text' }).fill('Speed is distance divided by time.')
  await page.getByRole('button', { name: 'Save draft' }).click()
  await expect(page.getByRole('status').filter({ hasText: 'Draft saved.' })).toBeVisible()
  await page.getByRole('button', { name: 'Publish edition' }).click()
  await expect(page.getByRole('status').filter({ hasText: 'Published edition 2.' })).toBeVisible()

  await page.reload()
  await page.getByRole('link', { name: 'Preview edition 1' }).click()
  await expect(page.getByText('Speed is distance over time.')).toBeVisible()
  await expect(page.getByText('divided by')).toHaveCount(0)
})
