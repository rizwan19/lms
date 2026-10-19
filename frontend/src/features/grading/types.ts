import type { QuestionType } from '../assessments/types'

export type QueueRow = {
  attemptId: string
  studentName: string
  username: string
  examTitle: string
  attemptNumber: number
  finalizedAt: string
  gradeStatus: 'DRAFT' | 'FINALIZED' | 'PUBLISHED' | 'SUPERSEDED'
  revisionNumber: number
  publishedEarned: number | null
  maximum: number
}

export type Revision = { id: string; number: number; status: QueueRow['gradeStatus']; earned: number | null; maximum: number; reason: string | null; publishedAt: string | null; version: number }

export type GradeView = {
  attempt: {
    attemptId: string
    examTitle: string
    attemptNumber: number
    maxScore: number
    questions: {
      questionId: string
      position: number
      type: QuestionType
      prompt: string
      points: number
      gradingNotes: string
      options: { id: string; label: string; correct: boolean }[]
      autoPoints: number | null
      selectedOptionIds: string[]
      writtenText: string | null
    }[]
  }
  revision: Revision
  grades: { questionId: string; points: number | null; feedback: string }[]
  history: Revision[]
}
