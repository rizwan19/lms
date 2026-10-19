import { expect, type Page } from '@playwright/test'

/** Calls the API with the page's session cookies and CSRF token, for fast test setup. */
export async function api<T>(page: Page, method: 'GET' | 'POST' | 'PUT', path: string, data?: unknown): Promise<T> {
  await page.request.get('/api/v1/auth/csrf')
  const token = (await page.context().cookies()).find((cookie) => cookie.name === 'XSRF-TOKEN')?.value ?? ''
  const response = await page.request.fetch(`/api/v1${path}`, { method, data, headers: { 'X-XSRF-TOKEN': decodeURIComponent(token) } })
  expect(response.ok(), `${method} ${path}: ${await response.text()}`).toBeTruthy()
  return (response.status() === 204 ? undefined : await response.json()) as T
}

type Lesson = { title: string; bodyHtml: string; required?: boolean; resourceAssetIds?: string[]; videoAssetId?: string }

/** Creates and publishes a one-section course as the signed-in teacher; returns the edition. */
export async function publishCourse(page: Page, title: string, lessons: Lesson[]) {
  const course = await api<{ id: string }>(page, 'POST', '/courses', { title })
  const draft = await api<{ revision: number }>(page, 'GET', `/courses/${course.id}/draft`)
  const saved = await api<{ revision: number }>(page, 'PUT', `/courses/${course.id}/draft`, {
    expectedRevision: draft.revision,
    title,
    description: '',
    sections: [{ title: 'Unit 1', lessons: lessons.map((lesson) => ({ required: true, resourceAssetIds: [], ...lesson })) }],
  })
  return api<{ versionId: string; courseId: string; sections: { lessons: { id: string }[] }[] }>(page, 'POST', `/courses/${course.id}/publish`, {
    expectedRevision: saved.revision,
  })
}

type Question = { type: string; prompt: string; points: string; options: { label: string; correct: boolean }[] }

/** Creates and publishes an exam for a course as the signed-in author; returns the edition. */
export async function publishExam(page: Page, courseId: string, title: string, questions: Question[]) {
  const exam = await api<{ id: string }>(page, 'POST', '/exams', { courseId, title })
  const draft = await api<{ revision: number }>(page, 'GET', `/exams/${exam.id}/draft`)
  const saved = await api<{ revision: number }>(page, 'PUT', `/exams/${exam.id}/draft`, {
    expectedRevision: draft.revision,
    title,
    instructions: 'Answer every question.',
    passPercent: '50',
    questions: questions.map((question) => ({ explanation: 'Explained.', gradingNotes: '', ...question })),
  })
  return api<{ versionId: string; questions: { id: string; prompt: string; options: { id: string; label: string }[] }[] }>(page, 'POST', `/exams/${exam.id}/publish`, {
    expectedRevision: saved.revision,
  })
}

/** Publishes a course and exam, creates an active class, enrolls the student and schedules the exam. */
export async function scheduledExam(page: Page, student: string, questions: Question[], schedule: Record<string, unknown> = {}) {
  const course = await publishCourse(page, `Exam course ${Date.now()}`, [{ title: 'Lesson', bodyHtml: '<p>Lesson</p>' }])
  const exam = await publishExam(page, course.courseId, 'Checkpoint', questions)
  const created = await api<{ id: string }>(page, 'POST', '/classes', { courseVersionId: course.versionId, name: 'Exam class' })
  await api(page, 'POST', `/classes/${created.id}/activate`)
  const enrollment = await api<{ enrollmentId: string }>(page, 'POST', `/classes/${created.id}/enrollments`, { username: student })
  const assignment = await api<{ id: string }>(page, 'POST', '/exam-assignments', {
    classId: created.id,
    examVersionId: exam.versionId,
    durationMinutes: 30,
    maxAttempts: 1,
    ...schedule,
  })
  return { classId: created.id, enrollmentId: enrollment.enrollmentId, assignmentId: assignment.id, exam }
}
