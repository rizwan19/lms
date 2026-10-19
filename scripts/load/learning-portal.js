// Planning-workload load test (spec §4): 1,000 enrolled students, 100 active browser sessions, 30 of them taking an exam at once.
// Synthetic data only. Usage:
//   BASE_URL=http://localhost:8088 ADMIN_USERNAME=admin ADMIN_PASSWORD='...' k6 run scripts/load/learning-portal.js
// Target: non-media API p95 below 500 ms. Sign-in is excluded (adaptive password hashing is deliberately slow).
import http from 'k6/http'
import { check, sleep } from 'k6'
import exec from 'k6/execution'

const BASE = `${__ENV.BASE_URL || 'http://localhost:8088'}/api/v1`
const PASSWORD = 'load-test-password-1234'
const ENROLLED = 1000
const STUDENTS = 100
const EXAM_TAKERS = 30
const DURATION = __ENV.DURATION || '3m'

export const options = {
  setupTimeout: '10m',
  scenarios: {
    learners: { executor: 'constant-vus', vus: STUDENTS - EXAM_TAKERS, duration: DURATION, exec: 'learn' },
    examTakers: { executor: 'constant-vus', vus: EXAM_TAKERS, duration: DURATION, exec: 'takeExam' },
  },
  thresholds: {
    'http_req_duration{kind:api}': ['p(95)<500'],
    'checks': ['rate>0.99'],
  },
}

function csrf(jar) {
  http.get(`${BASE}/auth/csrf`, { jar, tags: { kind: 'auth' } })
  return decodeURIComponent(jar.cookiesForURL(BASE)['XSRF-TOKEN'][0])
}

function session(username, password) {
  const jar = new http.CookieJar()
  const token = csrf(jar)
  const login = http.post(`${BASE}/auth/login`, JSON.stringify({ username, password }), {
    jar,
    headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': token },
    tags: { kind: 'auth' },
  })
  check(login, { 'signed in': (r) => r.status === 200 })
  return { jar, token: csrf(jar) }
}

function call(s, method, path, body, kind = 'api') {
  const params = { jar: s.jar, headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': s.token }, tags: { kind } }
  return http.request(method, `${BASE}${path}`, body === undefined ? null : JSON.stringify(body), params)
}

export function setup() {
  const admin = session(__ENV.ADMIN_USERNAME || 'admin', __ENV.ADMIN_PASSWORD)
  const stamp = Date.now().toString(36)
  const course = call(admin, 'POST', '/courses', { title: `Load course ${stamp}` }, 'setup').json()
  const exam = call(admin, 'POST', '/exams', { courseId: course.id, title: 'Load exam' }, 'setup').json()
  let draft = call(admin, 'GET', `/exams/${exam.id}/draft`, undefined, 'setup').json()
  const questions = []
  for (let i = 0; i < 10; i++) {
    questions.push({ type: 'SINGLE_CHOICE', prompt: `Question ${i}`, points: '1', explanation: '', gradingNotes: '', options: [{ label: 'A', correct: true }, { label: 'B', correct: false }] })
  }
  questions.push({ type: 'WRITTEN', prompt: 'Essay', points: '5', explanation: '', gradingNotes: '', options: [] })
  draft = call(admin, 'PUT', `/exams/${exam.id}/draft`, { expectedRevision: draft.revision, title: 'Load exam', instructions: '', passPercent: '50', questions }, 'setup').json()
  const edition = call(admin, 'POST', `/exams/${exam.id}/publish`, { expectedRevision: draft.revision }, 'setup').json()
  let courseDraft = call(admin, 'GET', `/courses/${course.id}/draft`, undefined, 'setup').json()
  const lessons = []
  for (let i = 0; i < 20; i++) lessons.push({ title: `Lesson ${i}`, bodyHtml: `<p>Lesson ${i} text.</p>`, required: true, resourceAssetIds: [] })
  courseDraft = call(admin, 'PUT', `/courses/${course.id}/draft`, { expectedRevision: courseDraft.revision, title: `Load course ${stamp}`, description: '', sections: [{ title: 'Unit', lessons }], requiredExamVersionIds: [] }, 'setup').json()
  const published = call(admin, 'POST', `/courses/${course.id}/publish`, { expectedRevision: courseDraft.revision }, 'setup').json()
  const klass = call(admin, 'POST', '/classes', { courseVersionId: published.versionId, name: `Load class ${stamp}` }, 'setup').json()
  call(admin, 'POST', `/classes/${klass.id}/activate`, {}, 'setup')
  const assignment = call(admin, 'POST', '/exam-assignments', { classId: klass.id, examVersionId: edition.versionId, durationMinutes: 120, maxAttempts: 10 }, 'setup').json()

  const students = []
  for (let i = 0; i < ENROLLED; i++) {
    const username = `load-${stamp}-${i}`
    const created = call(admin, 'POST', '/users', { username, displayName: `Load Student ${i}`, email: '', roles: ['STUDENT'], delivery: 'TEMPORARY_PASSWORD' }, 'setup').json()
    const enrollment = call(admin, 'POST', `/classes/${klass.id}/enrollments`, { username }, 'setup').json()
    if (i >= STUDENTS) continue
    const temporary = created.temporaryCredential.temporaryPassword
    call(session(username, temporary), 'PUT', '/auth/me/password', { currentPassword: temporary, newPassword: PASSWORD }, 'setup')
    students.push({ username, enrollmentId: enrollment.enrollmentId })
  }
  return { students, lessonIds: published.sections[0].lessons.map((lesson) => lesson.id), assignmentId: assignment.id, questions: edition.questions }
}

const state = {}

function me(data) {
  if (!state.session) {
    const student = data.students[(exec.vu.idInTest - 1) % data.students.length]
    state.session = session(student.username, PASSWORD)
    state.student = student
    state.revisions = {}
  }
  return state
}

export function learn(data) {
  const s = me(data)
  check(call(s.session, 'GET', '/me/learning'), { dashboard: (r) => r.status === 200 })
  check(call(s.session, 'GET', `/me/enrollments/${s.student.enrollmentId}`), { player: (r) => r.status === 200 })
  const lesson = data.lessonIds[Math.floor(Math.random() * data.lessonIds.length)]
  const revision = s.revisions[lesson] || 0
  const saved = call(s.session, 'PUT', `/enrollments/${s.student.enrollmentId}/lessons/${lesson}/progress`, { expectedRevision: revision, completed: false, resumeSeconds: revision * 15 })
  check(saved, { progress: (r) => r.status === 200 })
  if (saved.status === 200) s.revisions[lesson] = saved.json('revision')
  sleep(1 + Math.random() * 2)
}

export function takeExam(data) {
  const s = me(data)
  if (!s.attempt) {
    const started = call(s.session, 'POST', `/exam-assignments/${data.assignmentId}/attempts`, {})
    check(started, { started: (r) => r.status === 200 })
    s.attempt = started.json()
    s.answerRevisions = {}
  }
  const question = s.attempt.questions[Math.floor(Math.random() * s.attempt.questions.length)]
  const revision = s.answerRevisions[question.id] || 0
  const body = question.type === 'WRITTEN'
    ? { expectedRevision: revision, selectedOptionIds: [], writtenText: `Answer revision ${revision}` }
    : { expectedRevision: revision, selectedOptionIds: [question.options[Math.floor(Math.random() * question.options.length)].id] }
  const saved = call(s.session, 'PUT', `/attempts/${s.attempt.id}/answers/${question.id}`, body)
  check(saved, { 'answer saved': (r) => r.status === 200 })
  if (saved.status === 200) s.answerRevisions[question.id] = saved.json('revision')
  if (Math.random() < 0.1) check(call(s.session, 'GET', `/attempts/${s.attempt.id}`), { 'attempt read': (r) => r.status === 200 })
  sleep(2 + Math.random() * 2)
}
