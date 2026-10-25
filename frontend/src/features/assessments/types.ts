export type QuestionType = 'SINGLE_CHOICE' | 'MULTIPLE_SELECT' | 'TRUE_FALSE' | 'WRITTEN'
export type ResultPolicy = 'AUTO_PUBLISH' | 'TEACHER_PUBLISH'
export type FeedbackPolicy = 'SCORE_AND_COMMENTS' | 'AFTER_CLOSE' | 'AFTER_SUBMISSION'

export type AuthorOption = { id: string; label: string; correct: boolean }
export type AuthorQuestion = { id: string; type: QuestionType; prompt: string; points: number; explanation: string; gradingNotes: string; options: AuthorOption[] }

export type ExamSummary = { id: string; title: string; latestEdition: number | null; latestVersionId: string | null; draftRevision: number | null }

export type ExamDetail = {
  id: string
  courseId: string
  title: string
  editions: { versionId: string; edition: number; title: string; publishedAt: string }[]
  draft: { versionId: string; revision: number } | null
}

export type ExamContent = {
  versionId: string
  examId: string
  edition: number | null
  revision: number
  title: string
  instructions: string
  passPercent: number
  questions: AuthorQuestion[]
}

export type Schedule = {
  opensAt: string | null
  closesAt: string | null
  durationMinutes: number | null
  maxAttempts: number
  resultPolicy: ResultPolicy
  feedbackPolicy: FeedbackPolicy
}

export type ExamAssignment = {
  id: string
  classId: string
  examVersionId: string
  examTitle: string
  edition: number
  questionCount: number
  maxPoints: number
  mixed: boolean
  required: boolean
  schedule: Schedule
  revision: number
}

export const QUESTION_TYPE_LABELS: Record<QuestionType, string> = {
  SINGLE_CHOICE: 'Single choice',
  MULTIPLE_SELECT: 'Multiple select',
  TRUE_FALSE: 'True / false',
  WRITTEN: 'Written answer',
}

export type AttemptStatus = 'IN_PROGRESS' | 'SUBMITTED' | 'AWAITING_GRADING' | 'GRADED' | 'EXPIRED'

export type StudentExam = {
  assignmentId: string
  title: string
  required: boolean
  questionCount: number
  maxPoints: number
  opensAt: string | null
  closesAt: string | null
  durationMinutes: number | null
  maxAttempts: number
  attemptsUsed: number
  activeAttemptId: string | null
  canStart: boolean
  attempts: { id: string; attemptNumber: number; status: AttemptStatus; finalizedAt: string | null }[]
}

export type SavedAnswer = { questionId: string; selectedOptionIds: string[]; writtenText: string | null; savedAt: string }

export type Attempt = {
  id: string
  assignmentId: string
  enrollmentId: string
  examTitle: string
  instructions: string
  attemptNumber: number
  maxAttempts: number
  status: AttemptStatus
  startedAt: string
  expiresAt: string
  serverNow: string
  finalizedAt: string | null
  questions: { id: string; position: number; type: QuestionType; prompt: string; points: number; options: { id: string; label: string }[] }[]
  answers: SavedAnswer[]
}
